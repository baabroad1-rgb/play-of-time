package online.playoftime.use_case

import online.playoftime.database.SessionDatabaseModel
import online.playoftime.database.TimeDatabase

class UpdateSessionUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(value: SessionDatabaseModel){
        // Пишем ТОЛЬКО тайминги (endTime, activeTimeMs). Полный @Update затирал
        // isQuestComplete=true, записанный асинхронным markSessionQuestComplete,
        // обратно во false из state-копии — из-за чего init-restore подтягивал
        // попытки завершённого квеста в новую сессию (numbering-баг).
        database.sessionDao().updateSessionTimes(
            id           = value.id,
            endTime      = value.endTime,
            activeTimeMs = value.activeTimeMs
        )
    }
}