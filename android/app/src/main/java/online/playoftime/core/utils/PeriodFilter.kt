package online.playoftime.core.utils

import java.util.Calendar

// ВАЖНО: LocalStorageService.getPeriodFilter()/putPeriodFilter() персистят ЭТОТ enum
// по ORDINAL (SharedPreferences int), а не по имени — WEEK добавлен ПОСЛЕДНИМ в списке,
// чтобы не сдвинуть ordinal у MONTH/ALL_TIME и не сломать сохранённый выбор периода
// у существующих игроков (та же ловушка, что уже была с SCM-миграциями/Room ordinal'ами
// в этом проекте). Никогда не вставляй новое значение в середину этого списка.
enum class PeriodFilter {
    SESSION, TODAY, MONTH, ALL_TIME, WEEK;

    /** Метка для PeriodPill (используется только на экране аналитики —
     *  единственное место, где вообще читается это свойство). TODAY
     *  показывается как "Day", а не "Today" (Alex 2026-09-09) — короче и
     *  ровнее по ритму с Session/Week/Month/All time. */
    val label: String get() = when (this) {
        SESSION  -> "Session"
        TODAY    -> "Day"
        WEEK     -> "Week"
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
        // Календарная неделя с первого дня недели по локали (Alex 2026-10-06):
        // та же неделя, что в дереве сессий. Раньше было скользящее окно
        // «последние 7 дней» — и неделя в фильтре не совпадала с неделей в дереве.
        WEEK -> weekStart().timeInMillis
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

    /**
     * [начало, конец) периода со сдвигом `offset` периодов НАЗАД от текущего
     * (0 = тот же диапазон, что и startTimeMs(); 1 = предыдущий период того же
     * типа, и т.д.). Только для TODAY/WEEK/MONTH — у SESSION/ALL_TIME сдвиг не
     * по датам (SESSION листается по номеру сессии, ALL_TIME листать некуда).
     * Alex 2026-09-09: клювики влево/вправо под фильтром на экране аналитики,
     * листающие день/неделю/месяц туда-сюда.
     *
     * Верхняя граница при offset=0 намеренно уходит в будущее (полночь
     * следующего дня и т.п.) — это безвредно (более новых попыток, чем "сейчас",
     * в базе быть не может) и позволяет использовать одну и ту же формулу для
     * всех offset без спецкейса "текущий период не ограничен сверху".
     */
    fun rangeMsForOffset(offset: Int): Pair<Long, Long> = when (this) {
        TODAY -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            start to cal.timeInMillis
        }
        WEEK -> {
            val cal = weekStart()
            cal.add(Calendar.DAY_OF_YEAR, -7 * offset)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 7)
            start to cal.timeInMillis
        }
        MONTH -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.MONTH, -offset)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            start to cal.timeInMillis
        }
        SESSION, ALL_TIME -> 0L to Long.MAX_VALUE // не используется для этих типов
    }

    /** Полночь первого дня текущей недели — firstDayOfWeek локали, как WEEK_OF_YEAR в дереве. */
    private fun weekStart(): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        while (cal.get(Calendar.DAY_OF_WEEK) != cal.firstDayOfWeek) cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal
    }
}
