package ru.iflex.timemvp.features.stats.domain.usecase

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.database.TimeDatabase

class YourAnrUseCase (
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService,
) {
    suspend operator fun invoke(): Double {
        return database.statisticDao().getTotalARN(localStorage.getAimTime())
    }
}