package online.playoftime.use_case

import online.playoftime.database.TimeDatabase
import online.playoftime.database.TryDatabaseModel
import online.playoftime.features.main.domain.SessionComparisonStats
import kotlin.math.pow
import kotlin.math.roundToLong

class GetSessionComparisonUseCase(private val database: TimeDatabase) {

    suspend operator fun invoke(currentSessionId: Int): SessionComparisonStats {
        val allSessions = database.sessionDao().getAllSessions().sortedBy { it.startTime }
        val currentIdx  = allSessions.indexOfFirst { it.id == currentSessionId }

        val currentAttempts = database.tryDao().getAttemptsBySessionId(currentSessionId)
        val prevAttempts = if (currentIdx > 0)
            database.tryDao().getAttemptsBySessionId(allSessions[currentIdx - 1].id)
        else
            emptyList()

        val hasPrevious = prevAttempts.isNotEmpty()

        val scmCurrent  = avgScm(currentAttempts)
        val scmPrev     = avgScm(prevAttempts)
        val earlyCurrent = earlyMedianMs(currentAttempts)
        val earlyPrev    = earlyMedianMs(prevAttempts)
        val lateCurrent  = lateMedianMs(currentAttempts)
        val latePrev     = lateMedianMs(prevAttempts)
        val hitCurrent   = hitRatePct(currentAttempts)
        val hitPrev      = hitRatePct(prevAttempts)

        val improvedCount = if (!hasPrevious) 0 else listOf(
            scmCurrent  > scmPrev  && scmPrev  > 0,
            earlyCurrent < earlyPrev && earlyPrev > 0,
            lateCurrent  < latePrev  && latePrev  > 0,
            hitCurrent  > hitPrev
        ).count { it }

        val bestTrio = currentAttempts
            .sortedBy { kotlin.math.abs((it.endTime - it.startTime) - AIM_MS) }
            .take(3)
            .map { formatDuration(it.endTime - it.startTime, it.accuracy) }

        val bestThisMs  = database.statisticDao().getBestAccuracyMsBySession(currentSessionId)
        val bestEverMs  = database.statisticDao().getBestAccuracyMsExcluding(currentSessionId)
        val isRecord    = bestThisMs < bestEverMs

        return SessionComparisonStats(
            hasPrevious        = hasPrevious,
            improvedCount      = improvedCount,
            scmPaceCurrent     = scmCurrent,
            scmPacePrevious    = scmPrev,
            earlyErrCurrentMs  = earlyCurrent,
            earlyErrPreviousMs = earlyPrev,
            lateErrCurrentMs   = lateCurrent,
            lateErrPreviousMs  = latePrev,
            hitRateCurrent     = hitCurrent,
            hitRatePrevious    = hitPrev,
            bestTrio           = bestTrio,
            isPersonalRecord   = isRecord,
            personalRecordMs   = bestThisMs,
        )
    }

    private fun avgScm(attempts: List<TryDatabaseModel>): Double {
        if (attempts.isEmpty()) return 0.0
        return attempts.sumOf { it.scm ?: 0.0 } / attempts.size
    }

    private fun earlyMedianMs(attempts: List<TryDatabaseModel>): Double {
        val errors = attempts
            .mapNotNull { ((it.endTime - it.startTime) - AIM_MS).takeIf { e -> e < 0 }?.let { e -> (-e).toDouble() } }
            .sorted()
        return if (errors.isEmpty()) 0.0 else errors[errors.size / 2]
    }

    private fun lateMedianMs(attempts: List<TryDatabaseModel>): Double {
        val errors = attempts
            .mapNotNull { ((it.endTime - it.startTime) - AIM_MS).takeIf { e -> e > 0 }?.toDouble() }
            .sorted()
        return if (errors.isEmpty()) 0.0 else errors[errors.size / 2]
    }

    private fun hitRatePct(attempts: List<TryDatabaseModel>): Int {
        if (attempts.isEmpty()) return 0
        val wins = attempts.count { isWin(it) }
        return wins * 100 / attempts.size
    }

    private fun isWin(attempt: TryDatabaseModel): Boolean {
        val factor = 10.0.pow(attempt.accuracy.toDouble())
        val dur = online.playoftime.core.utils.roundedCells(attempt.endTime - attempt.startTime, attempt.accuracy)
        val aim = online.playoftime.core.utils.roundedCells(AIM_MS, attempt.accuracy)
        return dur == aim
    }

    private fun formatDuration(durationMs: Long, accuracyOrdinal: Int): String {
        val factor = 10.0.pow(accuracyOrdinal.toDouble())
        val rounded = online.playoftime.core.utils.roundedCells(durationMs, accuracyOrdinal) / factor
        return when (accuracyOrdinal) {
            0    -> "%.0f".format(rounded)
            1    -> "%.1f".format(rounded)
            2    -> "%.2f".format(rounded)
            else -> "%.3f".format(rounded)
        }
    }

    companion object {
        private const val AIM_MS = 3000L
    }
}
