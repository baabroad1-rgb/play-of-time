package ru.iflex.timemvp.features.main.domain

data class QuestCompletionStats(
    val levelId: Int,
    val taskId: Int,
    val sessionAttempts: Int,
    val sessionWins: Int,
    val congratulationText: String,
    // Quest-wide stats (populated asynchronously after quest win).
    // Сравнение «этот квест vs накопленное среднее за всю историю»
    // (Alex 2026-07-12). Все средние — геометрические.
    val questDays: Int = 0,
    val questPureTimeMinutes: Int = 0,
    val scmEarned: Double = 0.0,
    val questEarlyMs: Double = 0.0,
    val overallEarlyMs: Double = 0.0,
    val questLateMs: Double = 0.0,
    val overallLateMs: Double = 0.0,
    val hitRateQuestPct: Int = 0,
    val hitRateOverallPct: Int = 0,
    // Квест, НА который игрок переходит (0 — игра закончена). По нему карточка
    // выбирает игривую похвалу (CheerUpMessages.praiseFor).
    val arrivalLevelId: Int = 0,
    val arrivalTaskId: Int = 0,
    // Переход уровня (последний квест уровня взят) — золотой блок в GreetingView
    val isLevelComplete: Boolean = false,
    val isGameComplete: Boolean = false,
    /** Например "hundredths of a second: 3.00" — точность следующего уровня. */
    val nextPrecisionLabel: String = "",
    val levelAttempts: Int = 0,
    val levelTrueThrees: Int = 0,
    // ── Интерпретация «победная сессия vs вся история» (Alex 2026-07-12) ──
    // Показывается с L2.2. Сентинелы -1.0 / -1 = данных нет, строка скрыта.
    val sessPrecisionMs: Double = -1.0,
    val histPrecisionMs: Double = -1.0,
    val sessLucky: Double = -1.0,
    val histLucky: Double = -1.0,
    val sessFocusPct: Int = -1,
    val histFocusPct: Int = -1,
    val sessConsistPct: Int = -1,
    val histConsistPct: Int = -1,
    val sessPauseMin: Int = -1,
    val histPauseMin: Int = -1,
    // Возбуждение (ранние ошибки) / торможение (поздние) — Alex 2026-07-15
    val sessExcitationMs: Double = -1.0,
    val histExcitationMs: Double = -1.0,
    val sessInhibitionMs: Double = -1.0,
    val histInhibitionMs: Double = -1.0,
) {
    /** Интерпретационный блок доступен начиная со второго квеста второго уровня. */
    val interpretationUnlocked: Boolean get() =
        levelId >= 3 || (levelId == 2 && taskId >= 2)

    val winRatePct: Int get() =
        if (sessionAttempts > 0) (sessionWins * 100 / sessionAttempts) else 0

    val hasQuestStats: Boolean get() = questDays > 0 || scmEarned > 0.0

    // «Improved» = в этом квесте лучше твоего накопленного среднего.
    // Сравнение осмысленно только когда есть обе стороны (> 0).
    val earlyImproved: Boolean get() =
        questEarlyMs > 0 && overallEarlyMs > 0 && questEarlyMs < overallEarlyMs
    val lateImproved:  Boolean get() =
        questLateMs > 0 && overallLateMs > 0 && questLateMs < overallLateMs
    val hitImproved:   Boolean get() = hitRateQuestPct > hitRateOverallPct

    val earlyWorsened: Boolean get() =
        questEarlyMs > 0 && overallEarlyMs > 0 && questEarlyMs > overallEarlyMs
    val lateWorsened:  Boolean get() =
        questLateMs > 0 && overallLateMs > 0 && questLateMs > overallLateMs
    val hitWorsened:   Boolean get() = hitRateQuestPct < hitRateOverallPct

    val allImproved: Boolean get() = earlyImproved && lateImproved && hitImproved
    val allWorsened: Boolean get() = earlyWorsened && lateWorsened && hitWorsened
}
