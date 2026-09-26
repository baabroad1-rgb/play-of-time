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

