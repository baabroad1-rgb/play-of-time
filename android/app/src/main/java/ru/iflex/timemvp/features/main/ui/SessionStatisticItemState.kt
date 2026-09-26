package ru.iflex.timemvp.features.main.ui

/** Роль строки в группе twin-shot: NONE — не twin; SOLO — одиночная (не должно случаться,
 *  twin всегда парный); TOP / MIDDLE / BOTTOM — позиция внутри группы из 2+ подряд равных ошибок. */
enum class TwinRole { NONE, SOLO, TOP, MIDDLE, BOTTOM }

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
    val endTimestamp: Long = 0L,
    /** true — строка-разделитель между сессиями, не попытка */
    val isSessionHeader: Boolean = false,
    val sessionLabel: String = "",
    /** Дата dd/MM/yyyy — заполняется ТОЛЬКО у самого верхнего заголовка сессии
     *  в логе; рисуется слева от разделительной линии (Alex 2026-07-12). */
    val sessionDateLabel: String = "",
    /** Serenity Coins earned on this attempt (null = unknown / not computed) */
    val scm: Double? = null,
    /** true — эта попытка повторила |error| предыдущей попытки (twin shot).
     *  Поле в первичном конструкторе → попадает в copy(), и LazyColumn пересоберёт строку. */
    val twinShot: Boolean = false,
    /** true — |error| этой попытки СОСЕДНИЙ (± одна клетка точности уровня)
     *  с |error| смежной попытки — «wow, neighbors!» (Alex 2026-07-23).
     *  Значение ошибки в строке обводится жёлтым. */
    val neighborShot: Boolean = false,
    /** Точность, в которой ИГРАЛАСЬ попытка — значение ошибки в строке
     *  форматируется в ней, а не в текущей глобальной (Alex 2026-07-23).
     *  null → использовать глобальную (старое поведение). */
    val accuracy: ru.iflex.timemvp.core.utils.Accuracy? = null,
    /** Индикатор успешности сессии на строке-заголовке: true = палец вверх
     *  (квест взят в этой сессии), false = палец вниз (сессия закончилась без
     *  победы), null = не показывать (сессия ещё идёт). */
    val sessionThumbUp: Boolean? = null
)
