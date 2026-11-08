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
 *     "doppelganger";
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

    /** Current quest of the rival — "L.T" for the UI. */
    fun questLabel(): String = loadState().let { "${it.levelId}.${it.taskId}" }

    /** Diary, oldest first. */
    suspend fun diary(): List<DiaryEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!diaryFile.exists()) return@withLock emptyList()
            diaryFile.readLines().mapNotNull { line ->
                runCatching {
                    val o = JSONObject(line)
                    DiaryEntry(o.getInt("n"), o.getLong("t"), o.getInt("s"), o.getString("x"))
                }.getOrNull()
            }
        }
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
            rivalDb.withTransaction {
                for (src in batch) playOne(st, player, src, notes)
            }
            saveState(st)
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
        // (an imported old history). Like the player's app closing a session.
        if (st.sessionId != 0 && (src.startTime < st.lastEndTime ||
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
        val start = src.startTime
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
        st.player = o.optJSONObject("mind")?.let { runCatching { MicroPlayer.fromJson(it) }.getOrNull() }
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
        private const val PREFS = "doppelganger"
        private const val KEY_STATE = "state"
        private const val DIARY_FILE = "doppelganger_diary.jsonl"
        private const val BATCH = 500
        private const val SESSION_BREAK_MS = 30 * 60_000L
    }
}
