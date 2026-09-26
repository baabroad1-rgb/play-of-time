package online.playoftime.use_case

import online.playoftime.database.TryDatabaseModel
import kotlin.math.abs
import kotlin.math.pow

/**
 * Вычисляет Serenity Coin Mint для попытки [curr] на основе соседних попыток.
 *
 * Формула (из measures.docx / parametr_table.docx):
 * SCM_i = 99 / (3*t_i + t_{i-1} + t_{i+1} + |T_i−T_{i+1}| + |T_i−T_{i-1}| − |T_{i+1}−T_{i-1}|/3 + 1)
 *
 * Где:
 *   T_i = (endTime - startTime)  — результат попытки в МИЛЛИСЕКУНДАХ
 *   t_i = |T_i - aimTime|        — абсолютная ошибка в МИЛЛИСЕКУНДАХ
 *
 * При нулевой ошибке SCM = 99 (максимум).
 * Типичный диапазон: ~0.05–2.0 SC/попытку (ошибка 30–200 мс).
 *
 * Level-фактор (Alex 2026-07-05): результат умножается на `10^(accuracy − 3)`:
 *   L1 (deci,  accuracy=1) → ×1/100
 *   L2 (centi, accuracy=2) → ×1/10
 *   L3 (milli, accuracy=3) → ×1
 * Precision growth 100→10→1 мс между уровнями делает L3 в 100× сложнее L1;
 * коин теперь стоит одинаковую «работу точности» вне зависимости от уровня.
 * Читаем `curr.accuracy` (per-attempt) — исторические попытки при recalc-миграции
 * получают правильный множитель по своему уровню записи, а не текущему уровню игрока.
 */
class CalculateScmUseCase {
    operator fun invoke(
        prev: TryDatabaseModel,
        curr: TryDatabaseModel,
        next: TryDatabaseModel,
        aimTimeMs: Int
    ): Double {
        val aimMs = aimTimeMs.toDouble()

        // Длительности в миллисекундах (как в parametr_table.docx)
        val tAbsPrev = (prev.endTime - prev.startTime).toDouble()
        val tAbsCurr = (curr.endTime - curr.startTime).toDouble()
        val tAbsNext = (next.endTime - next.startTime).toDouble()

        // Ошибки в миллисекундах (абсолютные)
        val tPrev = abs(tAbsPrev - aimMs)
        val tCurr = abs(tAbsCurr - aimMs)
        val tNext = abs(tAbsNext - aimMs)

        // SCM_i = 99 / denominator
        // denominator = (3·tᵢ + tᵢ₋₁ + tᵢ₊₁) + |Tᵢ−Tᵢ₊₁| + |Tᵢ−Tᵢ₋₁| − |Tᵢ₊₁−Tᵢ₋₁|/3 + 1
        val denominator = 3.0 * tCurr +
                tPrev +
                tNext +
                abs(tAbsCurr - tAbsNext) +
                abs(tAbsCurr - tAbsPrev) -
                abs(tAbsNext - tAbsPrev) / 3.0 +
                1.0

        // Level-факторный множитель по уровню точности попытки.
        // Accuracy ordinal: SECONDS=0, DECISECONDS=1, CENTISECONDS=2, MILLISECONDS=3.
        val levelFactor = 10.0.pow((curr.accuracy - 3).toDouble())

        return 99.0 / denominator * levelFactor
    }
}
