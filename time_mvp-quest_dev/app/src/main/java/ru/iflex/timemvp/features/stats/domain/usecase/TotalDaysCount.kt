package ru.iflex.timemvp.features.stats.domain.usecase

import ru.iflex.timemvp.database.TimeDatabase

class TotalDaysCount (
    private val database: TimeDatabase
) {
    suspend operator fun invoke(): String {
        return database.statisticDao().getTotalDaysCount().toString()
    }
}