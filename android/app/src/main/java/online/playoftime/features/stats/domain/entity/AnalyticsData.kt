package online.playoftime.features.stats.domain.entity

import online.playoftime.core.utils.PeriodFilter

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
    val attemptBars     : List<AttemptBarData> = emptyList(),
    /** Индикатор успешности: true = палец вверх (квест взят в сессии),
     *  false = палец вниз (сессия закончилась без победы),
     *  null = сессия ещё идёт — индикатор не показываем. */
    val thumbUp         : Boolean?       = false
)

data class AnalyticsData(
    // ── Текущий период ────────────────────────────────────────────────
    val period         : PeriodFilter = PeriodFilter.ALL_TIME,
    val periodAttempts : Long         = 0L,
    val periodScm      : Double       = 0.0,
    // Листание периода назад/вперёд клювиками под фильтром (Alex 2026-09-09):
    // periodOffset — сколько периодов назад от "сейчас" (0 = текущий); для
    // ALL_TIME всегда 0 и оба флага false (листать некуда). hasOlderPeriod/
    // hasNewerPeriod управляют enabled-состоянием клювиков.
    val periodOffset      : Int     = 0,
    val hasOlderPeriod    : Boolean = false,
    val hasNewerPeriod    : Boolean = false,
    // Только для period == SESSION — номер (внутри дня) той сессии, что сейчас
    // показана; null для остальных периодов.
    val selectedSessionNumber : Int? = null,

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
    /** Чистые тройки периода — округлённая до ячейки уровня ошибка ровно 0
     *  (допуск и пол разрешения НЕ учитываются, в отличие от [periodWins]). */
    val periodPrecise       : Long   = 0L,
    /** Границы периода (epoch, мс) по ФАКТИЧЕСКОМУ набору попыток периода —
     *  первая/последняя попытка; 0/0 если попыток нет. Нужны карточке отчёта
     *  на Analytics: страница мордочки считает по тому же набору. */
    val periodFromMs        : Long   = 0L,
    val periodToMs          : Long   = 0L,
    val periodSessionsCount : Long   = 0L,

    // ── Early / Late error counts ─────────────────────────────────────
    val earlyCount : Long = 0L,
    val lateCount  : Long = 0L,

    // ── Sparklines (последние 5 сессий) ──────────────────────────────
    val sparklinePaceOfLearning  : List<Float> = emptyList(),
    val sparklineChanceOfWin     : List<Float> = emptyList(),
    val sparklineSessionDates    : List<Long>  = emptyList(),
    // Pace of Learning chart (Alex 2026-07-30): ось X по выбранному периоду —
    // All time → месяцы, Month → дни, Today → часы, Session → попытки.
    // Значения = эффективность 0..1 в бакете; метки = готовые подписи оси X.
    // null (Alex 2026-08-14) = бакет БЕЗ данных (нет попыток в этот день/час/
    // месяц) — график должен рвать линию тут, а не тянуть её сквозь пропуск
    // или формально опускать до нуля.
    val paceChartValues          : List<Float?> = emptyList(),
    // Те же бакеты, другие ряды (Alex 2026-10-07): Hit rate в %, Lucky index.
    val paceChartHitRate         : List<Float?> = emptyList(),
    val paceChartLucky           : List<Float?> = emptyList(),
    // Mean error (Alex 2026-10-18/19): геометрическое среднее ранних и поздних
    // ошибок в каждом бакете, мс, как карточка Geometric Mean; и то же за весь
    // период — для строки под графиком.
    val paceChartEarlyMs         : List<Float?> = emptyList(),
    val paceChartLateMs          : List<Float?> = emptyList(),
    val missMeanEarlyMs          : Float? = null,
    val missMeanLateMs           : Float? = null,
    // Faces (Alex 2026-10-19): сколько весёлых и грустных рожиц главного экрана
    // в каждом бакете; и всего за период.
    val paceChartHappy           : List<Float?> = emptyList(),
    val paceChartSad             : List<Float?> = emptyList(),
    val facesHappy               : Int = 0,
    val facesSad                 : Int = 0,
    val paceChartLabels          : List<String> = emptyList(),
    // Подпись точки в подсказке по удержанию (Alex 2026-10-20: у дня — номер
    // сессии и время её начала); пусто — берётся paceChartLabels.
    val paceChartTips            : List<String> = emptyList(),

    // ── Quest tolerance (for histogram green zone) ───────────────────
    val toleranceMs : Double = 0.0,

    // ── Дерево сессий ────────────────────────────────────────────────
    val sessions : List<SessionTreeData>
)
