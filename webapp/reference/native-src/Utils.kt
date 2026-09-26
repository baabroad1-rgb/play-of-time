package ru.iflex.timemvp.core.utils

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class Accuracy(val abbr: String) {
    SECONDS("s"),
    DECISECONDS("ds"),
    CENTISECONDS("cs"),
    MILLISECONDS("ms"),
}

/**
 * Дизеринг длительности (Alex 2026-07-25) — делает техническую вероятность
 * КАЖДОГО значения (включая ровно 3.000) одинаковой.
 *
 * Тачскрин семплит палец дискретно (~8 мс): сырая длительность неравномерна —
 * значения ложатся полосами через 8 мс, а ровно 3.000 попадает в провал (в
 * данных Alex — 2 попадания из 3491, соседи по ~150). Прибавляя к итоговой
 * длительности равномерный шум шириной в один период сетки
 * [−DITHER_HALF_MS, +DITHER_HALF_MS], декоррелируем ошибку квантования →
 * распределение становится равномерным, любое значение равновероятно. Это ровно
 * то, что раньше случайно делал джиттер диспетчера (до фикса латентности 14 июля
 * «работало справедливо»), только теперь контролируемо. Применяется в
 * MainViewModel к реальной попытке (отладочный auto-stop левой руки не трогаем).
 */
// Дизеринг теперь АДАПТИВНЫЙ (Alex 2026-07-26): ширина = половина ФАКТИЧЕСКИ
// измеренной ячейки опроса тача (TouchRateEstimator), а не фикс-константа.
// Частоту меряем по реальным тапам; при ячейке ≤ ~1 мс (≈1000 Гц) дизеринг сам
// отключается — панель уже честно разрешает до миллисекунды. Значение ниже —
// лишь ДЕФОЛТНАЯ ячейка (мс), пока в сессии не набралось замеров: 4 мс ≈ 250 Гц
// (как на OnePlus 10 Pro). Хранится и уточняется в LocalStorage.getTouchCellMs().
const val DEFAULT_TOUCH_CELL_MS = 4.0

// Пол допуска ОТКЛЮЧЁН (0): равномерность даёт дизеринг (DITHER_HALF_MS), а не
// расширение окна победы. Победа снова = точное попадание. Хелпер оставлен как
// no-op, чтобы не переписывать вызовы maxOf(tol, floor) — при floor=0 они = tol.
const val TOUCH_RESOLUTION_FLOOR_MS = 0

fun resolutionFloorMs(accuracyOrdinal: Int): Int =
    if (accuracyOrdinal == Accuracy.MILLISECONDS.ordinal) TOUCH_RESOLUTION_FLOOR_MS else 0

fun resolutionFloorMs(accuracy: Accuracy): Int = resolutionFloorMs(accuracy.ordinal)

enum class StopWatchCycle {
    ON_START,
    IS_RUNNING,
    ON_STOP,
}

enum class StatisticExcelFields(val columnName: String) {
    DATE("Date"),
    TIME("Time"),
    SESSION_NUMBER("Session number"),
    ATTEMPT_NUMBER("Attempt number"),
    ATTEMPT_RESULT("Attempt result, ms"),
    ERROR_TIME("Error time, ms"),
    ACCURACY("Accuracy")
}

fun getDate(timestampDate: Long): String {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy")
    val date = Date(timestampDate)
    return dateFormat.format(date)
}

fun formatDate(date: String): String {
    val inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val outputFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val date = LocalDate.parse(date, inputFormat)
    return date.format(outputFormat)
}

fun getHours(timestampDate: Long) : String {
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")
    val date = Date(timestampDate)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    return dateFormat.format(date)
}


fun formatTime(time: Long, accuracy: Accuracy): String {
    val seconds = (time / 1000).toInt()
    val timeSeconds = time.toDouble() / 1000

    return when (accuracy) {
        Accuracy.SECONDS -> "%d".format(seconds)

        Accuracy.DECISECONDS -> String.format(
            Locale.US, "%03.1f", timeSeconds
        )

        Accuracy.CENTISECONDS -> String.format(
            Locale.US, "%04.2f", timeSeconds
        )

        Accuracy.MILLISECONDS -> String.format(
            Locale.US, "%.3f", timeSeconds
        )
    }
}

fun formatTime(time: Double, accuracy: Accuracy): String {
    return when (accuracy) {
        Accuracy.SECONDS -> "%d".format(time.toLong())

        Accuracy.DECISECONDS -> String.format(
            Locale.US, "%03.1f", time
        )

        Accuracy.CENTISECONDS -> String.format(
            Locale.US, "%04.2f", time
        )

        Accuracy.MILLISECONDS -> String.format(
            Locale.US, "%.3f", time
        )
    }
}

fun getLocalTimeInMillis(): Long {
    return System.currentTimeMillis()
}

