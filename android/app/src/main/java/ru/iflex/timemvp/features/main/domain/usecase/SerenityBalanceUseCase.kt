package ru.iflex.timemvp.features.main.domain.usecase

import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.database.TimeDatabase

/**
 * Возвращает баланс Serenity Coins:
 * @return Pair(totalBalance, todayDelta)
 */
class SerenityBalanceUseCase(
    private val database: TimeDatabase
) {
    suspend operator fun invoke(): Pair<Double, Double> {
        val total = database.statisticDao().getTotalSerenityBalance()
        val todayStart = PeriodFilter.TODAY.startTimeMs()
        val todayDelta = database.statisticDao().getDaySerenityBalance(todayStart)
        return Pair(total, todayDelta)
    }
}
