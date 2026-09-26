package ru.iflex.timemvp.features.stats.domain.entity

data class DailyStatisticsEntity (
    val number: Int,
    val date: String,
    val attempts: Long,
    val wins: Long,
)