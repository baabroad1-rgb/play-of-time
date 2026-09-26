package ru.iflex.timemvp.features.main.ui

data class SessionStatisticItemState(
    val number: Long,
    val time: String,
    val error: Double,
    val deflection: Int?,
    var isMaximum: Boolean?,
    var isMinimum: Boolean?,
    val hand: String = "",
    /** true — попытка засчитана как победа (попадание в T±t с учётом текущего допуска) */
    val isWin: Boolean = false,
    /** Момент нажатия кнопки (мс, getLocalTimeInMillis). 0 если неизвестен (история из БД). */
    val startTimestamp: Long = 0L,
    /** Момент отпускания кнопки (мс, getLocalTimeInMillis). 0 если неизвестен (история из БД). */
    val endTimestamp: Long = 0L
) {
}
