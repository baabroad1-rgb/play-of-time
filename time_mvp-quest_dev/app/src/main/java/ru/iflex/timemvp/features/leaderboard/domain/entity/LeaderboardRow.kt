package ru.iflex.timemvp.features.leaderboard.domain.entity

/** Одна строка лидерборда — зеркалит JSON-ответ leaderboard.php (webapp). */
data class LeaderboardRow(
    val playerId: String,
    val displayName: String?,
    val levelId: Int,
    val taskId: Int,
    val scTotal: Double,
    val attemptsTotal: Int,
    val winsTotal: Int,
    val hitRatePct: Double,
    val combinedScore: Double,
    /** empirical/theoretical clean-three rate, server-computed (Alex 2026-09-10) — see leaderboard.php doc-comment. */
    val luckyIndex: Double,
    /** ISO-UTC время первой попытки (текущая эпоха) — "как давно начал". */
    val firstAttemptAt: String? = null,
    /** Статистика по каждому уровню игры (ключ 1..3) — для сравнения "на одном уровне". */
    val tiers: Map<Int, TierStats> = emptyMap()
)

/** Срез статистики игрока на одном уровне игры (accuracy-тир 1/2/3). */
data class TierStats(
    val attempts: Int,
    val wins: Int,
    val scTotal: Double,
    val hitRatePct: Double,
    val luckyIndex: Double,
    val firstAttemptAt: String?
)

/** Сортировки лидерборда — те же 4, что в webapp (LB_SORTS). */
enum class LeaderboardSort(val apiValue: String, val label: String) {
    LEVEL("level", "Progress"),
    SC("sc", "Coins"),
    HITRATE("hitrate", "Accuracy"),
    COMBINED("combined", "Combined")
}

/**
 * Порядок строк «лучшие сверху» по колонке [sort] (Alex 2026-09-19: сортировка
 * по возрастанию/убыванию не работала, колонка Luck сортировалась по
 * combinedScore, а не по показанному luckyIndex). Сортируем на клиенте ровно
 * по тому значению, что показано в колонке; ничьи разрешаются прогрессом
 * (уровень → квест → монеты), чтобы порядок был стабильным. Для возрастания
 * вызывающий просто разворачивает список.
 */
fun List<LeaderboardRow>.sortedBestFirst(sort: LeaderboardSort): List<LeaderboardRow> {
    val byProgress = compareByDescending<LeaderboardRow> { it.levelId }
        .thenByDescending { it.taskId }
        .thenByDescending { it.scTotal }
    val primary: Comparator<LeaderboardRow> = when (sort) {
        LeaderboardSort.LEVEL    -> byProgress
        LeaderboardSort.SC       -> compareByDescending<LeaderboardRow> { it.scTotal }.then(byProgress)
        LeaderboardSort.HITRATE  -> compareByDescending<LeaderboardRow> { it.hitRatePct }.then(byProgress)
        LeaderboardSort.COMBINED -> compareByDescending<LeaderboardRow> { it.luckyIndex }.then(byProgress)
    }
    return sortedWith(primary.thenBy { it.playerId })
}
