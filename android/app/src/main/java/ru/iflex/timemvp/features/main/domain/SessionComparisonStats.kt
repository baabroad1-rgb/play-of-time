package ru.iflex.timemvp.features.main.domain

data class SessionComparisonStats(
    val hasPrevious: Boolean,
    val improvedCount: Int,        // out of 4 metrics
    val scmPaceCurrent: Double,
    val scmPacePrevious: Double,
    val earlyErrCurrentMs: Double,
    val earlyErrPreviousMs: Double,
    val lateErrCurrentMs: Double,
    val lateErrPreviousMs: Double,
    val hitRateCurrent: Int,
    val hitRatePrevious: Int,
    val bestTrio: List<String>,
    val isPersonalRecord: Boolean,
    val personalRecordMs: Long,
) {
    val overallBetter: Boolean get() = improvedCount >= 3
    val overallWorse:  Boolean get() = improvedCount <= 1
}
