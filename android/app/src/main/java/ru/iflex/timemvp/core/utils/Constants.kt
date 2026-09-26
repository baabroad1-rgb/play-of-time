package ru.iflex.timemvp.core.utils

const val beatsPerSecondLength = 16
const val defaultBeatsPerSecond = 2
const val deflectionLength = 3
const val defaultAimTime = 3000
const val yourAnrConst = 2 // 1 было 1 потом 3
const val minAttemptDuration = 2000L
const val maxAttemptDuration = 4000L
const val excelFileName = "day_times_tap.xlsx"
// Автозакрытие сессии для квестов БЕЗ лимита попыток (L1.1, L1.4, L1.5, L2.1, L3.1):
// счётчик идёт до 99, сессия закрывается и нумерация начинается заново (Alex 2026-07-07).
// Квесты с лимитом закрываются по своему эффективному N (33/66/99).
const val sessionAutoCloseAttempts = 99

const val portraitMode = "Portrait Mode"
const val landscapeMode = "Landscape Mode"
const val orientationMode = "Orientaion Mode"