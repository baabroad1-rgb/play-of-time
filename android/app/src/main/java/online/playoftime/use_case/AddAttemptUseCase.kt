package online.playoftime.use_case

import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.Hand
import online.playoftime.database.TimeDatabase
import online.playoftime.database.TryDatabaseModel

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