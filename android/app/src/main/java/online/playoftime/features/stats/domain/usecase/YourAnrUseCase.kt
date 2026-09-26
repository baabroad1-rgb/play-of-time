package online.playoftime.features.stats.domain.usecase

import online.playoftime.core.data.LocalStorageService
import online.playoftime.database.TimeDatabase

class YourAnrUseCase (
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService,
) {
    suspend operator fun invoke(): Double {
        return database.statisticDao().getTotalARN(localStorage.getAimTime())
    }
}