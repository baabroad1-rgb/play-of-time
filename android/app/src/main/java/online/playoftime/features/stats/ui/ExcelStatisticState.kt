package online.playoftime.features.stats.ui

data class ExcelStatisticState(
    val date: String,
    val time: String,
    val sessionNumber: Long,
    val attemptNumber: Long,
    val attemptResult: Long,
    val errorTime: Long,
    val accuracy: String,
    val hand: String
)