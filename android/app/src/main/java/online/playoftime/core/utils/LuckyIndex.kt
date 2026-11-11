package online.playoftime.core.utils

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Lucky Index (Alex 2026-10-24) — одна формула для всего приложения: Analytics,
 * дерево сессий, график, итог квеста. Та же — в вебе (luckyIndexOf) и в
 * серверном лидерборде (lucky_index() в webapp/api/_lib.php).
 *
 *   теоретическая вероятность зелёной попытки = зелёное окно ÷ весь диапазон:
 *     зелёное окно = 2·⌊допуск / ячейка⌋ + 1 ячеек уровня попытки (допуск —
 *       индульгенция квеста в момент попытки, без неё 0: на L3 при ±0.001 —
 *       3 мс, без допуска — 1 мс);
 *     весь диапазон = ⌊разброс / ячейка⌋ + 1 ячеек, разброс = |P3| + |P97|
 *       знаковых ошибок попыток ТОГО ЖЕ уровня в наборе (по уровню отдельно:
 *       иначе попытки L3 мерились бы разбросом L1);
 *   эмпирическая вероятность = зелёные ÷ все попытки;
 *   Lucky = эмпирическая ÷ средняя теоретическая. 0 — зелёных нет.
 *
 * Раньше числитель был только «ровно 3.000» на сетке 1 мс на любом уровне:
 * на L1/L2 Lucky почти всегда был 0, а изредка взлетал до 40+, и смягчение
 * требований не учитывалось.
 */
data class LuckyAttempt(
    val durationMs : Long,
    val accuracy   : Int,   // знаков после запятой: 1 — десятые, 2 — сотые, 3 — тысячные
    val toleranceMs: Int,
    val aimMs      : Int = 3000,
    /** Сессия и время попытки — для зелёных близнецов (docs/LUCKY_INDEX_TWINS.md).
     *  −1 — неизвестно: тогда близнецы не считаются, Lucky как раньше. */
    val sessionId  : Int = -1,
    val startMs    : Long = 0L
) {
    val errorMs: Double get() = (durationMs - aimMs).toDouble()
    private val cellMs: Double get() = 10.0.pow(3 - accuracy)

    /** Зелёная: ошибка, округлённая до ячейки уровня, в пределах допуска — как countTrueThree. */
    val isGreen: Boolean get() {
        val factor = 10.0.pow(accuracy)
        val durRounded = roundedCells(durationMs, accuracy)
        val aimRounded = roundedCells(aimMs.toLong(), accuracy)
        val roundedErrMs = (durRounded - aimRounded) * (1000.0 / factor)
        return abs(roundedErrMs) <= maxOf(toleranceMs, resolutionFloorMs(accuracy)) + 0.5
    }

    /** Знаковый промах в клетках уровня (как показан). */
    val errCells: Long get() = roundedCells(durationMs, accuracy) - roundedCells(aimMs.toLong(), accuracy)

    /** Клеток в зелёном окне: 2·⌊допуск/клетка⌋ + 1. */
    val windowCells: Int get() = (2 * floor(maxOf(toleranceMs, resolutionFloorMs(accuracy)) / cellMs) + 1).toInt()

    /** Шанс оказаться зелёной случайно при разбросе [spanMs] уровня. */
    fun chance(spanMs: Double): Double {
        val c = cellMs
        val window = 2 * floor(maxOf(toleranceMs, resolutionFloorMs(accuracy)) / c) + 1
        val all = floor(spanMs / c) + 1
        return (window / all).coerceAtMost(1.0)
    }
}

/** Разброс |P3| + |P97| знаковых ошибок отдельно для каждого уровня набора. */
fun luckySpans(attempts: List<LuckyAttempt>): Map<Int, Double> =
    attempts.groupBy { it.accuracy }.mapValues { (_, list) ->
        val e = list.map { it.errorMs }.sorted()
        val lo = e[(e.size * 0.03).toInt().coerceIn(0, e.size - 1)]
        val hi = e[(e.size * 0.97).toInt().coerceIn(0, e.size - 1)]
        abs(lo) + abs(hi)
    }

/** Lucky Index набора попыток; 0.0 — пусто или ни одной зелёной. */
fun luckyIndex(attempts: List<LuckyAttempt>): Double {
    if (attempts.isEmpty()) return 0.0
    val greens = attempts.count { it.isGreen }
    if (greens == 0) return 0.0
    val spans = luckySpans(attempts)
    val p = attempts.map { it.chance(spans.getValue(it.accuracy)) }
    val expectedGreens = p.sum()
    // Зелёные близнецы (Alex 11.11.2026, docs/LUCKY_INDEX_TWINS.md): зелёная, и
    // следующая в той же сессии — зелёная с тем же модулем промаха (одинаковая или
    // зеркальная). Отдельное событие сверху G; ожидание — pᵢpᵢ₊₁·(2w−1)/w².
    var twins = 0
    var expectedTwins = 0.0
    val ordered = attempts.indices.filter { attempts[it].sessionId >= 0 }.sortedBy { attempts[it].startMs }
    for (j in 1 until ordered.size) {
        val i0 = ordered[j - 1]; val i1 = ordered[j]
        val a = attempts[i0]; val b = attempts[i1]
        if (a.sessionId != b.sessionId) continue
        if (a.isGreen && b.isGreen && abs(a.errCells) == abs(b.errCells)) twins++
        if (a.accuracy == b.accuracy) {
            val w = minOf(a.windowCells, b.windowCells).toDouble()
            expectedTwins += p[i0] * p[i1] * (2 * w - 1) / (a.windowCells.toDouble() * b.windowCells)
        }
    }
    val denom = expectedGreens + expectedTwins
    return if (denom > 0) (greens + twins) / denom else 0.0
}
