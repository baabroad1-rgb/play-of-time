package ru.iflex.timemvp.use_case

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.database.TimeDatabase

/**
 * Вызывается сразу после сохранения новой попытки N.
 *
 * Логика:
 * - Берём последние 3 попытки из БД (N, N-1, N-2)
 * - Если их ровно 3 — считаем SCM для N-1 (теперь у неё оба соседа известны)
 * - Сохраняем SCM в БД
 *
 * Первая и последняя попытка в истории SCM не получают — это по дизайну.
 */
class SaveScmAfterAttemptUseCase(
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService,
    private val calculateScm: CalculateScmUseCase
) {
    suspend operator fun invoke() {
        val last3 = database.tryDao().getLastThreeAttempts()
        if (last3.size < 3) return

        // last3[0] = newest (N), last3[1] = N-1 (считаем для неё), last3[2] = N-2
        val next = last3[0]
        val curr = last3[1]
        val prev = last3[2]

        val scm = calculateScm(
            prev = prev,
            curr = curr,
            next = next,
            aimTimeMs = localStorage.getAimTime()
        )

        database.tryDao().updateScm(id = curr.id, scm = scm)
    }
}
