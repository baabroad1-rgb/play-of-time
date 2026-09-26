package ru.iflex.timemvp.use_case

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.database.TimeDatabase

/**
 * Одноразовый пересчёт SCM для всех исторических попыток в БД.
 *
 * История версий:
 *   v2 — ошибочно считала в секундах (~65-90 SC/попытку).
 *   v3 — исправила в миллисекунды (~0.05-2.0 SC/попытку) per parametr_table.docx.
 *   v4 — 2026-07-05: добавлен level-factor `10^(accuracy − 3)`.
 *        L1 попытки теперь получают ×1/100, L2 — ×1/10, L3 — ×1. Цель — выровнять
 *        «работу точности» между уровнями: 1 SC на L3 = 10 SC на L2 = 100 SC на L1.
 *
 * Вызывается один раз при первом запуске приложения после обновления.
 * Флаг: SharedPreferences ключ "scm_recalculated_v4".
 */
class RecalculateScmUseCase(
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService,
    private val calculateScm: CalculateScmUseCase
) {
    suspend operator fun invoke() {
        if (localStorage.isScmRecalculated()) return

        val all = database.tryDao().getAllAttempts()
            .sortedBy { it.startTime }

        if (all.size < 3) {
            localStorage.markScmRecalculated()
            return
        }

        val aimTimeMs = localStorage.getAimTime()

        // Пересчитываем SCM для каждой попытки кроме первой и последней
        for (i in 1 until all.size - 1) {
            val scm = calculateScm(
                prev = all[i - 1],
                curr = all[i],
                next = all[i + 1],
                aimTimeMs = aimTimeMs
            )
            database.tryDao().updateScm(id = all[i].id, scm = scm)
        }

        // Первая и последняя попытки SCM не получают (нет полного окна)
        database.tryDao().updateScm(id = all.first().id, scm = 0.0)
        database.tryDao().updateScm(id = all.last().id, scm = 0.0)

        localStorage.markScmRecalculated()
    }
}
