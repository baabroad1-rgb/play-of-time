package ru.iflex.timemvp.core.utils

import java.util.Calendar

enum class PeriodFilter {
    SESSION, TODAY, MONTH, ALL_TIME;

    /** Метка для PeriodPill */
    val label: String get() = when (this) {
        SESSION  -> "Session"
        TODAY    -> "Today"
        MONTH    -> "Month"
        ALL_TIME -> "All time"
    }

    /** Время начала периода в мс (0L = без фильтра). Для SESSION — нужен sessionId. */
    fun startTimeMs(): Long = when (this) {
        SESSION  -> 0L
        ALL_TIME -> 0L
        TODAY -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        MONTH -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
    }
}
