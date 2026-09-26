package ru.iflex.timemvp.features.stats.domain.usecase

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.database.SessionDatabaseModel
import ru.iflex.timemvp.database.TimeDatabase
import ru.iflex.timemvp.database.TryDatabaseModel
import ru.iflex.timemvp.features.stats.domain.entity.AnalyticsData
import ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData
import ru.iflex.timemvp.features.stats.domain.entity.DeviationProfile
import ru.iflex.timemvp.features.stats.domain.entity.SessionTreeData
import ru.iflex.timemvp.features.stats.domain.entity.TopDeviation
import java.text.SimpleDateFormat
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
        period: PeriodFilter = PeriodFilter.ALL_TIME
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

        // ── Попытки и сессии за выбранный период ─────────────────────────
        val fromTime = period.startTimeMs()
        // Для TODAY/MONTH берём «сессии, затронувшие период» — все попытки этих сессий,
        // даже если сессия началась до полуночи / до месяца. Согласовано с MainMetricsUseCase
        // и GetLogAttemptsUseCase: иначе Σtries на главном экране ≠ Σtries на аналитике
        // (и геом. средние тоже расходятся) для одного и того же фильтра.
        val touchingSessionIds: Set<Int> = when (period) {
            PeriodFilter.ALL_TIME, PeriodFilter.SESSION -> emptySet()
            else -> tryDao.getAttemptsSince(fromTime).map { it.sessionId }.toSet()
        }

        val periodAttempts: List<TryDatabaseModel> = when (period) {
            PeriodFilter.ALL_TIME -> tryDao.getAllAttempts()
            PeriodFilter.SESSION  -> {
                val lastSession = runCatching { sessionDao.getCurrentSession() }.getOrNull()
                if (lastSession != null) tryDao.getAttemptsBySessionId(lastSession.id)
                else emptyList()
            }
            else -> touchingSessionIds
                .flatMap { sid -> tryDao.getAttemptsBySessionId(sid) }
                .sortedBy { it.startTime }
        }

        val periodSessions: List<SessionDatabaseModel> = when (period) {
            PeriodFilter.ALL_TIME -> sessionDao.getAllSessions()
            PeriodFilter.SESSION  -> {
                val lastSession = runCatching { sessionDao.getCurrentSession() }.getOrNull()
                if (lastSession != null) listOf(lastSession) else emptyList()
            }
            else -> sessionDao.getAllSessions().filter { it.id in touchingSessionIds }
        }

        val periodScm = periodAttempts.mapNotNull { it.scm }.filter { it > 0 }.sum()

        // ── Ошибки периода ────────────────────────────────────────────────
        val errorsMs       = periodAttempts.map { (it.endTime - it.startTime - aimTime).toDouble() }
        val absErrorsMs    = errorsMs.map { abs(it) }
        // Absolute (deviationAll) — ЗНАКОВОЕ смещение центра масс длительностей
        // относительно aim: geomMean(durations) − aim. Согласовано с главным экраном
        // (см. MainMetricsUseCase). Это индикатор баланса: <0 при перекосе early,
        // >0 при перекосе late, ≈0 при балансе.
        // stdPct берём от модульного профиля — это спред величины промаха, осмысленно
        // как «насколько разнотипны отклонения», знак не нужен.
        val absDeviationProfile  = computeProfile(absErrorsMs)
        val durationsMs          = periodAttempts.map { (it.endTime - it.startTime).toDouble() }
        val signedAbsoluteMs     = if (durationsMs.isNotEmpty()) {
            val positiveDurations = durationsMs.filter { it > 0.0 }
            if (positiveDurations.isEmpty()) 0.0
            else exp(positiveDurations.sumOf { ln(it) } / positiveDurations.size) - aimTime.toDouble()
        } else 0.0
        val deviationAll   = DeviationProfile(
            medianMs = signedAbsoluteMs,
            stdPct   = absDeviationProfile.stdPct
        )
        val earlyErrorsMs  = errorsMs.filter { it < 0 }.map { abs(it) }
        val lateErrorsMs   = errorsMs.filter { it > 0 }
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
        // ВАЖНО (Alex 2026-07-10): ширина слота ОБЯЗАНА совпадать с шириной
        // окна «чистой тройки» в числителе. countTrueThree считает тройку в
        // точности отображения САМОЙ попытки (L1: 3.0 → окно ~100 мс, L2: 10 мс,
        // L3: 1 мс), поэтому слот берём по точности большинства попыток периода.
        // Слот 1 мс для всех уровней (вариант A) отклонён: он разрывал пару
        // числитель/знаменатель и завышал Lucky на L1 в ~100 раз.
        val majorityAccuracy = periodAttempts.groupBy { it.accuracy }
            .maxByOrNull { it.value.size }?.key ?: 3
        val slotMs         = when (majorityAccuracy) { 1 -> 100.0; 2 -> 10.0; else -> 1.0 }
        val rangeSpanMs    = abs(rangeEarlyMs) + abs(rangeLateMs)
        val outcomeSlots   = (rangeSpanMs / slotMs).toLong().coerceAtLeast(1L) + 1L
        val dispersionProb = 1.0 / outcomeSlots
        val empiricalProb  = if (periodTotal > 0) trueThreeWins.toDouble() / periodTotal else 0.0
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
        val paceOfLearning = if (periodTotal > 0) periodScm / periodTotal else 0.0

        // ── Practice Density (мин игры / день, всегда ALL_TIME) ──────────
        val practiceDensity = if (daysSinceStart > 0)
            (totalPlayTimeMs / 60_000.0) / daysSinceStart
        else 0.0

        // ── Допуск квеста (для подсветки попыток в допуске) ──────────────
        val toleranceMs = localStorage.getQuestCurrentToleranceMs().toDouble()

        // ── Global session number map (oldest session = #1) ──────────────
        val allSessionsOrdered = sessionDao.getAllSessions().sortedBy { it.startTime }
        val sessionNumberMap   = allSessionsOrdered.mapIndexed { idx, s -> s.id to (idx + 1) }.toMap()

        // ── Sessions для дерева ───────────────────────────────────────────
        val sessionsList = periodSessions.map { session ->
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
                attemptBars     = bars
            )
        }.sortedByDescending { it.startTime }

        // ── Sparklines (последние 5 сессий, хронологически) ──────────────
        // sessionsList отсортирован НОВЕЙШИЕ-ПЕРВЫМИ → последние 5 сессий это
        // take(5), не takeLast(5). Прежний takeLast(5) брал 5 СТАРЕЙШИХ сессий
        // периода — спарклайны показывали древнюю историю вместо свежего тренда.
        val sparklineSessions = sessionsList.take(5).sortedBy { it.startTime }
        val sparklinePace = sparklineSessions.map { s ->
            // SCM в bars не хранится; берём из tryDao через sessionId
            val sScm = tryDao.getAttemptsBySessionId(s.sessionId)
                .mapNotNull { it.scm }.filter { it > 0 }.sum()
            if (s.attempts == 0) 0f else (sScm / s.attempts).toFloat()
        }
        val sparklineWin = sparklineSessions.map { s ->
            if (s.attempts == 0) 0f else s.wins.toFloat() / s.attempts.toFloat()
        }

        return AnalyticsData(
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
            periodSessionsCount      = periodSessions.size.toLong(),
            earlyCount               = earlyErrorsMs.size.toLong(),
            lateCount                = lateErrorsMs.size.toLong(),
            paceOfPlayPerMin         = paceOfPlay,
            paceOfLearning           = paceOfLearning,
            practiceDensityMinPerDay     = practiceDensity,
            sparklinePaceOfLearning      = sparklinePace,
            sparklineChanceOfWin         = sparklineWin,
            sparklineSessionDates        = sparklineSessions.map { it.startTime },
            sessions                     = sessionsList,
            periodAttempts           = periodTotal,
            period                   = period,
            toleranceMs              = toleranceMs
        )
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    private fun countTrueThree(attempts: List<TryDatabaseModel>, aimTime: Int): Int {
        return attempts.count { attempt ->
            val decimals   = attempt.accuracy
            val factor     = 10.0.pow(decimals)
            val aimRounded = (aimTime / 1000.0 * factor).roundToLong()
            val durRounded = ((attempt.endTime - attempt.startTime) / 1000.0 * factor).roundToLong()
            aimRounded == durRounded
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
