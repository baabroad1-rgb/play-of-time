package ru.iflex.timemvp.use_case

import ru.iflex.timemvp.database.SessionDatabaseModel
import ru.iflex.timemvp.database.TimeDatabase

class GetCurrentSessionIdUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(): SessionDatabaseModel {
        val session = database.sessionDao().getCurrentSession()
        return session
    }
}