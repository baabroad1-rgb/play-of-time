package online.playoftime.features.stats.domain.usecase

import online.playoftime.database.TimeDatabase

class TotalDaysCount (
    private val database: TimeDatabase
) {
    suspend operator fun invoke(): String {
        return database.statisticDao().getTotalDaysCount().toString()
    }
}