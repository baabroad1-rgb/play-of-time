package online.playoftime.features.stats.ui

data class ExcelStatisticState(
    val date: String,
    val time: String,
    val sessionNumber: Long,
    val attemptNumber: Long,
    val attemptResult: Long,
    val errorTime: Long,
    val accuracy: String,
    val hand: String,
    // Колонки, добавленные 2026-10-25 (Alex): «да, все добавь».
    val sc: Double?,            // монеты Serenity за попытку (у самой свежей ещё нет)
    val pauseSec: Double?,      // пауза перед попыткой в той же сессии, с (у первой — нет)
    val isGreen: Boolean,       // зелёная с учётом смягчения, как Hit rate
    val toleranceMs: Int,       // допуск квеста в момент попытки
    val sessionEarly: Int,      // ранних промахов во всей сессии
    val sessionLate: Int        // поздних промахов во всей сессии
)