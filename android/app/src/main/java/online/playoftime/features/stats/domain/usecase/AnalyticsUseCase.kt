package online.playoftime.features.stats.domain.usecase

import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.core.utils.resolutionFloorMs
import online.playoftime.database.SessionDatabaseModel
import online.playoftime.database.TimeDatabase
import online.playoftime.database.TryDatabaseModel
import online.playoftime.features.stats.domain.entity.AnalyticsData
import online.playoftime.features.stats.domain.entity.AttemptBarData
import online.playoftime.features.stats.domain.entity.DeviationProfile
import online.playoftime.features.stats.domain.entity.SessionTreeData
import online.playoftime.features.stats.domain.entity.TopDeviation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

class AnalyticsUseCase(
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService
) {
    suspend operator fun invoke(
        period: PeriodFilter = PeriodFilter.ALL_TIME,
        // Сколько периодов НАЗАД от текущего листать (0 = текущий) — клювики
        // влево/вправо под фильтром на экране аналитики (Alex 2026-09-09).
        // Игнорируется для ALL_TIME.
        offset: Int = 0
    ): AnalyticsData {
        val statDao    = database.statisticDao()
        val tryDao     = database.tryDao()
        val sessionDao = database.sessionDao()
        val aimTime    = localStorage.getAimTime()

        // ── Базовые агрегаты (ALL_TIME для шапки Summary) ────────────────
        val totalScm        = statDao.getTotalSerenityBalance()
        val sessionsCount   = statDao.getTotalSessionsCount()
        val totalPlayTimeMs = statDao.getTotalTime()
        val totalAttempts   = statDao.getTotalAttemptsCount()
        val totalWins       = statDao.getTotalWinsCount(aimTime)
        val daysSinceStart  = statDao.getTotalDaysCount()
        val startTimestamp  = statDao.getTotalSessionsDate()

        val startDateStr = if (startTimestamp > 0L) {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(Date(startTimestamp))
        } else "—"

        // ── Попытки и сессии за выбранный период (с учётом offset — Alex
        //    2026-09-09: клювики влево/вправо листают день/неделю/месяц) ────
        val effectiveOffset = if (period == PeriodFilter.ALL_TIME) 0 else offset
        val (fromTime, toTime) = when (period) {
            PeriodFilter.TODAY, PeriodFilter.WEEK, PeriodFilter.MONTH ->
                period.rangeMsForOffset(effectiveOffset)
            PeriodFilter.SESSION, PeriodFilter.ALL_TIME ->
                period.startTimeMs() to Long.MAX_VALUE // не используется ниже для этих типов
        }
        // Для TODAY/MONTH берём «сессии, затронувшие период» — все попытки этих сессий,
        // даже если сессия началась до полуночи / до месяца. Согласовано с MainMetricsUseCase
        // и GetLogAttemptsUseCase: иначе Σtries на главном экране ≠ Σtries на аналитике
        // (и геом. средние тоже расходятся) для одного и того же фильтра.
        val touchingSessionIds: Set<Int> = when (period) {
            PeriodFilter.ALL_TIME, PeriodFilter.SESSION -> emptySet()
            else -> tryDao.getAttemptsInRange(fromTime, toTime).map { it.sessionId }.toSet()
        }

        // SESSION: не «последняя сессия», а N-я С КОНЦА (N = offset, 0 = самая
        // свежая). lazy{} тут не годится — getAllSessions() suspend — поэтому
        // считаем один раз обычным условием, только когда период реально SESSION.
        val sessionsByRecency: List<SessionDatabaseModel> =
            if (period == PeriodFilter.SESSION)
                sessionDao.getAllSessions().sortedByDescending { it.startTime }
            else emptyList()
        val targetSession: SessionDatabaseModel? =
            if (period == PeriodFilter.SESSION) sessionsByRecency.getOrNull(effectiveOffset) else null

        val periodAttempts: List<TryDatabaseModel> = when (period) {
            PeriodFilter.ALL_TIME -> tryDao.getAllAttempts()
            PeriodFilter.SESSION  ->
                targetSession?.let { tryDao.getAttemptsBySessionId(it.id) } ?: emptyList()
            else -> touchingSessionIds
                .flatMap { sid -> tryDao.getAttemptsBySessionId(sid) }
                .sortedBy { it.startTime }
        }

        val periodSessions: List<SessionDatabaseModel> = when (period) {
            PeriodFilter.ALL_TIME -> sessionDao.getAllSessions()
            PeriodFilter.SESSION  -> targetSession?.let { listOf(it) } ?: emptyList()
            else -> sessionDao.getAllSessions().filter { it.id in touchingSessionIds }
        }

        // ── Можно ли листать дальше в ту или другую сторону ──────────────
        // Вперёд (к "сейчас") — просто пока offset > 0. Назад — есть ли вообще
        // история раньше начала текущего периода/сессии.
        val hasNewerPeriod = period != PeriodFilter.ALL_TIME && effectiveOffset > 0
        val hasOlderPeriod = when (period) {
            PeriodFilter.ALL_TIME -> false
            PeriodFilter.SESSION  -> effectiveOffset + 1 < sessionsByRecency.size
            else                  -> startTimestamp in 1 until fromTime
        }

        val periodScm = periodAttempts.mapNotNull { it.scm }.filter { it > 0 }.sum()

        // ── Ошибки периода ────────────────────────────────────────────────
        val errorsMs       = periodAttempts.map { (it.endTime - it.startTime - aimTime).toDouble() }

        // Трим 3–97% (Alex 2026-07-23): профили GEOMETRIC MEAN считаются по той
        // же популяции, что и Range of Deviations — крайние 3% знаковых ошибок
        // с КАЖДОЙ стороны отброшены, выбросы не тянут среднее и разброс.
        val trimmedErrorsMs = if (errorsMs.size >= 3) {
            val sorted = errorsMs.sorted()
            val lo = sorted[(sorted.size * 0.03).toInt().coerceIn(0, sorted.size - 1)]
            val hi = sorted[(sorted.size * 0.97).toInt().coerceIn(0, sorted.size - 1)]
            errorsMs.filter { it in lo..hi }
        } else errorsMs

        val absErrorsMs    = trimmedErrorsMs.map { abs(it) }
        // Absolute (deviationAll) — ЗНАКОВОЕ смещение центра масс длительностей
        // относительно aim: geomMean(durations) − aim. Согласовано с главным экраном
        // (см. MainMetricsUseCase). Это индикатор баланса: <0 при перекосе early,
        // >0 при перекосе late, ≈0 при балансе.
        // stdPct берём от модульного профиля — это спред величины промаха, осмысленно
        // как «насколько разнотипны отклонения», знак не нужен.
        val absDeviationProfile  = computeProfile(absErrorsMs)
        // Absolute (смещение центра масс) — по тем же тримленным ошибкам:
        // duration = error + aim.
        val durationsMs          = trimmedErrorsMs.map { it + aimTime.toDouble() }
        val signedAbsoluteMs     = if (durationsMs.isNotEmpty()) {
            val positiveDurations = durationsMs.filter { it > 0.0 }
            if (positiveDurations.isEmpty()) 0.0
            else exp(positiveDurations.sumOf { ln(it) } / positiveDurations.size) - aimTime.toDouble()
        } else 0.0
        val deviationAll   = DeviationProfile(
            medianMs = signedAbsoluteMs,
            stdPct   = absDeviationProfile.stdPct
        )
        val earlyErrorsMs  = trimmedErrorsMs.filter { it < 0 }.map { abs(it) }
        val lateErrorsMs   = trimmedErrorsMs.filter { it > 0 }
        val deviationEarly = computeProfile(earlyErrorsMs)
        val deviationLate  = computeProfile(lateErrorsMs)

        // ── Range of Errors (3rd & 97th percentile on signed errors) ────
        val sortedSigned = errorsMs.sorted()
        val rangeEarlyMs = if (sortedSigned.isNotEmpty()) {
            val idx = (sortedSigned.size * 0.03).toInt().coerceIn(0, sortedSigned.size - 1)
            sortedSigned[idx]
        } else 0.0
        val rangeLateMs = if (sortedSigned.isNotEmpty()) {
            val idx = (sortedSigned.size * 0.97).toInt().coerceIn(0, sortedSigned.size - 1)
            sortedSigned[idx]
        } else 0.0

        // ── True Three Hit Rate ───────────────────────────────────────────
        val trueThreeWins    = countTrueThree(periodAttempts, aimTime)
        val periodTotal      = periodAttempts.size.toLong()
        val trueThreeHitRate = if (periodTotal > 0)
            trueThreeWins.toDouble() / periodTotal * 100.0 else 0.0
        val attemptsPerWin   = if (trueThreeWins > 0)
            periodTotal.toDouble() / trueThreeWins else 0.0

        // ── Top Deviations ───────────────────────────────────────────────
        // Раундинг берём с КАЖДОЙ попытки персонально (att.accuracy), а не от
        // текущего уровня игрока. Иначе сессия, сыгранная на L2 (центи, шаг 10мс),
        // при текущем L3 (милли, 1мс) показывала бы «фейковые» модусы вроде −47/−33,
        // которых на L2 быть не могло — там видны только многократные 10мс.
        // Также:
        //   • выкидываем нули — ранняя/поздняя ошибка, схлопнувшаяся в 0 после
        //     округления, не является валидным early/late модусом;
        //   • требуем count ≥ 2 — настоящий модус = повтор, единичные значения
        //     это просто отдельные попытки, не «modes of deviation».
        val earlyRoundedMs: List<Double> = periodAttempts.mapNotNull { att ->
            val errMs = (att.endTime - att.startTime - aimTime).toDouble()
            if (errMs >= 0.0) return@mapNotNull null
            val rounding = accuracyRoundingMs(att.accuracy)
            val rounded = (abs(errMs) / rounding).roundToLong() * rounding
            rounded.takeIf { it > 0.0 }
        }
        val lateRoundedMs: List<Double> = periodAttempts.mapNotNull { att ->
            val errMs = (att.endTime - att.startTime - aimTime).toDouble()
            if (errMs <= 0.0) return@mapNotNull null
            val rounding = accuracyRoundingMs(att.accuracy)
            val rounded = (errMs / rounding).roundToLong() * rounding
            rounded.takeIf { it > 0.0 }
        }
        val topEarly = topDeviations(earlyRoundedMs, negated = true)
        val topLate  = topDeviations(lateRoundedMs,  negated = false)

        // ── Lucky Index ───────────────────────────────────────────────────
        // Lucky = эмпирическая вероятность (чистые тройки / все попытки) ÷
        // теоретическая (1 благоприятный слот / все слоты диапазона разброса).
        // ВАЖНО (Alex 2026-07-30): Lucky ВСЕГДА считается «как на уровне 3» —
        // и числитель, и знаменатель в тысячных, независимо от реального уровня
        // попыток. Слот = 1 мс; «чистая тройка» числителя = ровно 3.000 (±0.5 мс).
        // Это внутренняя нормировка (пользователю нигде больше не видна) — делает
        // Lucky сравнимым между уровнями. Отдельный счёт luckyWins, чтобы НЕ задеть
        // True Three Hit Rate (там тройка считается в точности своего уровня).
        val luckyWins      = periodAttempts.count {
            abs((it.endTime - it.startTime - aimTime).toDouble()) < 0.5
        }
        val slotMs         = 1.0
        val rangeSpanMs    = abs(rangeEarlyMs) + abs(rangeLateMs)
        val outcomeSlots   = (rangeSpanMs / slotMs).toLong().coerceAtLeast(1L) + 1L
        val dispersionProb = 1.0 / outcomeSlots
        val empiricalProb  = if (periodTotal > 0) luckyWins.toDouble() / periodTotal else 0.0
        val luckyIndex     = if (dispersionProb > 0) empiricalProb / dispersionProb else 0.0

        // ── Pace of Play (попыток/мин за период) ─────────────────────────
        // Play Time = сумма activeTimeMs (только пока MainScreen был активен),
        // а не wall-clock (endTime − startTime). Для сессий до миграции 6→7
        // activeTimeMs бэкфилл'ен из (endTime − startTime), так что старые
        // числа сохранены с прежней (переоценённой) точностью.
        val periodPlayMs  = periodSessions.sumOf { it.activeTimeMs }
        val periodPlayMin = periodPlayMs / 60_000.0
        val paceOfPlay    = if (periodPlayMin > 0) periodTotal / periodPlayMin else 0.0

        // ── Pace of Learning (SCM / попытка за период) ───────────────────
        // Делим на число попыток, у которых SC реально посчитан (scm != null,
        // scm > 0), а НЕ на periodTotal. Самая свежая попытка в истории по
        // дизайну ещё без SC (SaveScmAfterAttemptUseCase считает его ретроактивно
        // для N−1, когда приходит N) — раньше она трактовалась как SC=0 и делила
        // общую сумму, occasioning заниженное/скачущее значение, особенно заметное
        // на коротких периодах (Session/Today), где одна такая попытка — заметная
        // доля выборки.
        val scoredAttemptsCount = periodAttempts.count { (it.scm ?: 0.0) > 0.0 }
        val paceOfLearning = if (scoredAttemptsCount > 0) periodScm / scoredAttemptsCount else 0.0

        // ── Practice Density (мин игры / игровой день за ВЫБРАННЫЙ период) ─
        // Раньше всегда считалось за всё время и показывало одно число на всех
        // периодах (Alex 2026-10-24: «конечно считать за выбранный период»).
        // Смысл прежний — среднее время игры за день, в который играл: минуты
        // игры периода (activeTimeMs его сессий) ÷ число разных дней с
        // попытками внутри периода.
        val practiceDensity = run {
            val inRange = when (period) {
                PeriodFilter.TODAY, PeriodFilter.WEEK, PeriodFilter.MONTH ->
                    periodAttempts.filter { it.startTime in fromTime until toTime }
                else -> periodAttempts
            }
            val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
            val days = inRange.map { dayFmt.format(Date(it.startTime)) }.toSet().size
            if (days > 0) (periodPlayMs / 60_000.0) / days else 0.0
        }

        // ── Допуск квеста (для подсветки попыток в допуске) ──────────────
        val toleranceMs = localStorage.getQuestCurrentToleranceMs().toDouble()

        // ── Per-day session numbering (Alex 2026-07-23): номер сессии
        // считается ВНУТРИ дня (первая сессия дня = #1), не сквозной по истории.
        // Формула вынесена в общую online.playoftime.core.utils.perDaySessionNumbers
        // (Alex 2026-09-22) — раньше здесь и в GetLogAttemptsUseCase.kt жили две
        // РАЗНЫЕ формулы (эта — по дню, там — глобальная за всю историю),
        // разошедшиеся незаметно на два месяца; отсюда "Session #130" на главном
        // экране при реальных 10-15 сессиях за день.
        val allSessionsOrdered = sessionDao.getAllSessions().sortedBy { it.startTime }
        val sessionNumberMap = online.playoftime.core.utils.perDaySessionNumbers(allSessionsOrdered)

        // Номер (внутри дня) той сессии, что сейчас показана при period == SESSION —
        // та же нумерация, что и в дереве сессий, чтобы подпись под фильтром
        // ("Session #N") и дерево не расходились в счёте.
        val selectedSessionNumber: Int? = targetSession?.let { sessionNumberMap[it.id] ?: it.id }

        // Активная (незавершённая) сессия — без индикатора успешности.
        val activeSessionId = runCatching { sessionDao.getCurrentSession() }.getOrNull()
            ?.takeIf { !it.isQuestComplete }?.id

        // ── Sessions Log (Alex 2026-07-23): дерево сессий показывает ВСЮ
        // историю и НЕ зависит от переключателя периодов — он управляет
        // только верхними карточками.
        val sessionsList = allSessionsOrdered.map { session ->
            val sAttempts = tryDao.getAttemptsBySessionId(session.id)
            val wins      = countTrueThree(sAttempts, aimTime)

            // Бары попыток: green = true three OR в допуске квеста
            val bars = sAttempts.mapIndexed { idx, att ->
                val errMs       = (att.endTime - att.startTime - aimTime).toDouble()
                val isTrueThree = countTrueThree(listOf(att), aimTime) > 0
                val inTolerance = toleranceMs > 0 && abs(errMs) <= toleranceMs
                AttemptBarData(
                    number     = idx + 1,
                    errorMs    = errMs,
                    isWin      = isTrueThree || inTolerance,
                    durationMs = att.endTime - att.startTime,
                    accuracy   = att.accuracy
                )
            }

            SessionTreeData(
                sessionId       = session.id,
                sessionNumber   = sessionNumberMap[session.id] ?: session.id,
                startTime       = session.startTime,
                endTime         = session.endTime,
                activeTimeMs    = session.activeTimeMs,
                attempts        = sAttempts.size,
                wins            = wins,
                isQuestComplete = session.isQuestComplete,
                questLevelId    = session.questLevelId,
                questTaskId     = session.questTaskId,
                attemptBars     = bars,
                thumbUp         = if (session.id == activeSessionId) null
                                  else session.isQuestComplete
            )
        }.sortedByDescending { it.startTime }

        // ── Sparklines (последние 5 сессий, хронологически) ──────────────
        // sessionsList отсортирован НОВЕЙШИЕ-ПЕРВЫМИ → последние 5 сессий это
        // take(5), не takeLast(5). Прежний takeLast(5) брал 5 СТАРЕЙШИХ сессий
        // периода — спарклайны показывали древнюю историю вместо свежего тренда.
        // sessionsList теперь ВСЯ история (Sessions Log отвязан от периода),
        // поэтому спарклайны — карточка, зависящая от периода — фильтруются
        // по сессиям выбранного периода отдельно.
        val periodSessionIds  = periodSessions.map { it.id }.toSet()
        val sparklineSessions = sessionsList
            .filter { it.sessionId in periodSessionIds }
            .take(5).sortedBy { it.startTime }
        val sparklinePace = sparklineSessions.map { s ->
            // SCM в bars не хранится; берём из tryDao через sessionId
            val sScm = tryDao.getAttemptsBySessionId(s.sessionId)
                .mapNotNull { it.scm }.filter { it > 0 }.sum()
            if (s.attempts == 0) 0f else (sScm / s.attempts).toFloat()
        }
        val sparklineWin = sparklineSessions.map { s ->
            if (s.attempts == 0) 0f else s.wins.toFloat() / s.attempts.toFloat()
        }

        // ── Pace of Learning chart (Alex 2026-07-30) ─────────────────────
        // Ось X зависит от выбранного периода: All time → месяцы, Month → дни,
        // Today → часы, Session → попытки. Значение бакета = средняя ЭФФЕКТИВНОСТЬ
        // попытки (0..1), НЕ средний сырой SC.
        //
        // Баг #1 (найден 2026-08-13): самая свежая попытка в истории по дизайну ещё
        // не имеет SC (см. SaveScmAfterAttemptUseCase — считается ретроактивно для
        // N−1, когда приходит N). Раньше `it.scm ?: 0.0` трактовал это как честный
        // ноль и делил СУММУ бакета на ПОЛНОЕ число попыток в нём. Фикс: попытки
        // без посчитанного SC исключаются из числителя И знаменателя везде ниже.
        //
        // Баг #2 (найден 2026-08-14): «монотонная, очевидно ошибочная линия на
        // All time». Причина — CalculateScmUseCase домножает сырой SC на
        // levelFactor = 10^(accuracy−3): L3 стоит на два порядка больше «сырых»
        // единиц, чем L1, ПРИ ОДИНАКОВОМ качестве попадания. Если за всю историю
        // игрок прошёл несколько уровней, помесячное среднее сырого SC почти
        // всегда рисует резкий монотонный скачок вверх ровно в месяц перехода на
        // новый уровень — это артефакт масштаба валюты, а не реальный тренд
        // обучения (то же явление, что и известная нерешённая чувствительность
        // Lucky Index к уровню). Фикс: делим каждый scm на «потолок» SC для
        // ТОЧНОСТИ этой конкретной попытки (peakScmForAccuracy) — получаем
        // эффективность 0..1, сравнимую между уровнями, левелфактор сокращается.
        // Тот же бакет даёт ещё два ряда для выпадающего списка над графиком
        // (Alex 2026-10-07): Hit rate (% побед с допуском, как в Chance of Win) и
        // Lucky index бакета (та же формула, что headline Lucky).
        // Рожицы главного экрана (Alex 2026-10-19, график Faces): после каждой
        // попытки, кроме первой в сессии, лицо весёлое, если ошибка (округлённая
        // до точности уровня, как на экране) по модулю не больше предыдущей
        // попытки той же сессии, и грустное, если больше. Та же логика, что
        // faceKind в MainScreen.kt.
        val faceOf = java.util.IdentityHashMap<TryDatabaseModel, Boolean>().also { m ->
            val prevAbs = HashMap<Int, Double>()
            periodAttempts.sortedBy { it.startTime }.forEach { att ->
                val e = abs(displayErrorMs(att, aimTime))
                prevAbs[att.sessionId]?.let { m[att] = e <= it }
                prevAbs[att.sessionId] = e
            }
        }
        fun happyIn(b: List<TryDatabaseModel>) = b.count { faceOf[it] == true }.toFloat()
        fun sadIn(b: List<TryDatabaseModel>)   = b.count { faceOf[it] == false }.toFloat()

        val paceChart: PaceChartSeries = run {
            val ordered = periodAttempts.sortedBy { it.startTime }
            if (ordered.isEmpty()) return@run PaceChartSeries(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            // Нарезка оси X — единое правило для всех графиков (Alex 2026-10-19):
            //   Session  — каждая попытка;
            //   Today    — каждая сессия дня, подпись — её номер за день (#1, #2…);
            //   Week     — каждый день недели (7 столбиков, пустые дни — пусто);
            //   Month    — каждый день месяца (1–31);
            //   All time — недели, если история короче 9 месяцев, иначе месяцы.
            // Точка без данных (или меньше двух попыток для средних) — пустая,
            // место и подпись остаются: пропуск виден, линия не «склеивается».
            fun slotSeries(slots: List<Pair<List<TryDatabaseModel>, String>>, tips: List<String>? = null): PaceChartSeries {
                val v = slots.map { (g, _) ->
                    val scored = g.mapNotNull { att ->
                        att.scm?.takeIf { it > 0.0 }?.let { it / peakScmForAccuracy(att.accuracy) }
                    }
                    if (scored.size < 2) null else (scored.sum() / scored.size).toFloat()
                }
                val hit = slots.map { (g, _) ->
                    if (g.size < 2) null else (countTrueThree(g, aimTime).toDouble() / g.size * 100.0).toFloat()
                }
                val lucky = slots.map { (g, _) -> if (g.size < 2) null else luckyIndexOf(g, aimTime).toFloat() }
                val early = slots.map { (g, _) -> if (g.size < 2) null else sideGeomMeanMs(g, aimTime, early = true) }
                val late  = slots.map { (g, _) -> if (g.size < 2) null else sideGeomMeanMs(g, aimTime, early = false) }
                val happy = slots.map { (g, _) -> if (g.isEmpty()) null else happyIn(g) }
                val sad   = slots.map { (g, _) -> if (g.isEmpty()) null else sadIn(g) }
                val labels = slots.map { it.second }
                return PaceChartSeries(v, hit, lucky, early, late, happy, sad, labels, tips ?: labels)
            }
            // Слоты по календарю: [границы[k], границы[k+1]) с шагом step единиц field.
            fun byCalendar(startMs: Long, endMs: Long, field: Int, step: Int, labFmt: SimpleDateFormat): PaceChartSeries {
                val bounds = mutableListOf(startMs)
                val cal = Calendar.getInstance().apply { timeInMillis = startMs }
                while (bounds.last() < endMs) { cal.add(field, step); bounds.add(cal.timeInMillis) }
                val slots = (0 until bounds.size - 1).map { k ->
                    ordered.filter { it.startTime >= bounds[k] && it.startTime < bounds[k + 1] } to
                        labFmt.format(Date(bounds[k]))
                }
                return slotSeries(slots)
            }
            fun dayStart(ms: Long) = Calendar.getInstance().apply {
                timeInMillis = ms
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            when (period) {
                PeriodFilter.ALL_TIME -> {
                    val first = ordered.first().startTime
                    val last  = ordered.last().startTime + 1
                    val nineMonths = Calendar.getInstance().apply { timeInMillis = first; add(Calendar.MONTH, 9) }
                    if (last < nineMonths.timeInMillis) {
                        val wk = dayStart(first).apply {
                            while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(Calendar.DAY_OF_YEAR, -1)
                        }
                        byCalendar(wk.timeInMillis, last, Calendar.DAY_OF_YEAR, 7,
                            SimpleDateFormat("d MMM", Locale.ENGLISH))
                    } else {
                        val mo = dayStart(first).apply { set(Calendar.DAY_OF_MONTH, 1) }
                        byCalendar(mo.timeInMillis, last, Calendar.MONTH, 1,
                            SimpleDateFormat("MMM ''yy", Locale.ENGLISH))
                    }
                }
                PeriodFilter.MONTH ->
                    byCalendar(fromTime, toTime, Calendar.DAY_OF_YEAR, 1, SimpleDateFormat("d", Locale.ENGLISH))
                PeriodFilter.WEEK ->
                    byCalendar(fromTime, toTime, Calendar.DAY_OF_YEAR, 1, SimpleDateFormat("EEE", Locale.ENGLISH))
                // День — по сессиям; под столбиком номер сессии за день, тот же,
                // что в журнале и в «Session #N» (Alex 2026-10-20); в подсказке
                // по удержанию — ещё и время начала сессии.
                PeriodFilter.TODAY -> {
                    val fmt = SimpleDateFormat("HH:mm", Locale.ENGLISH)
                    val bySession = ordered.groupBy { it.sessionId }
                    slotSeries(
                        bySession.map { (sid, g) -> g to "#${sessionNumberMap[sid] ?: sid}" },
                        tips = bySession.map { (sid, g) -> "#${sessionNumberMap[sid] ?: sid} · ${fmt.format(Date(g.first().startTime))}" }
                    )
                }
                PeriodFilter.SESSION  -> {
                    // Session — сырые точки по попыткам, не по календарю: пропусков
                    // тут нет по определению (последовательные попытки одной сессии),
                    // так что null-разрывы не нужны, все non-null.
                    // Столбик на каждую попытку сессии, с первой по последнюю, и без
                    // усреднения: в сессии каждый столбик — сама попытка (Alex
                    // 2026-10-19: «в сессии должны быть не сдвоенные столбики, а по
                    // одному столбику в каждой попытке; только начиная с уровня дня
                    // столбики могут быть агрегированными»).
                    //  Pace       — эффективность попытки (у последней SC ещё нет — пусто);
                    //  Hit rate   — 100, если попытка — победа с допуском, иначе 0;
                    //  Lucky      — 1, если ровно 3.000 (как считает Lucky), иначе 0;
                    //  Mean error — модуль ошибки попытки, мс: в ряд early, если рано,
                    //               в ряд late, если поздно — один столбик своего цвета;
                    //  Faces      — 1 в ряд happy или sad по рожице после попытки.
                    val idxs = ordered.indices.toList()
                    fun err(i: Int) = (ordered[i].endTime - ordered[i].startTime - aimTime).toDouble()
                    PaceChartSeries(
                        values = idxs.map { i ->
                            ordered[i].scm?.takeIf { it > 0.0 }?.let { (it / peakScmForAccuracy(ordered[i].accuracy)).toFloat() }
                        },
                        hit    = idxs.map { i -> if (countTrueThree(listOf(ordered[i]), aimTime) > 0) 100f else 0f },
                        lucky  = idxs.map { i -> if (abs(err(i)) < 0.5) 1f else 0f },
                        early  = idxs.map { i -> err(i).takeIf { it < 0 }?.let { abs(it).toFloat() } },
                        late   = idxs.map { i -> err(i).takeIf { it > 0 }?.toFloat() },
                        happy  = idxs.map { i -> if (faceOf[ordered[i]] == true) 1f else null },
                        sad    = idxs.map { i -> if (faceOf[ordered[i]] == false) 1f else null },
                        labels = idxs.map { "#${it + 1}" }
                    )
                }
            }
        }

        return AnalyticsData(
            periodOffset              = effectiveOffset,
            hasOlderPeriod            = hasOlderPeriod,
            hasNewerPeriod            = hasNewerPeriod,
            selectedSessionNumber     = selectedSessionNumber,
            totalScm                 = totalScm,
            periodScm                = periodScm,
            sessionsCount            = sessionsCount,
            totalPlayTimeMs          = totalPlayTimeMs,
            totalAttempts            = totalAttempts,
            totalWins                = totalWins,
            daysSinceStart           = daysSinceStart,
            startDateStr             = startDateStr,
            deviationAll             = deviationAll,
            deviationEarly           = deviationEarly,
            deviationLate            = deviationLate,
            rangeEarlyMs             = rangeEarlyMs,
            rangeLateMs              = rangeLateMs,
            trueThreeHitRatePct      = trueThreeHitRate,
            attemptsPerWin           = attemptsPerWin,
            luckyIndex               = luckyIndex,
            topEarly                 = topEarly,
            topLate                  = topLate,
            periodPlayTimeMs         = periodPlayMs,
            periodWins               = trueThreeWins.toLong(),
            periodPrecise            = countPrecise(periodAttempts, aimTime).toLong(),
            periodFromMs             = periodAttempts.minOfOrNull { it.startTime } ?: 0L,
            periodToMs               = periodAttempts.maxOfOrNull { it.startTime } ?: 0L,
            periodSessionsCount      = periodSessions.size.toLong(),
            // Счётчики attempts ratio — по ПОЛНОЙ популяции (трим 3–97% касается
            // только профилей геом. среднего, не количества попыток). Победа
            // (в т.ч. облегчённая, 2.999 при допуске) считается только зелёной
            // и в красный/синий не попадает (Alex 2026-10-06) — иначе сумма
            // трёх счётчиков была больше числа попыток.
            earlyCount               = periodAttempts.count { att ->
                (att.endTime - att.startTime - aimTime) < 0 && countTrueThree(listOf(att), aimTime) == 0
            }.toLong(),
            lateCount                = periodAttempts.count { att ->
                (att.endTime - att.startTime - aimTime) > 0 && countTrueThree(listOf(att), aimTime) == 0
            }.toLong(),
            paceOfPlayPerMin         = paceOfPlay,
            paceOfLearning           = paceOfLearning,
            practiceDensityMinPerDay     = practiceDensity,
            sparklinePaceOfLearning      = sparklinePace,
            sparklineChanceOfWin         = sparklineWin,
            sparklineSessionDates        = sparklineSessions.map { it.startTime },
            paceChartValues              = paceChart.values,
            paceChartHitRate             = paceChart.hit,
            paceChartLucky               = paceChart.lucky,
            paceChartEarlyMs             = paceChart.early,
            paceChartLateMs              = paceChart.late,
            paceChartHappy               = paceChart.happy,
            paceChartSad                 = paceChart.sad,
            missMeanEarlyMs              = sideGeomMeanMs(periodAttempts, aimTime, early = true),
            missMeanLateMs               = sideGeomMeanMs(periodAttempts, aimTime, early = false),
            facesHappy                   = faceOf.values.count { it },
            facesSad                     = faceOf.values.count { !it },
            paceChartLabels              = paceChart.labels,
            paceChartTips                = paceChart.tips,
            sessions                     = sessionsList,
            periodAttempts           = periodTotal,
            period                   = period,
            toleranceMs              = toleranceMs
        )
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    /**
     * Максимально возможный SC для попытки данной точности (accuracy) —
     * достигается при denominator=1 в CalculateScmUseCase (нулевая ошибка,
     * идеально ровные соседи): 99 × levelFactor, levelFactor = 10^(accuracy−3).
     * Используется чтобы нормализовать sc/peak → эффективность 0..1, сравнимую
     * между уровнями (см. Pace of Learning chart ниже).
     */
    private fun peakScmForAccuracy(accuracy: Int): Double =
        99.0 * 10.0.pow((accuracy - 3).toDouble())

    private class PaceChartSeries(
        val values: List<Float?>, val hit: List<Float?>, val lucky: List<Float?>,
        val early: List<Float?>, val late: List<Float?>,
        val happy: List<Float?>, val sad: List<Float?>, val labels: List<String>,
        // Подпись точки в подсказке по удержанию (у дня — номер и время сессии).
        val tips: List<String> = labels
    )

    /**
     * Геометрическое среднее ошибки одной стороны, мс — ровно как карточка
     * Geometric Mean ÷ Spread (Alex 2026-10-19: на графике Mean error должно
     * быть геометрическое среднее, как там): все попытки, попадания тоже,
     * крайние 3% знаковых ошибок с каждой стороны отброшены (при ≥ 3 попытках),
     * затем computeProfile по ранним или поздним. null — ошибок этой стороны нет.
     */
    private fun sideGeomMeanMs(attempts: List<TryDatabaseModel>, aimTime: Int, early: Boolean): Float? {
        val errs = attempts.map { (it.endTime - it.startTime - aimTime).toDouble() }
        val trimmed = if (errs.size >= 3) {
            val sorted = errs.sorted()
            val lo = sorted[(sorted.size * 0.03).toInt().coerceIn(0, sorted.size - 1)]
            val hi = sorted[(sorted.size * 0.97).toInt().coerceIn(0, sorted.size - 1)]
            errs.filter { it in lo..hi }
        } else errs
        val side = if (early) trimmed.filter { it < 0 }.map { abs(it) } else trimmed.filter { it > 0 }
        if (side.isEmpty()) return null
        return computeProfile(side).medianMs.toFloat()
    }

    /** Ошибка попытки, округлённая до точности её уровня, мс — как на экране. */
    private fun displayErrorMs(att: TryDatabaseModel, aimTime: Int): Double {
        val factor = 10.0.pow(att.accuracy)
        val durRounded = ((att.endTime - att.startTime) / 1000.0 * factor).roundToLong()
        val aimRounded = (aimTime / 1000.0 * factor).roundToLong()
        return (durRounded - aimRounded) * (1000.0 / factor)
    }

    /** Lucky index набора попыток — та же формула, что headline Lucky выше. */
    private fun luckyIndexOf(attempts: List<TryDatabaseModel>, aimTime: Int): Double {
        if (attempts.isEmpty()) return 0.0
        val errs = attempts.map { (it.endTime - it.startTime - aimTime).toDouble() }.sorted()
        val lo = errs[(errs.size * 0.03).toInt().coerceIn(0, errs.size - 1)]
        val hi = errs[(errs.size * 0.97).toInt().coerceIn(0, errs.size - 1)]
        val slots = ((abs(lo) + abs(hi)) / 1.0).toLong().coerceAtLeast(1L) + 1L
        val luckyWins = errs.count { abs(it) < 0.5 }
        return luckyWins.toDouble() / errs.size * slots
    }

    private fun countTrueThree(attempts: List<TryDatabaseModel>, aimTime: Int): Int {
        return attempts.count { attempt ->
            val decimals   = attempt.accuracy
            val factor     = 10.0.pow(decimals)
            val aimRounded = (aimTime / 1000.0 * factor).roundToLong()
            val durRounded = ((attempt.endTime - attempt.startTime) / 1000.0 * factor).roundToLong()
            // Округлённая до ячейки уровня ошибка в мс, сравниваем с допуском ИЛИ
            // полом разрешения (на L3 ±4 мс — точный 0 недостижим касанием).
            val roundedErrMs = (durRounded - aimRounded).toDouble() * (1000.0 / factor)
            val winTol = maxOf(attempt.toleranceMs, resolutionFloorMs(attempt.accuracy))
            abs(roundedErrMs) <= winTol + 0.5
        }
    }

    /** Чистые тройки: ошибка, округлённая до ячейки уровня, равна нулю (без допуска). */
    private fun countPrecise(attempts: List<TryDatabaseModel>, aimTime: Int): Int {
        return attempts.count { attempt ->
            val factor     = 10.0.pow(attempt.accuracy)
            val aimRounded = (aimTime / 1000.0 * factor).roundToLong()
            val durRounded = ((attempt.endTime - attempt.startTime) / 1000.0 * factor).roundToLong()
            durRounded == aimRounded
        }
    }

    private fun computeProfile(values: List<Double>): DeviationProfile {
        val positives = values.filter { it > 0.0 }
        if (positives.isEmpty()) return DeviationProfile(0.0, 0.0)
        val lnValues = positives.map { ln(it) }
        val lnMean   = lnValues.average()
        val geoMean  = exp(lnMean)
        val lnStd    = sqrt(lnValues.map { (it - lnMean).pow(2) }.average())
        val gcvPct   = (exp(lnStd) - 1.0) * 100.0
        return DeviationProfile(medianMs = geoMean, stdPct = gcvPct)
    }

    /** Раундинг в мс, соответствующий точности дисплея, при которой попытка была сделана. */
    private fun accuracyRoundingMs(accuracyOrdinal: Int): Double = when (accuracyOrdinal) {
        0    -> 1000.0   // SECONDS
        1    -> 100.0    // DECISECONDS
        2    -> 10.0     // CENTISECONDS
        else -> 1.0      // MILLISECONDS
    }

    /** Группирует уже округлённые значения и возвращает топ-3 самых частых.
     *  Значения с единичной встречаемостью отфильтрованы — модус определяется как
     *  повторяющееся значение, единичные попытки не считаются модусами. */
    private fun topDeviations(roundedMs: List<Double>, negated: Boolean): List<TopDeviation> {
        if (roundedMs.isEmpty()) return emptyList()
        return roundedMs
            .groupingBy { it }
            .eachCount()
            .entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(3)
            .map { (errMs, count) ->
                TopDeviation(errorMs = if (negated) -errMs else errMs, count = count)
            }
    }
}
