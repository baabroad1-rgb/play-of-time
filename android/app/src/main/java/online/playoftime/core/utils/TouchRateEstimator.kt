package online.playoftime.core.utils

/**
 * Адаптивный оценщик ячейки опроса тачскрина (Alex 2026-07-26).
 *
 * Дизеринг честен только когда его ширина = одна ячейка сетки опроса тача. Ячейка
 * зависит от частоты панели (250 Гц → 4 мс, 500 → 2 мс, 1000 → 1 мс) и может
 * меняться на лету (простой / активный / Game Mode). Поэтому вместо фиксированной
 * константы мерим ячейку в реальном времени по интервалам между семплами касаний
 * и подбираем ширину дизеринга под неё.
 *
 * Кормится ТОЛЬКО реальными тапами по кнопке секундомера (базовая частота панели,
 * релевантная тапу) — НЕ драгом из измерителя (драг завышает частоту на движении).
 * Оценка = медиана интервалов (устойчива к джиттеру, у которого бывают короткие
 * пики). Накапливается по многим тапам скользящим окном.
 *
 * Автоотключение: если ячейка ≤ [DISABLE_CELL_MS] (≈1000 Гц и выше) — сетка уже
 * разрешает до миллисекунды, ровно 3.000 достижима честно, дизеринг НЕ нужен → 0.
 */
object TouchRateEstimator {

    private const val MAX_INTERVALS      = 400
    private const val MIN_FOR_ESTIMATE   = 24
    // Ячейка ≤ этого → дизеринг не нужен (панель разрешает до 1 мс сама).
    private const val DISABLE_CELL_MS    = 1.2
    // Потолок ширины дизеринга — защита от абсурда при просадке панели до 60 Гц.
    private const val MAX_DITHER_HALF_MS = 10.0

    private val intervals = ArrayDeque<Double>()
    @Volatile private var cachedCellMs: Double = 0.0   // 0 = ещё нет оценки

    /**
     * Скормить таймстампы семплов ОДНОГО жеста (по возрастанию). Межсемпловые
     * интервалы вне [0.3..30] мс отбрасываются (нули и разрывы между жестами).
     */
    fun feed(sampleTimesMs: List<Long>) {
        if (sampleTimesMs.size < 2) return
        synchronized(this) {
            for (i in 1 until sampleTimesMs.size) {
                val d = (sampleTimesMs[i] - sampleTimesMs[i - 1]).toDouble()
                if (d in 0.3..30.0) {
                    intervals.addLast(d)
                    if (intervals.size > MAX_INTERVALS) intervals.removeFirst()
                }
            }
            if (intervals.size >= MIN_FOR_ESTIMATE) {
                val sorted = intervals.sorted()
                cachedCellMs = sorted[sorted.size / 2]   // медиана
            }
        }
    }

    fun hasEstimate(): Boolean = cachedCellMs > 0.0

    /** Текущая измеренная ячейка (мс), 0 если оценки ещё нет. */
    fun currentCellMs(): Double = cachedCellMs

    /** Измеренная ячейка либо переданный фоллбэк (последняя известная из storage). */
    private fun cellMs(fallbackCellMs: Double): Double =
        if (cachedCellMs > 0.0) cachedCellMs else fallbackCellMs

    /** Ячейка для расчёта cellIndex в компенсации дизеринга (та же, что и для шума). */
    fun cellForDither(fallbackCellMs: Double): Double = cellMs(fallbackCellMs)

    /**
     * Полуширина дизеринга для попытки: половина ячейки, либо 0 если частота уже
     * достаточна (ячейка ≤ [DISABLE_CELL_MS]). [fallbackCellMs] — последняя
     * известная ячейка из хранилища (пока не набралось семплов в этой сессии).
     */
    fun ditherHalfMs(fallbackCellMs: Double): Double {
        val cell = cellMs(fallbackCellMs)
        return if (cell <= DISABLE_CELL_MS) 0.0
        else (cell / 2.0).coerceIn(0.5, MAX_DITHER_HALF_MS)
    }
}
