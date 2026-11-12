package online.playoftime.features.rival

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.Accuracy
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.core.utils.Quest
import online.playoftime.core.utils.VictoryCondition
import online.playoftime.core.utils.getCurrentQuest
import online.playoftime.core.utils.getTasksCountForLevel
import online.playoftime.core.utils.maxAttemptDuration
import online.playoftime.core.utils.minAttemptDuration
import online.playoftime.core.utils.roundedCells
import online.playoftime.core.utils.sessionAutoCloseAttempts
import online.playoftime.database.SessionDatabaseModel
import online.playoftime.database.TimeDatabase
import online.playoftime.database.TryDatabaseModel
import online.playoftime.features.stats.domain.entity.AnalyticsData
import online.playoftime.features.stats.domain.usecase.AnalyticsUseCase
import online.playoftime.use_case.CalculateScmUseCase
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Doppelgänger — the AI rival who learns from zero (Alex 08.11.2026: «обгони
 * своего ИИ-соперника, который будет учиться с нуля»).
 *
 * Rules agreed with Alex:
 *   • every attempt the player makes, the rival makes one too (lockstep);
 *     on first launch he replays the player's whole history by COUNT of
 *     attempts («пусть переиграет всю историю в смысле количества попыток»);
 *   • he plays his OWN independent game — his own quests, windows N,
 *     tolerances, indulgences («он играет свою независимую игру — как дойдёт,
 *     так дойдёт»), with no artificial limits on how good he may get;
 *   • he keeps a diary.
 *
 * Storage is fully separate from the player's, so nothing of his leaks into
 * the leaderboard sync, the Excel export or the player's own analytics:
 *   • attempts and sessions — a second Room file of the same schema
 *     ([provideRivalDatabase], "doppelganger.db"); [AnalyticsUseCase] works on
 *     it unchanged, so every analytics card exists for him too;
 *   • his quest state and his mind ([MicroPlayer]) — SharedPreferences
 *     "doppelganger", his learned synapses — filesDir/doppelganger_brain.bin;
 *   • the diary — filesDir/doppelganger_diary.jsonl (one JSON object a line).
 *
 * Each rival attempt is stamped with the player's attempt time (start), so
 * day/week/month filters show the two of them over the same days. The id of
 * the player's attempt he answered is kept in the otherwise unused
 * `deflection` column — that is the replay cursor: whatever player attempts
 * have a larger id are still to be answered. Idempotent and crash-safe.
 *
 * The quest rules are a straight port of MainViewModel (checkVictory,
 * onQuestWindowSuccess/Failure, getEffectiveN/ToleranceMs) without UI. The
 * player's touch dithering on L3 is NOT applied — it compensates a phone's
 * touch sampling, which the rival does not have.
 */
class RivalEngine(
    context: Context,
    private val playerDb: TimeDatabase,
    private val rivalDb: TimeDatabase,
    private val localStorage: LocalStorageService,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val diaryFile = File(appContext.filesDir, DIARY_FILE)
    private val brainFile = File(appContext.filesDir, BRAIN_FILE)
    private val daysFile = File(appContext.filesDir, DAYS_FILE)
    private val llmFile = File(appContext.filesDir, LLM_FILE)
    private val llmMutex = Mutex()
    private val writer = DiaryWriter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val calculateScm = CalculateScmUseCase()

    /** Fire-and-forget catch-up — after each player attempt and on start. */
    fun requestSync() {
        scope.launch { sync() }
    }

    /** Answer every player attempt not yet answered. Safe to call any time. */
    suspend fun sync() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { syncLocked() }
            }
        }
    }

    /** Analytics of the rival — the same use case as the player's, on his DB. */
    suspend fun analytics(period: PeriodFilter, offset: Int): AnalyticsData {
        sync()
        val st = mutex.withLock { loadState() }
        return AnalyticsUseCase(rivalDb, localStorage)(period, offset, currentToleranceMs = st.curT)
    }

    /** His quest with the indulgence step and condition, like the quest strip: "Quest 2.3.1" / "3.00 ± 0.01 · 3× in a row". */
    fun questBadge(): Pair<String, String> = loadState().let { questBadge(it.levelId, it.taskId, it.curN, it.curT) }

    /** Current quest of the rival — "L.T" for the UI. */
    fun questLabel(): String = loadState().let { "${it.levelId}.${it.taskId}" }

    /** Diary, oldest first. */
    suspend fun diary(): List<DiaryEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { diaryUnlocked() }
    }

    // ── Diary by a language model (Alex 2026-11-08) ─────────────────────────
    //
    // One entry per day he played, written only when the player opens that
    // day in the diary («запросы генерятся… только если человек зашёл и хочет
    // почитать»). Today's entry is rewritten when the day has grown a lot
    // since; past days are written once and kept. The model's memory chains
    // from day to day: the memory it wrote for the latest earlier day.

    data class LlmEntry(
        val entry: String, val memBefore: String, val memAfter: String,
        val attempts: Int, val at: Long, val model: String,
    )

    sealed class WriteResult {
        data class Written(val entry: LlmEntry) : WriteResult()
        /** Fewer than [MIN_ATTEMPTS_FOR_ENTRY] attempts that day — nothing to write about. */
        data object TooFew : WriteResult()
        /** Offline, server not configured, limit reached. */
        data object Failed : WriteResult()
    }

    suspend fun llmEntries(): Map<String, LlmEntry> = llmMutex.withLock { withContext(Dispatchers.IO) { loadLlm() } }

    suspend fun writeDayEntry(dayKey: String): WriteResult {
        sync()
        val facts = mutex.withLock { withContext(Dispatchers.IO) { runCatching { dayFacts(dayKey) }.getOrNull() } }
            ?: return WriteResult.TooFew
        val attempts = facts.optInt("attempts_today")
        val existing = llmMutex.withLock { withContext(Dispatchers.IO) { loadLlm() } }
        existing[dayKey]?.let { e ->
            val today = dayKey == dayKey(System.currentTimeMillis())
            val grown = attempts >= maxOf(e.attempts + 50, (e.attempts * 1.5).toInt())
            if (!(today && grown)) return WriteResult.Written(e)
        }
        val memBefore = existing[dayKey]?.memBefore
            ?: existing.filterKeys { it < dayKey }.maxByOrNull { it.key }?.value?.memAfter
            ?: FIRST_MEMORY
        val res = writer.write(localStorage.getOrCreatePlayerId(), dayKey, facts, memBefore)
            ?: return WriteResult.Failed
        val e = LlmEntry(res.entry, memBefore, res.memory.ifEmpty { memBefore }, attempts,
            System.currentTimeMillis(), res.model)
        llmMutex.withLock {
            withContext(Dispatchers.IO) {
                val all = loadLlm().toMutableMap()
                all[dayKey] = e
                saveLlm(all)
            }
        }
        return WriteResult.Written(e)
    }

    // ── One entry per day (Alex 2026-11-08: «дневник не должен быть слишком
    // подробным. Не надо каждую попытку комментировать — максимум одна по
    // результатам дня»). The engine still keeps its running notes (diary file)
    // as raw material; the screen shows one entry per day: the language
    // model's when there is one, else [templateEntry] from the same facts.

    /** One day of his play, counted once from his attempts. */
    data class DayDigest(
        val key: String, val startMs: Long,
        val attempts: Int, val exact: Int, val early: Int, val late: Int,
        val meanMs: Long, val spreadMs: Long, val bestRun: Int, val precisions: List<Int>,
        val totalAttempts: Int, val exactEver: Int, val dayIndex: Int,
        val sessionIds: Set<Int>, val questEnd: String?,
        val questsDone: List<String>, val levelsDone: List<String>, val easedDone: List<String>,
        val windowsFailed: Int, val habitsStart: JSONObject?, val habitsEnd: JSONObject?,
        /** The day's entry written by the engine itself (2–4 sentences). */
        val text: String = "",
    )

    /** All days he played, oldest first. */
    suspend fun dayDigests(): List<DayDigest> {
        sync()
        return mutex.withLock {
            withContext(Dispatchers.IO) { runCatching { buildDigests() }.getOrDefault(emptyList()) }
        }
    }

    private suspend fun buildDigests(): List<DayDigest> {
        val all = rivalDb.tryDao().getAllAttempts().sortedBy { it.startTime }
        if (all.isEmpty()) return emptyList()
        val sessions = rivalDb.sessionDao().getAllSessions().associateBy { it.id }
        val notesByDay = diaryUnlocked().groupBy { dayKey(it.time) }
        val snaps = loadDays()
        val snapKeys = snaps.keys().asSequence().toList().sorted()
        val habitsNow = habitsJson(loadState().player?.habits() ?: emptyMap())
        val aim = aimMs().toLong()
        fun errCells(t: TryDatabaseModel) = roundedCells(t.endTime - t.startTime, t.accuracy) - roundedCells(aim, t.accuracy)
        fun errMs(t: TryDatabaseModel) = errCells(t) * cellMs(Accuracy.values()[t.accuracy.coerceIn(0, 3)])
        var total = 0
        var exactEver = 0
        var idx = 0
        val out = mutableListOf<DayDigest>()
        all.groupBy { dayKey(it.startTime) }.forEach { (key, day) ->
            idx++
            val errs = day.map { errMs(it).toDouble() }
            val mean = errs.average()
            val spread = kotlin.math.sqrt(errs.sumOf { (it - mean) * (it - mean) } / errs.size)
            var run = 0
            var bestRun = 0
            day.forEach { if (errCells(it) == 0L) { run++; bestRun = maxOf(bestRun, run) } else run = 0 }
            val exact = day.count { errCells(it) == 0L }
            total += day.size
            exactEver += exact
            val notes = notesByDay[key].orEmpty().map { it.text }
            val questEnd = sessions[day.last().sessionId]
                ?.let { getCurrentQuest(it.questLevelId, it.questTaskId) }
                ?.let { "${it.label()} — ${it.victoryText()}" }
            val d = DayDigest(
                key = key, startMs = dayStartMs(key),
                attempts = day.size, exact = exact,
                early = day.count { errCells(it) < 0 }, late = day.count { errCells(it) > 0 },
                meanMs = Math.round(mean), spreadMs = Math.round(spread), bestRun = bestRun,
                precisions = day.map { it.accuracy }.distinct(),
                totalAttempts = total, exactEver = exactEver, dayIndex = idx,
                sessionIds = day.map { it.sessionId }.toSet(), questEnd = questEnd,
                questsDone = notes.mapNotNull { QUEST_DONE.find(it)?.groupValues?.get(1) },
                levelsDone = notes.mapNotNull { LEVEL_DONE.find(it)?.groupValues?.get(1) },
                easedDone = notes.mapNotNull { EASED_DONE.find(it)?.groupValues?.get(1) },
                windowsFailed = notes.count { WINDOW_FAILED.containsMatchIn(it) },
                habitsStart = snaps.optJSONObject(key),
                habitsEnd = snapKeys.firstOrNull { it > key }?.let { snaps.optJSONObject(it) } ?: habitsNow,
            )
            out += d.copy(text = templateEntry(d))
        }
        return out
    }

    /** The day's entry from the engine itself — one per day, 2–4 sentences, few numbers. */
    private fun templateEntry(d: DayDigest): String {
        val s = mutableListOf<String>()
        fun threes(n: Int) = if (n == 1) "exact three" else "exact threes"
        s += when {
            d.attempts < 10 -> "Only ${d.attempts} ${if (d.attempts == 1) "attempt" else "attempts"} today" +
                (if (d.exact > 0) ", ${d.exact} of them exactly three." else ".")
            d.exact == 0 -> "${d.attempts} attempts today and not one exact three."
            else -> {
                val rate = d.exact.toDouble() / d.attempts
                val share = if (rate >= 0.5) "more than half" else "about one in ${Math.round(1 / rate)}"
                "${d.attempts} attempts today, ${d.exact} ${threes(d.exact)} — $share."
            }
        }
        if (d.attempts >= 10) {
            val e = d.early
            val l = d.late
            s += when {
                e >= 6 && e >= 2 * maxOf(1, l) -> "Mostly early — I still rush."
                l >= 6 && l >= 2 * maxOf(1, e) -> "Mostly late — I still drag."
                else -> "My misses fell on both sides about evenly; what holds me back is the spread, about ${d.spreadMs} ms."
            }
        }
        val prec = mapOf("1" to "hundredths", "2" to "thousandths")
        when {
            d.levelsDone.isNotEmpty() -> {
                val lv = d.levelsDone.last()
                s += "Finished level $lv" + (prec[lv]?.let { " — the counter now shows $it" } ?: "") + "."
            }
            d.questsDone.isNotEmpty() -> s += (if (d.questsDone.size == 1) "Got through quest ${d.questsDone[0]}"
                else "Got through quests ${d.questsDone.joinToString(", ")}") +
                (d.questEnd?.let { "; next is $it." } ?: ".")
            d.easedDone.isNotEmpty() -> s += "Passed ${d.easedDone.last()} only on eased terms, so the game tightened it again."
            d.windowsFailed > 0 -> s += "Didn't manage ${d.questEnd?.substringBefore(" —") ?: "my quest"} in its window" +
                (if (d.windowsFailed > 1) " (${d.windowsFailed} times)" else "") + "; the game gave me more room."
            d.questEnd != null && d.attempts >= 10 -> s += "Still on quest ${d.questEnd}."
        }
        // How he learns: the biggest change of a correction habit over the day.
        val st = d.habitsStart
        val en = d.habitsEnd
        if (st != null && en != null) {
            val names = mapOf("same_side" to "after a second miss on the same side",
                "switched" to "after jumping to the other side", "near" to "within a step of three")
            names.keys.mapNotNull { k ->
                val a = st.optString(k).removeSuffix("%").toIntOrNull()
                val b = en.optString(k).removeSuffix("%").toIntOrNull()
                if (a != null && b != null && kotlin.math.abs(b - a) >= 15) Triple(k, a, b) else null
            }.maxByOrNull { kotlin.math.abs(it.third - it.second) }?.let { (k, a, b) ->
                s += "I've changed how I learn: ${names[k]} I now correct by $b% of a miss instead of $a%."
            }
        }
        return s.joinToString(" ")
    }

    /**
     * Everything the model may say about one day — the engine's digest of it
     * plus its notable moments. The model gets nothing else to go on, so it
     * has nothing to invent from.
     */
    private suspend fun dayFacts(dayKey: String): JSONObject? {
        val d = buildDigests().firstOrNull { it.key == dayKey } ?: return null
        if (d.attempts < MIN_ATTEMPTS_FOR_ENTRY) return null
        val notes = diaryUnlocked().filter { dayKey(it.time) == dayKey && !it.text.startsWith("Last 25") }
        val events = notes.filter { QUEST_EVENT.containsMatchIn(it.text) }
        val others = notes.filterNot { QUEST_EVENT.containsMatchIn(it.text) }
        val moments = (events + others.takeLast(maxOf(0, 10 - events.size))).sortedBy { it.n }.takeLast(12)
        return JSONObject()
            .put("day", d.dayIndex)
            .put("precision_today", d.precisions.joinToString(", then ") {
                when (it) { 1 -> "tenths (3.0)"; 2 -> "hundredths (3.00)"; 3 -> "thousandths (3.000)"; else -> "seconds" }
            })
            .put("attempts_today", d.attempts)
            .put("attempts_total", d.totalAttempts)
            .put("exact_threes_today", d.exact)
            .put("exact_threes_ever", d.exactEver)
            .put("early_today", d.early)
            .put("late_today", d.late)
            .put("average_miss_ms", d.meanMs)
            .put("spread_ms", d.spreadMs)
            .put("longest_run_of_exact_today", d.bestRun)
            .put("quest_at_end_of_day", d.questEnd ?: "unknown")
            .put("correction_habits_at_start_of_day", d.habitsStart?.takeIf { it.length() > 0 } ?: "none yet")
            .put("correction_habits_now", d.habitsEnd ?: "none yet")
            .put("habits_legend", "share of a miss I correct: same_side = after a second miss on the same side; " +
                "switched = after jumping to the other side; near = when within one step of three")
            .put("notable_moments", org.json.JSONArray().apply { moments.forEach { put("attempt ${it.n}: ${it.text}") } })
    }

    /**
     * Weekly reflections (Alex 11.11.2026: weekly, in the analytics log, ВПФ-style)
     * keyed like the session tree's week node: "year-weekOfYear" (Calendar default).
     */
    suspend fun weeklyEntries(): Map<String, String> {
        sync()
        return mutex.withLock {
            withContext(Dispatchers.IO) { runCatching { buildWeekly() }.getOrDefault(emptyMap()) }
        }
    }

    private suspend fun buildWeekly(): Map<String, String> {
        val all = rivalDb.tryDao().getAllAttempts().sortedBy { it.startTime }
        if (all.isEmpty()) return emptyMap()
        val aim = aimMs().toLong()
        val cal = Calendar.getInstance()
        fun weekKey(ms: Long): String {
            cal.timeInMillis = ms
            return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.WEEK_OF_YEAR)}"
        }
        val notes = diaryUnlocked()
        val snaps = loadDays()
        val snapKeys = snaps.keys().asSequence().toList().sorted()
        fun habitsOf(o: JSONObject?): Map<String, Int>? = o?.let { j ->
            j.keys().asSequence().associate { k ->
                (if (k == "same_side") "repeat" else k) to (j.optString(k).removeSuffix("%").toIntOrNull() ?: 0)
            }
        }
        val nowHabits = loadState().player?.habits()?.filterValues { it != null }?.mapValues { it.value!! }
        val out = LinkedHashMap<String, String>()
        var arc: WeeklyReflection.Arc? = null
        var prevWeek: WeeklyReflection.Week? = null
        var prevAcc: Int? = null
        val rivalSessions = rivalDb.sessionDao().getAllSessions().associateBy { it.id }
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH)
        all.groupBy { weekKey(it.startTime) }.entries.forEachIndexed { idx, (key, wk) ->
            val seen = wk.map { t ->
                (roundedCells(t.endTime - t.startTime, t.accuracy) - roundedCells(aim, t.accuracy)) *
                    cellMs(Accuracy.values()[t.accuracy.coerceIn(0, 3)]).toDouble()
            }
            val m = seen.average()
            val sd = kotlin.math.sqrt(seen.sumOf { (it - m) * (it - m) } / seen.size).coerceAtLeast(1.0)
            var c = 0.0
            for (i in 1 until seen.size) c += (seen[i - 1] - m) * (seen[i] - m)
            val r1 = if (seen.size > 1) c / ((seen.size - 1) * sd * sd) else 0.0
            val first = mutableListOf<Double>(); val last = mutableListOf<Double>()
            wk.indices.groupBy { wk[it].sessionId }.values.filter { it.size >= 30 }.forEach { ix ->
                val third = ix.size / 3
                ix.take(third).forEach { first += kotlin.math.abs(seen[it]) }
                ix.takeLast(third).forEach { last += kotlin.math.abs(seen[it]) }
            }
            val fatigue = if (first.size > 30) last.average() / first.average().coerceAtLeast(1.0) - 1 else 0.0
            val from = wk.first().startTime
            val to = wk.last().startTime
            val wn = notes.filter { it.time in from..to }.map { it.text }
            val accs = wk.map { it.accuracy }
            val levelUp = if ((prevAcc != null && accs.max() > prevAcc!!) || accs.distinct().size > 1)
                when (accs.max()) { 2 -> "hundredths"; 3 -> "thousandths"; else -> null } else null
            prevAcc = accs.last()
            val firstDay = dayKey(from)
            val lastDay = dayKey(to)
            // Конкретика: лучший/худший день, серия точных, момент взятия квеста.
            val byDay = wk.indices.groupBy { dayKey(wk[it].startTime) }.filterValues { it.size >= 20 }
            fun exactOf(ix: List<Int>) = ix.count { seen[it] == 0.0 }
            val best = byDay.maxByOrNull { (_, ix) -> exactOf(ix).toDouble() / ix.size }
            val worst = byDay.minByOrNull { (_, ix) -> exactOf(ix).toDouble() / ix.size }
            val bySession = wk.indices.groupBy { wk[it].sessionId }
            val longestSession = bySession.maxByOrNull { it.value.size }?.key
            val worstTired = worst?.value?.groupBy { wk[it].sessionId }?.values?.filter { it.size >= 20 }?.any { ix ->
                val third = ix.size / 3
                ix.takeLast(third).map { kotlin.math.abs(seen[it]) }.average() >
                    ix.take(third).map { kotlin.math.abs(seen[it]) }.average() * 1.15
            } ?: false
            var run = 0; var bestRun = 0; var runDay: Long? = null
            seen.forEachIndexed { i, v ->
                run = if (v == 0.0 && (i == 0 || wk[i - 1].sessionId == wk[i].sessionId)) run + 1 else if (v == 0.0) 1 else 0
                if (run > bestRun) { bestRun = run; runDay = wk[i].startTime }
            }
            val moments = notes.filter { it.time in from..to }.mapNotNull { e ->
                val q = QUEST_DONE.find(e.text)?.groupValues?.get(1) ?: return@mapNotNull null
                val k = bySession[e.sessionId]?.count { wk[it].startTime <= e.time } ?: 0
                Triple(q, dayName.format(java.util.Date(e.time)), k.coerceAtLeast(1))
            }
            val deepest = wk.maxOfOrNull { t ->
                val sess = rivalSessions[t.sessionId]
                if (sess == null || sess.questLevelId == 0) 0 else {
                    val q = getCurrentQuest(sess.questLevelId, sess.questTaskId)
                    if (!q.adaptiveT || t.toleranceMs <= q.toleranceMsDefault) 0
                    else (t.toleranceMs - q.toleranceMsDefault) / when (q.accuracy.ordinal.coerceIn(1, 3)) { 1 -> 100; 2 -> 10; else -> 1 }
                }
            } ?: 0
            fun nameOf(ix: List<Int>?) = ix?.let { dayName.format(java.util.Date(wk[it.first()].startTime)) }
            val w0 = WeeklyReflection.Week(
                index = idx, attempts = wk.size,
                exact = seen.count { it == 0.0 }, early = seen.count { it < 0 }, late = seen.count { it > 0 },
                meanMs = m, sdMs = sd, r1 = r1, fatigue = fatigue,
                questsDone = wn.mapNotNull { QUEST_DONE.find(it)?.groupValues?.get(1) },
                windowsFailed = wn.count { WINDOW_FAILED.containsMatchIn(it) },
                levelUp = levelUp,
                habitStart = habitsOf(snaps.optJSONObject(firstDay)),
                habitEnd = snapKeys.firstOrNull { it > lastDay }?.let { habitsOf(snaps.optJSONObject(it)) } ?: nowHabits,
            )
            val w = w0.copy(
                bestDay = nameOf(best?.value), bestDayExact = best?.value?.let { exactOf(it) } ?: 0, bestDayAttempts = best?.value?.size ?: 0,
                worstDay = nameOf(worst?.value), worstDayExact = worst?.value?.let { exactOf(it) } ?: 0, worstDayAttempts = worst?.value?.size ?: 0,
                worstDayTired = worstTired,
                worstDayLongest = worst?.value?.any { wk[it].sessionId == longestSession } ?: false,
                longestRun = bestRun, longestRunDay = runDay?.let { dayName.format(java.util.Date(it)) },
                questMoments = moments, deepestStep = deepest, sessions = bySession.size,
                errors = seen.filter { it != 0.0 },
            )
            val (text, next) = WeeklyReflection.entry(w, arc, prevWeek)
            arc = next
            prevWeek = w
            // Ключ — подпись недели для экрана AI (Alex 11.11: дневник — на экран ИИ).
            val f = java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH)
            val y = java.text.SimpleDateFormat("yyyy", java.util.Locale.ENGLISH)
            out["${f.format(java.util.Date(from))} – ${f.format(java.util.Date(to))} ${y.format(java.util.Date(to))} · ${wk.size} attempts"] = text
        }
        return out
    }

    /**
     * Speed of learning for both (Alex 11.11.2026): the quest reached by each
     * attempt — index in questList, 0…14 — for the player and for him, the same
     * number of points (he makes one attempt for each of the player's).
     * Downsampled to ≤ [points].
     */
    /**
     * Лесенка индульгенций по попыткам (Alex 12.11.2026: «по вертикали
     * откладывать уровни с точностью до индульгенции… масштаб — чтобы видеть
     * колебание между индульгенциями, самая верхняя линия win»).
     * Значение попытки: номер ступени индульгенции (0 — условие квеста без
     * поблажки, 1, 2… — ослабленные), [LADDER_WIN] — попытка, которой квест взят.
     * Последние [points] попыток каждого, без прореживания — иначе колебание
     * сотрётся. Ступень — по допуску ±t, записанному в попытке (поблажка по N
     * в попытках не хранится и сюда не входит).
     */
    suspend fun learningCurves(period: PeriodFilter, offset: Int, points: Int = 400): Pair<List<Int>, List<Int>> {
        sync()
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    suspend fun ladder(db: TimeDatabase): List<Int> {
                        val sessions = db.sessionDao().getAllSessions().associateBy { it.id }
                        // Попытки выбранного периода — как в AnalyticsUseCase (Alex 12.11:
                        // «одинаковый для любого периода, что ошибка»).
                        val tries = when (period) {
                            PeriodFilter.ALL_TIME -> db.tryDao().getAllAttempts()
                            PeriodFilter.SESSION -> db.sessionDao().getAllSessions().sortedByDescending { it.startTime }
                                .getOrNull(offset)?.let { db.tryDao().getAttemptsBySessionId(it.id) } ?: emptyList()
                            else -> {
                                val (from, to) = period.rangeMsForOffset(offset)
                                db.tryDao().getAttemptsInRange(from, to).map { it.sessionId }.toSet()
                                    .flatMap { db.tryDao().getAttemptsBySessionId(it) }
                            }
                        }.sortedBy { it.startTime }
                        val lastOfSession = tries.groupBy { it.sessionId }.mapValues { (_, l) -> l.maxOf { it.startTime } }
                        // Значение = индекс квеста × 1000 + ступень (999 — win), чтобы график
                        // мог подписать квест над каждым отрезком.
                        return tries.map { t ->
                            val sess = sessions[t.sessionId]
                            val qi = if (sess == null) 0 else online.playoftime.core.utils.questList.indexOfFirst {
                                it.levelId == sess.questLevelId && it.taskId == sess.questTaskId }.coerceAtLeast(0)
                            val step = when {
                                sess != null && sess.isQuestComplete && lastOfSession[t.sessionId] == t.startTime -> LADDER_WIN_CODE
                                sess == null || sess.questLevelId == 0 -> 0
                                else -> {
                                    val q = online.playoftime.core.utils.getCurrentQuest(sess.questLevelId, sess.questTaskId)
                                    if (!q.adaptiveT || t.toleranceMs <= q.toleranceMsDefault) 0
                                    else (t.toleranceMs - q.toleranceMsDefault) / when (q.accuracy.ordinal.coerceIn(1, 3)) { 1 -> 100; 2 -> 10; else -> 1 }
                                }
                            }
                            qi * 1000 + step.coerceAtMost(LADDER_WIN_CODE)
                        }.let { full ->
                            // Больше [points] — прореживаем корзинами: win, если был в
                            // корзине, иначе самая глубокая ступень — колебание не теряется.
                            if (full.size <= points) full
                            else List(points) { i ->
                                val a = (i.toLong() * full.size / points).toInt()
                                val b = ((i + 1).toLong() * full.size / points).toInt().coerceAtLeast(a + 1)
                                val part = full.subList(a, b)
                                part.firstOrNull { it % 1000 == LADDER_WIN_CODE } ?: part.maxBy { it % 1000 }
                            }
                        }
                    }
                    ladder(playerDb) to ladder(rivalDb)
                }.getOrDefault(emptyList<Int>() to emptyList())
            }
        }
    }

    /**
     * His attempts in the format of api/report_data.php — for his own face and
     * report (Alex 2026-11-08: «а морда в аналитикс чья?» — the page drew the
     * player's, from the server; his attempts live only on the phone). Same
     * rule as the server: only his current precision tier.
     */
    suspend fun reportJson(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val all = rivalDb.tryDao().getAllAttempts()
                val acc = all.maxByOrNull { it.startTime }?.accuracy
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                val aim = aimMs()
                val counters = HashMap<Int, Int>()
                val rows = org.json.JSONArray()
                all.filter { it.accuracy == acc }
                    .sortedWith(compareBy({ it.sessionId }, { it.startTime }))
                    .forEach { t ->
                        val n = (counters[t.sessionId] ?: 0) + 1
                        counters[t.sessionId] = n
                        rows.put(JSONObject().put("t", iso.format(java.util.Date(t.startTime)))
                            .put("s", t.sessionId).put("n", n).put("e", t.endTime - t.startTime - aim))
                    }
                JSONObject().put("ok", true).put("displayName", NAME)
                    .put("acc", when (acc) { 1 -> "ds"; 2 -> "cs"; 3 -> "ms"; else -> null })
                    .put("attempts", rows).toString()
            }.getOrDefault("""{"ok":true,"attempts":[]}""")
        }
    }

    private fun habitsJson(h: Map<String, Int?>): JSONObject = JSONObject().apply {
        h.forEach { (k, v) -> if (v != null) put(if (k == "repeat") "same_side" else k, "$v%") }
    }

    private fun loadDays(): JSONObject =
        runCatching { JSONObject(daysFile.readText()) }.getOrElse { JSONObject() }

    private fun diaryUnlocked(): List<DiaryEntry> =
        if (!diaryFile.exists()) emptyList()
        else diaryFile.readLines().mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                DiaryEntry(o.getInt("n"), o.getLong("t"), o.getInt("s"), o.getString("x"))
            }.getOrNull()
        }

    private fun loadLlm(): Map<String, LlmEntry> = runCatching {
        val o = JSONObject(llmFile.readText())
        o.keys().asSequence().associateWith { k ->
            val e = o.getJSONObject(k)
            LlmEntry(e.getString("entry"), e.optString("memBefore"), e.optString("memAfter"),
                e.optInt("attempts"), e.optLong("at"), e.optString("model"))
        }
    }.getOrElse { emptyMap() }

    private fun saveLlm(all: Map<String, LlmEntry>) {
        val o = JSONObject()
        all.forEach { (k, e) ->
            o.put(k, JSONObject().put("entry", e.entry).put("memBefore", e.memBefore).put("memAfter", e.memAfter)
                .put("attempts", e.attempts).put("at", e.at).put("model", e.model))
        }
        val tmp = File(llmFile.parentFile, llmFile.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(llmFile)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun syncLocked() {
        var cursor = rivalDb.tryDao().getMaxDeflection().toInt()
        while (true) {
            val batch = playerDb.tryDao().getAttemptsAfterId(cursor, BATCH)
            if (batch.isEmpty()) break
            val st = loadState()
            val player = st.player ?: MicroPlayer.newborn(st.seed, aimMs()).also { st.player = it }
            val notes = mutableListOf<DiaryEntry>()
            val days = loadDays()
            rivalDb.withTransaction {
                for (src in batch) {
                    // How he corrected at the start of each day he played — for
                    // the language-model diary ("habits at start of day / now").
                    val dk = dayKey(src.startTime)
                    if (!days.has(dk)) days.put(dk, habitsJson(player.habits()))
                    playOne(st, player, src, notes)
                }
            }
            saveState(st)
            player.writeBrain(brainFile)
            daysFile.writeText(days.toString())
            appendDiary(notes)
            cursor = batch.last().id
        }
    }

    private fun aimMs(): Int = localStorage.getAimTime()

    private suspend fun playOne(st: State, mind: MicroPlayer, src: TryDatabaseModel, notes: MutableList<DiaryEntry>) {
        val quest = getCurrentQuest(st.levelId, st.taskId)
        val acc = quest.accuracy.ordinal
        val cell = cellMs(quest.accuracy)
        val aim = aimMs().toLong()

        // A session ends with the game's own events (below) — and also when the
        // player took a break: a gap over 30 min, a new day, or time going back
        // to before this session (an imported old history). Like the player's
        // app closing a session.
        if (st.sessionId != 0 && (src.startTime < st.sessionStart ||
                src.startTime - st.lastEndTime > SESSION_BREAK_MS ||
                !sameDay(st.sessionStart, src.startTime))) {
            closeSession(st)
        }
        if (st.sessionId == 0) {
            st.sessionStart = src.startTime
            st.sessionId = rivalDb.sessionDao().insertReturningId(
                SessionDatabaseModel(
                    id = 0, startTime = src.startTime, endTime = src.startTime,
                    questLevelId = quest.levelId, questTaskId = quest.taskId
                )
            ).toInt()
        }

        val tol = effectiveT(st, quest)
        val duration = mind.nextDurationMs().coerceIn(minAttemptDuration + 1, maxAttemptDuration - 1)
        // His attempt starts with the player's — but never before his own
        // previous one ended: the player may press RUN again sooner than the
        // rival's 3 s are over. A short human pause in between.
        // (Only a small overlap counts: an old imported history replayed after
        // newer attempts lies far in the past and keeps its own time.)
        val overlap = st.lastEndTime + MIN_PAUSE_MS - src.startTime
        val start = if (overlap in 1 until 60_000) src.startTime + overlap else src.startTime
        val end = start + duration
        val errCells = roundedCells(duration, acc) - roundedCells(aim, acc)
        val errMs = errCells * cell
        val isWin = abs(errMs) <= tol

        val id = rivalDb.tryDao().insertReturningId(
            TryDatabaseModel(
                id = 0, sessionId = st.sessionId, startTime = start, endTime = end,
                deflection = src.id.toLong(), accuracy = acc, toleranceMs = tol
            )
        ).toInt()
        val cur = Brief(id, start, end, acc)
        // SCM for the previous attempt, now that both its neighbours are known —
        // exactly SaveScmAfterAttemptUseCase.
        val p1 = st.prev1
        val p2 = st.prev2
        if (p1 != null && p2 != null) {
            rivalDb.tryDao().updateScm(p1.id, calculateScm(p2.model(), p1.model(), cur.model(), aim.toInt()))
        }
        st.prev2 = p1
        st.prev1 = cur
        st.lastEndTime = end
        rivalDb.sessionDao().updateSessionTimes(st.sessionId, end, end - st.sessionStart)

        fun note(text: String) { notes += DiaryEntry(mind.n, start, st.sessionId, text) }
        mind.feedback(errMs.toDouble(), cell.toInt(), isWin).forEach(::note)

        st.sessionWins += isWin
        val closed = checkVictory(st, quest, isWin, abs(errMs).toInt(), ::note)
        if (!closed && st.sessionId != 0) {
            val limit = effectiveN(st, quest).takeIf { it > 0 } ?: sessionAutoCloseAttempts
            if (st.sessionWins.size >= limit) closeSession(st)
        }
    }

    private fun closeSession(st: State) {
        st.sessionId = 0
        st.sessionWins.clear()
    }

    /** Port of MainViewModel.checkVictory. Returns true if the session closed. */
    private suspend fun checkVictory(st: State, quest: Quest, isWin: Boolean, errorMs: Int, note: (String) -> Unit): Boolean {
        if (quest.maxAttemptsDefault != null) {
            st.winAttempts++
            if (isWin) {
                st.winWins++
                if (quest.adaptiveT && errorMs > st.winMaxErr) st.winMaxErr = errorMs
                if (quest.adaptiveN) {
                    if (st.winFirstIdx == 0) st.winFirstIdx = st.winAttempts
                    st.winLastIdx = st.winAttempts
                }
            } else if (quest.adaptiveT) {
                st.winMaxErr = 0
            }
        }
        val windowCount = if (quest.maxAttemptsDefault != null) st.winAttempts else st.sessionWins.size
        val window = st.sessionWins.takeLast(windowCount)
        val winsCount = when (quest.victory.victoryCondition) {
            VictoryCondition.SESSION -> window.count { it }
            VictoryCondition.ROW -> {
                var best = 0; var run = 0
                window.forEach { w -> run = if (w) run + 1 else 0; if (run > best) best = run }
                best
            }
            VictoryCondition.IN_N -> st.winWins
        }

        if (winsCount >= quest.victory.victoryCount) {
            val sessionAttempts = st.sessionWins.size
            val atBaseline = onWindowSuccess(st, quest)
            rivalDb.sessionDao().updateQuestComplete(st.sessionId, true)
            if (!atBaseline) {
                note("Done ${quest.label()} — but on eased terms. The game tightened the rule back " +
                    "(${terms(st, quest)}). I have to prove it for real.")
            } else {
                val isLastLevel = quest.levelId >= 3
                val isLastTask = quest.taskId >= getTasksCountForLevel(quest.levelId)
                val wasIndulged = st.wasIndulged
                when {
                    isLastLevel && isLastTask ->
                        note("Quest ${quest.label()} done — that was the last one. The whole game in ${st.totalOf()} attempts. I'll keep playing.")
                    else -> {
                        val next = if (isLastTask) getCurrentQuest(quest.levelId + 1, 1)
                                   else getCurrentQuest(quest.levelId, quest.taskId + 1)
                        note(
                            (if (isLastTask) "Level ${quest.levelId} complete! " else "") +
                            "Quest ${quest.label()} done (${quest.victoryText()}) in $sessionAttempts attempts this session" +
                            (if (wasIndulged) ", after the game had eased it for me" else "") +
                            ". Next: ${next.label()} — ${next.victoryText()}."
                        )
                        st.levelId = next.levelId
                        st.taskId = next.taskId
                        resetQuestParams(st, next)
                    }
                }
            }
            closeSession(st)
            return true
        }

        if (quest.maxAttemptsDefault != null) {
            val effectiveCount = minOf(st.sessionWins.size, st.winAttempts)
            val n = effectiveN(st, quest)
            if (n > 0 && effectiveCount >= n) {
                val before = terms(st, quest)
                val wasAtCap = atCap(st, quest)
                onWindowFailure(st, quest)
                note(
                    if (wasAtCap) "Another window of $n without ${quest.victoryText()}. The game can't ease it any further — only the hand can."
                    else "Didn't make ${quest.victoryText()} in $n attempts ($before). The game eased it: ${terms(st, quest)}."
                )
                closeSession(st)
                return true
            }
        }
        return false
    }

    /** Port of MainViewModel.onQuestWindowSuccess: true = won at baseline. */
    private fun onWindowSuccess(st: State, quest: Quest): Boolean {
        val currentN = effectiveN(st, quest)
        val currentT = effectiveT(st, quest)
        val baseN = quest.maxAttemptsDefault ?: Int.MAX_VALUE
        val baseT = quest.toleranceMsDefault
        val step = quest.adaptiveNStep
        val atBaselineN = !quest.adaptiveN || currentN <= baseN
        val atBaselineT = !quest.adaptiveT || currentT <= baseT
        val worstWin = st.winMaxErr
        val lastWin = st.winLastIdx
        val firstWin = st.winFirstIdx
        val winSpan = if (firstWin in 1..lastWin) lastWin - firstWin + 1 else lastWin
        resetWindow(st)
        if (atBaselineN && atBaselineT) return true

        val qualityT = !quest.adaptiveT || worstWin <= baseT
        val qualityN = !quest.adaptiveN || (winSpan in 1..baseN)
        if (qualityT && qualityN) {
            if (quest.adaptiveN) st.curN = baseN
            if (quest.adaptiveT) st.curT = baseT
            return true
        }
        if (quest.adaptiveT && currentT > baseT) {
            val stepMs = cellMs(quest.accuracy).toInt()
            val needed = ceil(worstWin.toDouble() / stepMs).toInt()
            st.curT = (baseT + (needed - 1).coerceAtLeast(0) * stepMs).coerceIn(baseT, currentT)
        }
        if (quest.adaptiveN && currentN > baseN) {
            st.curN = if (lastWin in 1..currentN) {
                val over = (winSpan - baseN).coerceAtLeast(0)
                val needed = ceil(over.toDouble() / step).toInt()
                (baseN + (needed - 1).coerceAtLeast(0) * step).coerceIn(baseN, currentN)
            } else maxOf(baseN, currentN - step)
        }
        return false
    }

    /** Port of MainViewModel.onQuestWindowFailure (without the UI part). */
    private fun onWindowFailure(st: State, quest: Quest) {
        if (quest.adaptiveN) {
            val nMax = (quest.maxAttemptsDefault ?: quest.adaptiveNStep) + 2 * quest.adaptiveNStep
            st.curN = minOf(nMax, effectiveN(st, quest) + quest.adaptiveNStep)
        }
        if (quest.adaptiveT) {
            val stepMs = cellMs(quest.accuracy).toInt()
            val tMax = quest.toleranceMsDefault + quest.toleranceStepsCap * stepMs
            st.curT = minOf(tMax, effectiveT(st, quest) + stepMs)
        }
        st.wasIndulged = true
        resetWindow(st)
    }

    private fun atCap(st: State, quest: Quest): Boolean {
        val stepMs = cellMs(quest.accuracy).toInt()
        val nMax = (quest.maxAttemptsDefault ?: quest.adaptiveNStep) + 2 * quest.adaptiveNStep
        val tMax = quest.toleranceMsDefault + quest.toleranceStepsCap * stepMs
        return (quest.adaptiveN && effectiveN(st, quest) >= nMax) ||
               (quest.adaptiveT && effectiveT(st, quest) >= tMax)
    }

    private fun resetWindow(st: State) {
        st.winAttempts = 0; st.winWins = 0; st.winMaxErr = 0; st.winLastIdx = 0; st.winFirstIdx = 0
    }

    private fun resetQuestParams(st: State, q: Quest) {
        st.curN = q.maxAttemptsDefault ?: -1
        st.curT = q.toleranceMsDefault
        resetWindow(st)
        st.wasIndulged = false
    }

    /** Port of MainViewModel.getEffectiveN: N snapped to the step, capped at baseline + 2·step. */
    private fun effectiveN(st: State, quest: Quest): Int {
        val baseline = quest.maxAttemptsDefault ?: return -1
        val step = quest.adaptiveNStep
        if (st.curN == -1) { st.curN = baseline; return baseline }
        val snapped = if (st.curN <= baseline) baseline
                      else baseline + ((st.curN - baseline + step - 1) / step) * step
        st.curN = minOf(snapped, baseline + 2 * step)
        return st.curN
    }

    /** Port of MainViewModel.getEffectiveToleranceMs. */
    private fun effectiveT(st: State, quest: Quest): Int {
        val baseT = quest.toleranceMsDefault
        if (!quest.adaptiveT) return baseT
        val stepMs = cellMs(quest.accuracy).toInt()
        val snapped = if (st.curT <= baseT) baseT
                      else baseT + ((st.curT - baseT + stepMs - 1) / stepMs) * stepMs
        st.curT = minOf(snapped, baseT + quest.toleranceStepsCap * stepMs)
        return st.curT
    }

    private fun terms(st: State, quest: Quest): String {
        val parts = mutableListOf<String>()
        if (quest.adaptiveN) parts += "window ${effectiveN(st, quest)}"
        if (quest.adaptiveT) {
            val t = effectiveT(st, quest)
            parts += if (t == 0) "exact" else "±${t} ms"
        }
        return parts.joinToString(", ").ifEmpty { "baseline" }
    }

    private fun Quest.label() = "$levelId.$taskId"
    private fun Quest.victoryText(): String = when (victory.victoryCondition) {
        VictoryCondition.SESSION -> if (victory.victoryCount == 1) "one exact three" else "${victory.victoryCount} exact threes"
        VictoryCondition.ROW     -> "${victory.victoryCount} in a row"
        VictoryCondition.IN_N    -> "${victory.victoryCount} in a window"
    }

    private fun State.totalOf(): Int = player?.n ?: 0

    private fun cellMs(acc: Accuracy): Long = when (acc) {
        Accuracy.SECONDS -> 1000L
        Accuracy.DECISECONDS -> 100L
        Accuracy.CENTISECONDS -> 10L
        Accuracy.MILLISECONDS -> 1L
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
               ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun appendDiary(notes: List<DiaryEntry>) {
        if (notes.isEmpty()) return
        diaryFile.appendText(notes.joinToString("") { e ->
            JSONObject().put("n", e.n).put("t", e.time).put("s", e.sessionId).put("x", e.text).toString() + "\n"
        })
    }

    // ── State ────────────────────────────────────────────────────────────────

    private data class Brief(val id: Int, val start: Long, val end: Long, val acc: Int) {
        fun model() = TryDatabaseModel(id = id, sessionId = 0, startTime = start, endTime = end,
            deflection = null, accuracy = acc)
        fun json(): JSONObject = JSONObject().put("id", id).put("s", start).put("e", end).put("a", acc)
        companion object {
            fun of(o: JSONObject?) = o?.let { Brief(it.getInt("id"), it.getLong("s"), it.getLong("e"), it.getInt("a")) }
        }
    }

    private class State {
        var seed = 0
        var levelId = 1
        var taskId = 1
        var curN = -1
        var curT = 0
        var winAttempts = 0
        var winWins = 0
        var winMaxErr = 0
        var winLastIdx = 0
        var winFirstIdx = 0
        var wasIndulged = false
        var sessionId = 0
        var sessionStart = 0L
        var lastEndTime = 0L
        val sessionWins = mutableListOf<Boolean>()
        var prev1: Brief? = null
        var prev2: Brief? = null
        var player: MicroPlayer? = null
    }

    private fun loadState(): State {
        val st = State()
        val raw = prefs.getString(KEY_STATE, null)
        if (raw == null) {
            st.seed = (System.nanoTime() xor 0x5DEECE66DL).toInt()
            val q = getCurrentQuest(1, 1)
            st.curN = q.maxAttemptsDefault ?: -1
            st.curT = q.toleranceMsDefault
            return st
        }
        val o = JSONObject(raw)
        st.seed = o.optInt("seed")
        st.levelId = o.optInt("level", 1)
        st.taskId = o.optInt("task", 1)
        st.curN = o.optInt("n", -1)
        st.curT = o.optInt("t", 0)
        st.winAttempts = o.optInt("wa")
        st.winWins = o.optInt("ww")
        st.winMaxErr = o.optInt("wm")
        st.winLastIdx = o.optInt("wl")
        st.winFirstIdx = o.optInt("wf")
        st.wasIndulged = o.optBoolean("ind")
        st.sessionId = o.optInt("sid")
        st.sessionStart = o.optLong("ss")
        st.lastEndTime = o.optLong("le")
        o.optString("sw").forEach { st.sessionWins += it == '1' }
        st.prev1 = Brief.of(o.optJSONObject("p1"))
        st.prev2 = Brief.of(o.optJSONObject("p2"))
        st.player = o.optJSONObject("mind")
            ?.let { runCatching { MicroPlayer.fromJson(it) }.getOrNull() }
            ?.also { it.readBrain(brainFile) }
        return st
    }

    private fun saveState(st: State) {
        val o = JSONObject()
            .put("seed", st.seed).put("level", st.levelId).put("task", st.taskId)
            .put("n", st.curN).put("t", st.curT)
            .put("wa", st.winAttempts).put("ww", st.winWins).put("wm", st.winMaxErr)
            .put("wl", st.winLastIdx).put("wf", st.winFirstIdx).put("ind", st.wasIndulged)
            .put("sid", st.sessionId).put("ss", st.sessionStart).put("le", st.lastEndTime)
            .put("sw", st.sessionWins.joinToString("") { if (it) "1" else "0" })
        st.prev1?.let { o.put("p1", it.json()) }
        st.prev2?.let { o.put("p2", it.json()) }
        st.player?.let { o.put("mind", it.toJson()) }
        prefs.edit().putString(KEY_STATE, o.toString()).commit()
    }

    data class DiaryEntry(val n: Int, val time: Long, val sessionId: Int, val text: String)

    companion object {
        const val NAME = "Doppelgänger"

        /**
         * Квест крупно, с точностью до индульгенции (Alex 11.11.2026: «крупным —
         * про квест с точностью до индульгенций, уровень — мелочью под»):
         * "Quest 2.3.1" (ступень ослабления после точки) и условие
         * "3.00 ± 0.01 · 3× in a row" / "3.00 · 2× in 66 attempts".
         */
        fun questBadge(levelId: Int, taskId: Int, curN: Int, curT: Int): Pair<String, String> {
            val q = getCurrentQuest(levelId, taskId)
            val digits = q.accuracy.ordinal.coerceIn(1, 3)
            val cell = when (digits) { 1 -> 100; 2 -> 10; else -> 1 }
            val base = q.maxAttemptsDefault
            val n = if (base != null && curN > 0) curN else base
            val stepN = if (q.adaptiveN && base != null && n != null && n > base) (n - base) / q.adaptiveNStep else 0
            val t = if (q.adaptiveT) maxOf(curT, q.toleranceMsDefault) else q.toleranceMsDefault
            val stepT = if (q.adaptiveT) (t - q.toleranceMsDefault) / cell else 0
            val sub = stepN + stepT
            val title = "Quest ${q.levelId}.${q.taskId}" + if (sub > 0) ".$sub" else ""
            val aim = "3." + "0".repeat(digits)
            val tol = if (t > 0) " ± " + "%.${digits}f".format(java.util.Locale.US, t / 1000.0) else ""
            val c = q.victory.victoryCount
            val cond = when (q.victory.victoryCondition) {
                VictoryCondition.SESSION -> "$c× in total"
                VictoryCondition.ROW -> "$c× in a row"
                VictoryCondition.IN_N -> "$c× in ${n ?: "—"} attempts"
            }
            return title to "$aim$tol · $cond"
        }
        private const val PREFS = "doppelganger"
        private const val KEY_STATE = "state"
        private const val DIARY_FILE = "doppelganger_diary.jsonl"
        /** Его синапсы «клетки Кеньона → выходы» (MicroPlayer.writeBrain). */
        private const val BRAIN_FILE = "doppelganger_brain.bin"
        /** День → как он поправлялся в начале этого дня (для дневника модели). */
        private const val DAYS_FILE = "doppelganger_days.json"
        /** День → запись языковой модели + её память до и после. */
        private const val LLM_FILE = "doppelganger_llm.json"
        const val MIN_ATTEMPTS_FOR_ENTRY = 10
        /** learningCurves: попытка, которой взят квест — верхняя линия графика. */
        const val LADDER_WIN = -1
        /** learningCurves: код win в младших трёх разрядах значения. */
        const val LADDER_WIN_CODE = 999
        private const val FIRST_MEMORY = "Nothing yet. I have never played."
        private val QUEST_EVENT = Regex("^(Quest|Level|Done|Didn't make|Another window)")
        private val QUEST_DONE = Regex("Quest (\\d+\\.\\d+) done")
        private val LEVEL_DONE = Regex("^Level (\\d+) complete")
        private val EASED_DONE = Regex("^Done (\\d+\\.\\d+) — but on eased terms")
        private val WINDOW_FAILED = Regex("^(Didn't make|Another window)")

        private fun dayKey(ms: Long): String =
            java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(ms))

        private fun dayStartMs(key: String): Long =
            java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).parse(key)?.time ?: 0L
        private const val BATCH = 500
        private const val SESSION_BREAK_MS = 30 * 60_000L
        private const val MIN_PAUSE_MS = 400L
    }
}
