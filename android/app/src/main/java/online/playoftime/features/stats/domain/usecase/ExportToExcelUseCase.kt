package online.playoftime.features.stats.domain.usecase

import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.Accuracy
import online.playoftime.database.TimeDatabase
import online.playoftime.features.stats.ui.ExcelStatisticState

class ExportToExcelUseCase(
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService,
) {
    suspend operator fun invoke(): List<ExcelStatisticState> {
        val list = database.statisticDao().exportToExcel(localStorage.getAimTime())
        val result = mutableListOf<ExcelStatisticState>()
        var attemptNumber = 1L

        for (i in list.indices) {
            val it = list[i]
            val item = ExcelStatisticState(
                date = it.date,
                time = it.time,
                sessionNumber = it.sessionNumber,
                attemptNumber = attemptNumber,
                attemptResult = it.attemptResult,
                errorTime = it.errorTime,
                accuracy = Accuracy.entries[it.accuracy].abbr,
                hand = it.hand
            )
            if (i < list.size - 1) {
                if (it.sessionNumber == list[i + 1].sessionNumber) {
                    attemptNumber += 1
                } else {
                    attemptNumber = 1L
                }
            }
            result.add(item)
        }
        return result
    }
}
