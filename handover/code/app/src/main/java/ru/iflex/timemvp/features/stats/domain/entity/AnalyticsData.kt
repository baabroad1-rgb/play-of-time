package ru.iflex.timemvp.features.stats.domain.entity

import ru.iflex.timemvp.core.utils.PeriodFilter

data class TopDeviation(val errorMs: Double, val count: Int)

data class DeviationProfile(
    val medianMs: Double,
    val stdPct: Double
)

/** Одна попытка для отображения в дереве сессий */
data class AttemptBarData(
    val number     : Int,
    val errorMs    : Double,   // знак: < 0 = early, > 0 = late, ≈ 0 = win
    val isWin      : Boolean,  // true = попадание в окно точности квеста
    val durationMs : Long  = 0L,  // фактическая длительность попытки в мс
    val accuracy   : Int   = 2    // кол-во знаков после запятой (2=центисек, 3=мс)
)

data class SessionTreeData(
    val sessionId       : Int,
    val sessionNumber   : Int,
    val startTime       : Long,
    val endTime         : Long,
    /** Реальное время (мс), которое пользователь провёл на MainScreen в этой сессии.
     *  Тикает только пока экран в состоянии RESUMED. Используется для Play Time вместо
     *  wall-clock (endTime − startTime). */
    val activeTimeMs    : Long           = 0L,
    val attempts        : Int,
    val wins            : Int,
    val isQuestComplete : Boolean        = false,
    val questLevelId    : Int            = 0,
    val questTaskId     : Int            = 0,
    val attemptBars     : List<AttemptBarData> = emptyList()
)

data class AnalyticsData(
    // ── Текущий период ────────────────────────────────────────────────
    val period         : PeriodFilter = PeriodFilter.ALL_TIME,
    val periodAttempts : Long         = 0L,
    val periodScm      : Double       = 0.0,

    // ── Сводка (всегда ALL_TIME) ──────────────────────────────────────
    val totalScm                : Double,
    val sessionsCount           : Long,
    val totalPlayTimeMs         : Long,
    val totalAttempts           : Long,
    val totalWins               : Long,
    val daysSinceStart          : Long,
    val startDateStr            : String,

    // ── Deviation Profile ─────────────────────────────────────────────
    val deviationAll   : DeviationProfile,
    val deviationEarly : DeviationProfile,
    val deviationLate  : DeviationProfile,

    // ── Range of Errors (97th percentile) ────────────────────────────
    val rangeEarlyMs : Double,   // P3 of signed errors (negative = early)
    val rangeLateMs  : Double,   // P97 of signed errors (positive = late)

    // ── True Three Hit Rate ───────────────────────────────────────────
    val trueThreeHitRatePct : Double,
    val attemptsPerWin      : Double,
    val luckyIndex          : Double,

    // ── Top Deviations ────────────────────────────────────────────────
    val topEarly : List<TopDeviation>,
    val topLate  : List<TopDeviation>,

    // ── Pace ──────────────────────────────────────────────────────────
    val paceOfPlayPerMin     : Double,
    val paceOfLearning       : Double,
    val practiceDensityMinPerDay : Double,

    // ── Period-specific summary (change with filter) ──────────────────
    val periodPlayTimeMs    : Long   = 0L,
    val periodWins          : Long   = 0L,
    val periodSessionsCount : Long   = 0L,

    // ── Early / Late error counts ─────────────────────────────────────
    val earlyCount : Long = 0L,
    val lateCount  : Long = 0L,

    // ── Sparklines (последние 5 сессий) ──────────────────────────────
    val sparklinePaceOfLearning  : List<Float> = emptyList(),
    val sparklineChanceOfWin     : List<Float> = emptyList(),
    val sparklineSessionDates    : List<Long>  = emptyList(),

    // ── Quest tolerance (for histogram green zone) ───────────────────
    val toleranceMs : Double = 0.0,

    // ── Дерево сессий ────────────────────────────────────────────────
    val sessions : List<SessionTreeData>
)
