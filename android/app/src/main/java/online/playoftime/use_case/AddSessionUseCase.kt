package online.playoftime.use_case

import online.playoftime.database.SessionDatabaseModel
import online.playoftime.database.TimeDatabase

class AddSessionUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(
        startTime    : Long,
        questLevelId : Int = 0,
        questTaskId  : Int = 0
    ) {
        val value = SessionDatabaseModel(
            id           = 0,
            startTime    = startTime,
            endTime      = 1L,
            questLevelId = questLevelId,
            questTaskId  = questTaskId
        )
        database.sessionDao().insert(value)
    }
}
