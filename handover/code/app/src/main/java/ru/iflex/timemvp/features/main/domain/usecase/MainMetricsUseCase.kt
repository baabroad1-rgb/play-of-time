package ru.iflex.timemvp.features.main.domain.usecase

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.database.TimeDatabase
import ru.iflex.timemvp.database.TryDatabaseModel
import ru.iflex.timemvp.features.main.domain.DistribBucket
import ru.iflex.timemvp.features.main.domain.MainMetrics
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Загружает все метрики главного экрана для заданного периода.
 * Периоды: SESSION (по sessionId), TODAY (с начала суток), ALL_TIME (всё).
 */
class MainMetricsUseCase(
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService
) {
    suspend operator fun invoke(
        filter: PeriodFilter,
        currentSessionId: Int?
    ): MainMetrics {
        val aimTime = localStorage.getAimTime()

        // --- Загрузка попыток за период ---
        // Согласовано с GetLogAttemptsUseCase: для TODAY/MONTH берём ВСЕ попытки
        // сессий, которые «зацепили» период (а не только attempt'ы с startTime
        // >= periodStart). Иначе Σtries в логе ≠ количество в гистограмме,
        // когда сессия началась до полуночи и продолжилась после.
        val attempts: List<TryDatabaseModel> = when (filter) {
            PeriodFilter.SESSION -> {
                if (currentSessionId == null) emptyList()
                else database.tryDao().getAttemptsBySessionId(currentSessionId)
            }
            PeriodFilter.TODAY    -> attemptsForSessionsTouching(filter.startTimeMs())
            PeriodFilter.MONTH    -> attemptsForSessionsTouching(filter.startTimeMs())
            PeriodFilter.ALL_TIME -> database.tryDao().getAllAttempts()
        }

        if (attempts.isEmpty()) return MainMetrics()

        // --- Ошибки (знаковые, мс) — сырые для баланса и геом.среднего ---
        val errors = attempts.map { (it.endTime - it.startTime) - aimTime.toLong() }

        // --- Balance ---
        val redCount = errors.count { it < 0 }
        val blueCount = errors.count { it > 0 }

        // --- True Three Hit Rate ---
        val accuracyOrdinal = localStorage.getAccuracy().ordinal

        // --- Округлённые ошибки для гистограммы ---
        // True Three (3.0 vs 3.0) даёт ошибку 0 → бар ровно по центру
        val roundedErrors = attempts.map { attempt ->
            val roundedResult  = roundToAccuracy((attempt.endTime - attempt.startTime) / 1000.0, accuracyOrdinal) * 1000.0
            val roundedAimTime = roundToAccuracy(aimTime / 1000.0, accuracyOrdinal) * 1000.0
            (roundedResult - roundedAimTime).toLong()
        }
        val trueThreeCount = attempts.count { attempt ->
            val result = attempt.endTime - attempt.startTime
            roundToAccuracy(result / 1000.0, accuracyOrdinal) ==
                    roundToAccuracy(aimTime / 1000.0, accuracyOrdinal)
        }

        // --- Geometric Mean ---
        // Early/Late — модульное геом среднее по сырым ошибкам, согласовано с
        // AnalyticsUseCase (deviationEarly.medianMs / deviationLate.medianMs).
        // Absolute — ЗНАКОВОЕ смещение центра масс длительностей относительно aim:
        //   geomMean(durations) − aim.
        // Это индикатор баланса (bias): ≈0 при балансе, <0 при перекосе early,
        // >0 при перекосе late. Белая пунктирная линия "Absolute" на гистограмме
        // показывает в какую сторону смещён игрок.
        // СОЗНАТЕЛЬНО расходится с deviationAll.medianMs на аналитике, которое
        // считается как geomMean(|errors|) — там это статистика модуля промаха,
        // тут на главном — индикатор баланса.
        val earlyErrorsRaw = errors.filter { it < 0 }.map { abs(it).toDouble() }
        val lateErrorsRaw  = errors.filter { it > 0 }.map { it.toDouble() }
        val durationsMs    = attempts.map { (it.endTime - it.startTime).toDouble() }
        val aimMsDouble    = aimTime.toDouble()

        val geomMeanEarly     = geometricMean(earlyErrorsRaw)
        val geomMeanLate      = geometricMean(lateErrorsRaw)
        val geomMeanAbsOffset = if (durationsMs.isNotEmpty())
            geometricMean(durationsMs) - aimMsDouble
        else 0.0

        // Шкала вычисляется ПОД сетку гистограммы (21 столбик):
        //   step    = max(tick, ceil(worst / 10 / tick) · tick)
        //   range   = ±(10 · step + step/2)
        // Это держит крайний столбик ровно на «худшей» ошибке и не оставляет
        // лишнего пустого поля.
        val tickMsForScale = when (localStorage.getAccuracy()) {
            ru.iflex.timemvp.core.utils.Accuracy.SECONDS      -> 1000.0
            ru.iflex.timemvp.core.utils.Accuracy.DECISECONDS  ->  100.0
            ru.iflex.timemvp.core.utils.Accuracy.CENTISECONDS ->   10.0
            ru.iflex.timemvp.core.utils.Accuracy.MILLISECONDS ->    1.0
        }
        val absRoundedErrors = roundedErrors.map { abs(it).toDouble() }
        val worstAbs = absRoundedErrors.maxOrNull() ?: 0.0
        // Совпадает с buildDistrib: ширина бакета = одна клетка дисплея,
        // halfBuckets растёт под худшую ошибку, минимум 10.
        val halfBucketsForScale = maxOf(
            10,
            kotlin.math.ceil(worstAbs / tickMsForScale).toInt()
        )
        val scaleMax = halfBucketsForScale * tickMsForScale + tickMsForScale / 2.0

        // --- Distrib (гистограмма по округлённым ошибкам) ---
        // Победа = округлённая ошибка равна 0
        val errorsWithWin = roundedErrors.map { it.toDouble() to (it == 0L) }
        val tickMs = when (localStorage.getAccuracy()) {
            ru.iflex.timemvp.core.utils.Accuracy.SECONDS      -> 1000.0
            ru.iflex.timemvp.core.utils.Accuracy.DECISECONDS  ->  100.0
            ru.iflex.timemvp.core.utils.Accuracy.CENTISECONDS ->   10.0
            ru.iflex.timemvp.core.utils.Accuracy.MILLISECONDS ->    1.0
        }
        val buckets = buildDistrib(errorsWithWin, scaleMax, tickMs)

        return MainMetrics(
            redCount = redCount,
            blueCount = blueCount,
            trueThreeCount = trueThreeCount,
            totalAttempts = attempts.size,
            geomMeanEarlyMs = geomMeanEarly,
            geomMeanLateMs = geomMeanLate,
            geomMeanAbsoluteOffsetMs = geomMeanAbsOffset,
            scaleMaxMs = scaleMax,
            distribBuckets = buckets
        )
    }

    // --- Helpers ---

    /**
     * Возвращает попытки всех сессий, у которых есть хотя бы одна попытка
     * с `startTime >= fromTime`. Совпадает с поведением GetLogAttemptsUseCase
     * — чтобы log и histogram считали один и тот же набор данных.
     */
    private suspend fun attemptsForSessionsTouching(fromTime: Long): List<TryDatabaseModel> {
        val periodAttempts = database.tryDao().getAttemptsSince(fromTime)
        if (periodAttempts.isEmpty()) return emptyList()
        val touchingSessionIds = periodAttempts.map { it.sessionId }.toSet()
        return touchingSessionIds
            .flatMap { sid -> database.tryDao().getAttemptsBySessionId(sid) }
            .sortedBy { it.startTime }
    }

    private fun geometricMean(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val validValues = values.filter { it > 0 }
        if (validValues.isEmpty()) return 0.0
        val lnSum = validValues.sumOf { ln(it) }
        return exp(lnSum / validValues.size)
    }

    private fun roundToAccuracy(value: Double, accuracyOrdinal: Int): Double {
        val factor = when (accuracyOrdinal) {
            0 -> 1.0      // SECONDS
            1 -> 10.0     // DECISECONDS
            2 -> 100.0    // CENTISECONDS
            else -> 1000.0 // MILLISECONDS
        }
        return kotlin.math.round(value * factor) / factor
    }

    /**
     * Симметричная гистограмма с фиксированной шириной бакетов (правка 2026-06-27):
     *
     * Принципы:
     *   • Бакеты ОДНОЙ ширины — слева и справа от 0 столбики выглядят одинаково.
     *   • Ширина бакета ВСЕГДА = одна клетка уровня дисплея (tickMs); правило
     *     Скотта отменено 2026-06-27 (один уникальный результат = один столбик).
     *   • Шкала симметрична: расширяем scaleMax до ближайшего кратного step,
     *     чтобы 0 оказался ровно по центру нечётного бакета.
     *   • Количество бакетов = 2·adjScaleMax/step (нечётное → центральный
     *     бакет покрывает [-step/2, +step/2], т.е. «истинная тройка»).
     *   • Высота бакета — сырой count (без сглаживания, как просил пользователь).
     */
    private fun buildDistrib(
        errorsWithWin: List<Pair<Double, Boolean>>,
        scaleMax: Double,
        tickMs: Double
    ): List<DistribBucket> {
        if (errorsWithWin.isEmpty() || tickMs <= 0.0) return emptyList()

        // Правило (Alex, итог 2026-06-27):
        //   • Один уникальный результат ошибки = РОВНО ОДИН столбик.
        //   • Ширина бакета ВСЕГДА = одна клетка уровня дисплея (1ms/10ms/100ms).
        //   • Количество бакетов = 21 минимум, и растёт под худшую ошибку,
        //     чтобы НИ ОДИН промах не «склеивался» с соседним значением.
        val step = tickMs
        val worstAbs = errorsWithWin.maxOf { kotlin.math.abs(it.first) }
        val halfBuckets = maxOf(
            10,
            kotlin.math.ceil(worstAbs / tickMs).toInt()
        )
        val bucketCount = 2 * halfBuckets + 1
        val adjScaleMax = halfBuckets * step + step / 2.0

        val buckets = MutableList(bucketCount) { i ->
            val center = (i - halfBuckets).toDouble() * step
            DistribBucket(
                centerMs  = center,
                startMs   = center - step / 2.0,
                endMs     = center + step / 2.0,
                count     = 0,
                winsCount = 0
            )
        }

        for ((error, isWin) in errorsWithWin) {
            if (error < -adjScaleMax || error >= adjScaleMax) continue
            // (error + step/2) сдвигает шкалу так, что граница левого бакета
            // ровно на 0 → floor даёт относительный индекс от центра.
            val relIdx = kotlin.math.floor((error + step / 2.0) / step).toInt()
            val idx = (halfBuckets + relIdx).coerceIn(0, bucketCount - 1)
            buckets[idx] = buckets[idx].copy(
                count     = buckets[idx].count + 1,
                winsCount = buckets[idx].winsCount + if (isWin) 1 else 0
            )
        }

        return buckets
    }
}
