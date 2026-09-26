package ru.iflex.timemvp.core.utils

import ru.iflex.timemvp.database.SessionDatabaseModel
import java.util.Calendar
import java.util.Locale

/**
 * "Session #N" — номер СРЕДИ СЕССИЙ ТОГО ЖЕ КАЛЕНДАРНОГО ДНЯ (Alex 2026-09-22),
 * НЕ глобальный счётчик за всю историю. Раньше (с 20 июня) номер был глобальным
 * ("oldest = #1", монотонно растёт всю жизнь БД) — за месяцы тестов и разработки
 * набежали сотни строк в SessionDatabaseModel, и реальный сегодняшний игрок с
 * 10-15 сессиями за день видел "Session #130" — число никак не соотносится с
 * тем, сколько раз он СЕГОДНЯ садился играть, отсюда жалоба "номер не привязан
 * к дате". Сброс на 1 в начале каждого календарного дня (по местному времени
 * устройства) — единая функция, ОДНА на все три места, где раньше нумерация
 * считалась по отдельности и по-разному (GetLogAttemptsUseCase, AnalyticsUseCase,
 * MainViewModel — тот самый класс расхождения, из-за которого номер "не
 * совпадал" ещё в июле). Возвращает id сессии -> номер за её день.
 */
fun perDaySessionNumbers(sessions: List<SessionDatabaseModel>): Map<Int, Int> {
    val cal = Calendar.getInstance()
    fun dayKey(ms: Long): Long {
        cal.timeInMillis = ms
        return cal.get(Calendar.YEAR).toLong() * 1000L + cal.get(Calendar.DAY_OF_YEAR)
    }
    val counters = mutableMapOf<Long, Int>()
    val result = mutableMapOf<Int, Int>()
    for (s in sessions.sortedBy { it.startTime }) {
        val key = dayKey(s.startTime)
        val next = (counters[key] ?: 0) + 1
        counters[key] = next
        result[s.id] = next
    }
    return result
}

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

