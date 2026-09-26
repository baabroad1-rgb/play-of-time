package online.playoftime.use_case

import online.playoftime.database.SessionDatabaseModel
import online.playoftime.database.TimeDatabase

class GetCurrentSessionIdUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(): SessionDatabaseModel {
        val session = database.sessionDao().getCurrentSession()
        return session
    }
}