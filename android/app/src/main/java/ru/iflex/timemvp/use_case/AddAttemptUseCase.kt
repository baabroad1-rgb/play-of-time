package ru.iflex.timemvp.use_case

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.Hand
import ru.iflex.timemvp.database.TimeDatabase
import ru.iflex.timemvp.database.TryDatabaseModel

class AddAttemptUseCase(
    private val database: TimeDatabase,
    private val localStorageService: LocalStorageService
) {
    suspend operator fun invoke(
        sessionId: Int,
        startTime: Long,
        endTime: Long,
        deflectionMs: Long?,
        hand: Hand = localStorageService.getSelectedHand(),
        /** Допуск ±t (мс), активный в момент попытки. По умолчанию 0 (baseline). */
        toleranceMs: Int = 0
    ) {
        val value = TryDatabaseModel(
            id = 0,
            sessionId = sessionId,
            startTime = startTime,
            endTime = endTime,
            deflection = deflectionMs,
            accuracy = localStorageService.getAccuracy().ordinal,
            hand = hand.value,
            toleranceMs = toleranceMs
        )

        database.tryDao().insert(value)
    }
}