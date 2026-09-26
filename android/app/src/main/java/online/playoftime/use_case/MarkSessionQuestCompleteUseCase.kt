package online.playoftime.use_case

import online.playoftime.database.TimeDatabase

class MarkSessionQuestCompleteUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(sessionId: Int) {
        database.sessionDao().updateQuestComplete(sessionId, true)
    }
}
