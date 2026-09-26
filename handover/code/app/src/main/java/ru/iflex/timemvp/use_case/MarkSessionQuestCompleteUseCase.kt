package ru.iflex.timemvp.use_case

import ru.iflex.timemvp.database.TimeDatabase

class MarkSessionQuestCompleteUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(sessionId: Int) {
        database.sessionDao().updateQuestComplete(sessionId, true)
    }
}
