package ru.iflex.timemvp.core.utils

enum class VictoryCondition {
    SESSION,  // Хотя бы X попаданий за сессию (без ограничения попыток)
    ROW,      // X попаданий подряд (без промахов между ними)
    IN_N,     // X попаданий в пределах N попыток (не обязательно подряд)
}

enum class Victory(
    val victoryCount: Int,
    val victoryCondition: VictoryCondition,
    val taskDescription: String
) {
    ONE_ONLY(1,   VictoryCondition.SESSION, ""),
    TWO_IN_ROW(2, VictoryCondition.ROW,     "two times in a row"),
    THREE_IN_ROW(3,VictoryCondition.ROW,    "three times in a row"),
    TWO_IN_N(2,   VictoryCondition.IN_N,    "two times in N attempts"),
    THREE_IN_N(3, VictoryCondition.IN_N,    "three times in N attempts"),
}

/**
 * @param levelId             Номер уровня (1-3)
 * @param taskId              Номер квеста внутри уровня
 * @param victory             Условие победы
 * @param accuracy            Точность отображения времени
 * @param maxAttemptsDefault  Лимит попыток по умолчанию (null = без лимита). Хранится
 *                            в LocalStorage и адаптируется при провале/успехе.
 * @param toleranceMsDefault  Допустимое отклонение от T в мс по умолчанию (0 = точное
 *                            попадание). Для уровня 3 — адаптируется при провале/успехе.
 * @param adaptiveN           true — N адаптируется при провале/успехе на [adaptiveNStep]
 * @param adaptiveT           true — t увеличивается на 1 мс при провале, уменьшается при успехе
 * @param adaptiveNStep       шаг изменения N при провале/успехе (по умолчанию 33; квест 3.4 = 1)
 * @param toleranceStepsCap   максимум ступеней роста t при adaptiveT (по умолчанию 3, т.е.
 *                            потолок = toleranceMsDefault + 3 шага; L3.4/L3.5 = 9 → потолок ±9мс)
 * @param congratulationDescription  Текст поздравления при завершении квеста (null — нет всплывашки)
 */
class Quest(
    val levelId: Int,
    val taskId: Int,
    val victory: Victory,
    val accuracy: Accuracy,
    val maxAttemptsDefault: Int?,
    val toleranceMsDefault: Int,
    val adaptiveN: Boolean,
    val adaptiveT: Boolean,
    val adaptiveNStep: Int = 33,
    val toleranceStepsCap: Int = 3,
    val congratulationDescription: String?,
)

// ── Соответствие PDF-уровням из quests_oder.pdf ──────────────────────────────
//
// Восемь дизайн-уровней из PDF разнесены по 10 квестам (3 кода-уровня по точности):
//
//   PDF Level 1  → code L1.1, L1.2, L1.3   (3.0,  без лимита, ONE/TWO_ROW/THREE_ROW)
//   PDF Level 2  → code L2.1               (3.00, без лимита, ONE)
//   PDF Level 3  → code L2.2               (3.00, N=33 ±33,  TWO_IN_N)
//   PDF Level 4  → code L2.3               (3.00, N=33 ±33,  THREE_IN_N)
//   Extra (Alex) → code L2.4               (3.00, без лимита, TWO_IN_ROW)
//   Extra (Alex) → code L2.5               (3.00, без лимита, THREE_IN_ROW)
//   PDF Level 5  → code L3.1               (3.000, N=33 ±33, t±1ms, ONE_ONLY)
//   PDF Level 6  → code L3.2               (3.000, N=33 ±33, t±1ms, TWO_IN_N)
//   PDF Level 8  → code L3.3               (3.000, N=33 ±33, t±1ms, THREE_IN_N)
//   PDF Level 7  → code L3.4               (3.000, N=33 ±33, t±1ms, TWO_IN_ROW)
//   Extra (Alex) → code L3.5               (3.000, N=33 ±33, t±1ms, THREE_IN_ROW) ← final quest
//
// ПРАВИЛА (Alex 2026-06-23):
//   • N ≥ 33 и всегда кратен 33. Для всех адаптивных квестов baseline N=33, шаг ±33.
//   • Индульгенция (адаптация) — только один вид одновременно, никогда оба:
//       – IN_N квесты (TWO_IN_N, THREE_IN_N, ONE_ONLY-as-1-in-N) → N±33 only
//       – ROW квесты (TWO_IN_ROW, THREE_IN_ROW)                 → t±1ms only
//     (правило применяется к квестам, начиная с L2.2)
//   • Цикл индульгенции ограничен потолком (cap):
//       – N: 33 → 66 → 99 (2 ступени, всегда)
//       – t: 0 → 1 → 2 → … → toleranceStepsCap ms (по умолчанию cap=3;
//         L3.4/L3.5 подняты до cap=9 — Alex 2026-08-08)
//     После cap параметр больше не растёт даже при следующих провалах.
//   • Выход из цикла (advance к следующему квесту) — только после победы НА
//     ИСХОДНЫХ параметрах (baseline). См. onQuestWindowSuccess в MainViewModel.kt.
//
// Окно оценки N=33 присутствует у всех адаптивных квестов (включая ROW): пока счётчик
// попыток окна не достигнет 33 без достижения streak'а, провал не фиксируется.
val questList = listOf(

    // ── Level 1: Deciseconds (3.0) — структура зеркалит L2/L3 ─────────────────
    // L1.1 ONE_ONLY @ 3.0 — без лимита попыток
    Quest(
        levelId = 1, taskId = 1,
        victory = Victory.ONE_ONLY,
        accuracy = Accuracy.DECISECONDS,
        maxAttemptsDefault = null, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = false,
        congratulationDescription = null
    ),
    // L1.2 TWO_IN_N @ 3.0 — IN_N → N±33 (зеркало L2.2)
    Quest(
        levelId = 1, taskId = 2,
        victory = Victory.TWO_IN_N,
        accuracy = Accuracy.DECISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = true, adaptiveT = false,
        congratulationDescription = null
    ),
    // L1.3 THREE_IN_N @ 3.0 — IN_N → N±33 (зеркало L2.3)
    Quest(
        levelId = 1, taskId = 3,
        victory = Victory.THREE_IN_N,
        accuracy = Accuracy.DECISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = true, adaptiveT = false,
        congratulationDescription = null
    ),
    // L1.4 TWO_IN_ROW @ 3.0 — без лимита (раньше был L1.2)
    Quest(
        levelId = 1, taskId = 4,
        victory = Victory.TWO_IN_ROW,
        accuracy = Accuracy.DECISECONDS,
        maxAttemptsDefault = null, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = false,
        congratulationDescription = null
    ),
    // L1.5 THREE_IN_ROW @ 3.0 — финал уровня 1 (раньше был L1.3)
    Quest(
        levelId = 1, taskId = 5,
        victory = Victory.THREE_IN_ROW,
        accuracy = Accuracy.DECISECONDS,
        maxAttemptsDefault = null, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = false,
        congratulationDescription = "You are at Level 2 Now!"
    ),

    // ── Level 2: Centiseconds (3.00) ──────────────────────────────────────────
    // PDF Level 2: без лимита, ONE
    Quest(
        levelId = 2, taskId = 1,
        victory = Victory.ONE_ONLY,
        accuracy = Accuracy.CENTISECONDS,
        maxAttemptsDefault = null, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = false,
        congratulationDescription = null
    ),
    // PDF Level 3: TWO_IN_N с N=33 ±33 (циклическая индульгенция по N)
    Quest(
        levelId = 2, taskId = 2,
        victory = Victory.TWO_IN_N,
        accuracy = Accuracy.CENTISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = true, adaptiveT = false,
        congratulationDescription = null
    ),
    // PDF Level 4: THREE_IN_N с N=33 ±33
    Quest(
        levelId = 2, taskId = 3,
        victory = Victory.THREE_IN_N,
        accuracy = Accuracy.CENTISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = true, adaptiveT = false,
        congratulationDescription = null
    ),
    // L2.4 TWO_IN_ROW @ 3.00 — ROW квест: индульгенция только t±1ms (N=33 — окно оценки).
    Quest(
        levelId = 2, taskId = 4,
        victory = Victory.TWO_IN_ROW,
        accuracy = Accuracy.CENTISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = true,
        congratulationDescription = null
    ),
    // L2.5 THREE_IN_ROW @ 3.00 — последний квест уровня 2.
    Quest(
        levelId = 2, taskId = 5,
        victory = Victory.THREE_IN_ROW,
        accuracy = Accuracy.CENTISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = true,
        congratulationDescription = "You are at Level 3 Now!"
    ),

    // ── Level 3: Milliseconds (3.000) ─────────────────────────────────────────
    // L3.1 ONE_ONLY @ 3.000 — БЕЗ лимита попыток (решение Alex 2026-07-07,
    // зеркалит L2.1): счётчик сессии идёт до 99 и начинается заново; провала
    // окна и индульгенции у первого квеста уровня нет.
    Quest(
        levelId = 3, taskId = 1,
        victory = Victory.ONE_ONLY,
        accuracy = Accuracy.MILLISECONDS,
        maxAttemptsDefault = null, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = false,
        congratulationDescription = "You feel the millisecond!"
    ),
    // L3.2 TWO_IN_N @ 3.000 — IN_N → N±33 only.
    Quest(
        levelId = 3, taskId = 2,
        victory = Victory.TWO_IN_N,
        accuracy = Accuracy.MILLISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = true, adaptiveT = false,
        congratulationDescription = "Two milliseconds nailed!"
    ),
    // L3.3 THREE_IN_N @ 3.000 — IN_N → N±33 only.
    Quest(
        levelId = 3, taskId = 3,
        victory = Victory.THREE_IN_N,
        accuracy = Accuracy.MILLISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = true, adaptiveT = false,
        congratulationDescription = "Three in a window — fantastic!"
    ),
    // L3.4 TWO_IN_ROW @ 3.000 — ROW → t±1ms only (N=33 — окно оценки).
    // Потолок индульгенции поднят до ±9мс (Alex 2026-08-08; было ±3мс).
    Quest(
        levelId = 3, taskId = 4,
        victory = Victory.TWO_IN_ROW,
        accuracy = Accuracy.MILLISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = true,
        toleranceStepsCap = 9,
        congratulationDescription = "Two milliseconds in a row — incredible!"
    ),
    // L3.5 THREE_IN_ROW @ 3.000 — финальный квест игры. ROW → t±1ms only.
    // Потолок индульгенции поднят до ±9мс (Alex 2026-08-08; было ±3мс).
    Quest(
        levelId = 3, taskId = 5,
        victory = Victory.THREE_IN_ROW,
        accuracy = Accuracy.MILLISECONDS,
        maxAttemptsDefault = 33, toleranceMsDefault = 0,
        adaptiveN = false, adaptiveT = true,
        toleranceStepsCap = 9,
        congratulationDescription = "You are the best!"
    ),
)

fun getCurrentQuest(levelId: Int, taskId: Int): Quest {
    return questList.firstOrNull { it.levelId == levelId && it.taskId == taskId }
        ?: questList[0]
}

fun getTasksCountForLevel(levelId: Int): Int {
    return questList.count { it.levelId == levelId }
}
