package online.playoftime.features.stats.domain.entity

data class ExcelStatisticEntity (
    val date: String,
    val time: String,
    val sessionNumber: Long,
    val attemptNumber: Long,
    val attemptResult: Long,
    val errorTime: Long,
    val accuracy: Int,
    val hand: String
)