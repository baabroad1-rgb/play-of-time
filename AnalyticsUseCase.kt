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
        val periodAttempts: List<TryDatabaseModel> = when (period) {
            PeriodFilter.ALL_TIME -> tryDao.getAllAttempts()
            PeriodFilter.SESSION  -> {
                val lastSession = runCatching { sessionDao.getCurrentSession() }.getOrNull()
                if (lastSession != null) tryDao.getAttemptsBySessionId(lastSession.id)
                else emptyList()
            }
            else -> tryDao.getAttemptsSince(fromTime)
        }

        val periodSessions: List<SessionDatabaseModel> = when (period) {
            PeriodFilter.ALL_TIME -> sessionDao.getAllSessions()
            PeriodFilter.SESSION  -> {
                val lastSession = runCatching { sessionDao.getCurrentSession() }.getOrNull()
                if (lastSession != null) listOf(lastSession) else emptyList()
            }
            else -> sessionDao.getSessionsSince(fromTime)
        }

        val periodScm = periodAttempts.mapNotNull { it.scm }.filter { it > 0 }.sum()

        // ── Ошибки периода ────────────────────────────────────────────────
        val errorsMs       = periodAttempts.map { (it.endTime - it.startTime - aimTime).toDouble() }
        val absErrorsMs    = errorsMs.map { abs(it) }
        val deviationAll   = computeProfile(absErrorsMs)
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

        // ── Top Deviations (scoped to current level, rounded by level) ───
        val currentLevelId = localStorage.getCurrentLevelId()
        // Filter attempts to current level only (accuracy ordinal matches levelId)
        val levelAttempts = periodAttempts.filter { it.accuracy == currentLevelId }
        val levelErrorsMs = levelAttempts.map { (it.endTime - it.startTime - aimTime).toDouble() }
        val levelEarlyMs  = levelErrorsMs.filter { it < 0 }.map { abs(it) }
        val levelLateMs   = levelErrorsMs.filter { it > 0 }
        // Rounding: Level 1 (deci) = 100ms, Level 2 (centi) = 10ms, Level 3 (milli) = 1ms
        val roundingMs = when (currentLevelId) {
            1    -> 100.0
            2    -> 10.0
            else -> 1.0
        }
        val topEarly = topDeviations(levelEarlyMs, negated = true,  roundingMs = roundingMs)
        val topLate  = topDeviations(levelLateMs,  negated = false, roundingMs = roundingMs)

        // ── Pace of Play (попыток/мин за период) ─────────────────────────
        val periodPlayMs  = periodSessions.sumOf { s ->
            if (s.endTime > s.startTime) s.endTime - s.startTime else 0L
        }
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

        // ── Sessions для дерева ───────────────────────────────────────────
        val sessionsList = periodSessions.map { session ->
            val sAttempts = tryDao.getAttemptsBySessionId(session.id)
            val wins      = countTrueThree(sAttempts, aimTime)

            // Бары попыток: green = true three OR в допуске квеста
            val bars = sAttempts.mapIndexed { idx, att ->
                val errMs       = (att.endTime - att.startTime - aimTime).toDouble()
                val isTrueThree = countTrueThree(listOf(att), aimTime) > 0
                val inTolerance = toleranceMs > 0 && abs(errMs) <= toleranceMs
                AttemptBarData(number = idx + 1, errorMs = errMs, isWin = isTrueThree || inTolerance)
            }

            SessionTreeData(
                sessionId       = session.id,
                startTime       = session.startTime,
                endTime         = session.endTime,
                attempts        = sAttempts.size,
                wins            = wins,
                isQuestComplete = session.isQuestComplete,
                questLevelId    = session.questLevelId,
                questTaskId     = session.questTaskId,
                attemptBars     = bars
            )
        }.sortedByDescending { it.startTime }

        // ── Sparklines (последние 5 сессий, хронологически) ──────────────
        val sparklineSessions = sessionsList.takeLast(5).sortedBy { it.startTime }
        val sparklinePace = sparklineSessions.map { s ->
            val sScm = s.attemptBars.sumOf { 0.0 }.let {
                // SCM уже в bars нет; берём из tryDao через sessionId
                tryDao.getAttemptsBySessionId(s.sessionId)
                    .mapNotNull { it.scm }.filter { it > 0 }.sum()
            }
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
            topEarly                 = topEarly,
            topLate                  = topLate,
            paceOfPlayPerMin         = paceOfPlay,
            paceOfLearning           = paceOfLearning,
            practiceDensityMinPerDay     = practiceDensity,
            sparklinePaceOfLearning      = sparklinePace,
            sparklineChanceOfWin         = sparklineWin,
            sessions                     = sessionsList,
            periodAttempts           = periodTotal,
            period                   = period
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
        if (values.isEmpty()) return DeviationProfile(0.0, 0.0)
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 0)
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        else sorted[sorted.size / 2]
        if (median == 0.0) return DeviationProfile(0.0, 0.0)
        val variance = values.map { (it - median).pow(2) }.average()
        val stdPct   = sqrt(variance) / abs(median) * 100.0
        return DeviationProfile(medianMs = median, stdPct = stdPct)
    }

    private fun topDeviations(valuesMs: List<Double>, negated: Boolean, roundingMs: Double = 1.0): List<TopDeviation> {
        if (valuesMs.isEmpty()) return emptyList()
        return valuesMs
            .map { (it / roundingMs).roundToLong() * roundingMs }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { (errMs, count) ->
                TopDeviation(errorMs = if (negated) -errMs else errMs, count = count)
            }
    }
}
