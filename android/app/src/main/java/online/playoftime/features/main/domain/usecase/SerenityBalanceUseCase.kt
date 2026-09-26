package online.playoftime.features.main.domain.usecase

import online.playoftime.core.utils.PeriodFilter
import online.playoftime.database.TimeDatabase

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
