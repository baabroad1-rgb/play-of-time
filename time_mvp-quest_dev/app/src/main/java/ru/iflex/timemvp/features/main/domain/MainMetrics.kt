package ru.iflex.timemvp.features.main.domain

/**
 * Бакет гистограммы частот (Distrib.) — поддерживает переменную ширину.
 * @param centerMs центр бакета в мс (отрицательный = early, положительный = late)
 * @param startMs нижняя граница бакета в мс
 * @param endMs верхняя граница бакета в мс
 * @param count количество попыток в этом бакете
 * @param winsCount кол-во побед (TRUE_THREE) в этом бакете
 */
data class DistribBucket(
    val centerMs: Double,
    val count: Int,
    val winsCount: Int = 0,
    val startMs: Double = centerMs,
    val endMs: Double = centerMs
)

/**
 * Все метрики главного экрана за выбранный период.
 */
data class MainMetrics(
    // Balance
    val redCount: Int = 0,
    val blueCount: Int = 0,

    // True Three Hit Rate
    val trueThreeCount: Int = 0,
    val totalAttempts: Int = 0,

    // Geometric Mean (в мс)
    val geomMeanEarlyMs: Double = 0.0,   // среднее геометрическое красных ошибок
    val geomMeanLateMs: Double = 0.0,    // среднее геометрическое синих ошибок
    val geomMeanAbsoluteOffsetMs: Double = 0.0, // geomMean(T_i) - aimTime

    // Шкала (97-й перцентиль абсолютных ошибок в мс)
    val scaleMaxMs: Double = 200.0,

    // Гистограмма
    val distribBuckets: List<DistribBucket> = emptyList()
) {
    val trueThreeHitRate: Double
        get() = if (totalAttempts > 0) trueThreeCount.toDouble() / totalAttempts else 0.0

    val balanceSide: BalanceSide
        get() = when {
            redCount > blueCount -> BalanceSide.RED
            blueCount > redCount -> BalanceSide.BLUE
            else -> BalanceSide.NEUTRAL
        }
}

enum class BalanceSide { RED, NEUTRAL, BLUE }
