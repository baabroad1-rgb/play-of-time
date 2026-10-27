package online.playoftime.features.stats.domain.usecase

import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.Accuracy
import online.playoftime.core.utils.LuckyAttempt
import online.playoftime.database.TimeDatabase
import online.playoftime.features.stats.ui.ExcelStatisticState

class ExportToExcelUseCase(
    private val database: TimeDatabase,
    private val localStorage: LocalStorageService,
) {
    suspend operator fun invoke(): List<ExcelStatisticState> {
        val aimTime = localStorage.getAimTime()
        val list = database.statisticDao().exportToExcel(aimTime)
        val green = list.map {
            LuckyAttempt(it.endTime - it.startTime, it.accuracy, it.toleranceMs, aimTime).isGreen
        }
        // Баланс сессии: ранние и поздние промахи по всей сессии (зелёные — не промахи).
        val early = HashMap<Long, Int>()
        val late  = HashMap<Long, Int>()
        list.forEachIndexed { i, it ->
            if (!green[i]) {
                if (it.errorTime < 0) early[it.sessionNumber] = (early[it.sessionNumber] ?: 0) + 1
                if (it.errorTime > 0) late[it.sessionNumber]  = (late[it.sessionNumber] ?: 0) + 1
            }
        }
        val localDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val localTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val result = mutableListOf<ExcelStatisticState>()
        var attemptNumber = 1L
        val lastEnd = HashMap<Long, Long>()   // сессия → конец её предыдущей попытки

        for (i in list.indices) {
            val it = list[i]
            val item = ExcelStatisticState(
                // Местное время телефона (Alex 2026-10-27), а не date()/time()
                // из запроса — те в UTC, у московского игрока на 3 часа меньше.
                date = localDate.format(java.util.Date(it.startTime)),
                time = localTime.format(java.util.Date(it.startTime)),
                sessionNumber = it.sessionNumber,
                attemptNumber = attemptNumber,
                attemptResult = it.attemptResult,
                errorTime = it.errorTime,
                accuracy = Accuracy.entries[it.accuracy].abbr,
                hand = it.hand,
                sc = it.scm,
                pauseSec = lastEnd[it.sessionNumber]?.let { prev -> (it.startTime - prev).coerceAtLeast(0L) / 1000.0 },
                isGreen = green[i],
                toleranceMs = it.toleranceMs,
                sessionEarly = early[it.sessionNumber] ?: 0,
                sessionLate = late[it.sessionNumber] ?: 0
            )
            lastEnd[it.sessionNumber] = it.endTime
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
