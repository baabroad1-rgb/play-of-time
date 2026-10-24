package online.playoftime.use_case

import online.playoftime.database.TimeDatabase
import online.playoftime.database.TryDatabaseModel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

class GetQuestSummaryUseCase(private val database: TimeDatabase) {

    data class Result(
        val questDays: Int,
        val pureTimeMinutes: Int,
        val scmEarned: Double,
        // «Этот квест vs накопленное среднее» (Alex 2026-07-12). Все средние —
        // ГЕОМЕТРИЧЕСКИЕ (правило проекта: любое среднее = геом. среднее).
        // overall* — по ВСЕМ попыткам за всю историю, включая этот квест.
        val questEarlyMs: Double,
        val overallEarlyMs: Double,
        val questLateMs: Double,
        val overallLateMs: Double,
        // Hit rate — доля, а не среднее; геометрия неприменима.
        val hitRateQuestPct: Int,
        val hitRateOverallPct: Int,
        // ── Интерпретация «победная сессия vs вся история» (Alex 2026-07-12) ──
        // Сентинелы: -1.0 / -1 = данных недостаточно, строка не показывается.
        val sessPrecisionMs: Double = -1.0,   // геом. среднее |ошибок| сессии
        val histPrecisionMs: Double = -1.0,
        val sessLucky: Double = -1.0,         // lucky index (как на аналитике)
        val histLucky: Double = -1.0,
        val sessFocusPct: Int = -1,           // % смен знака red↔blue между соседями
        val histFocusPct: Int = -1,
        val sessConsistPct: Int = -1,         // % соседних пар с уменьшением |ошибки|
        val histConsistPct: Int = -1,
        val sessPauseMin: Int = -1,           // пауза перед этой сессией, мин
        val histPauseMin: Int = -1,           // геом. среднее межсессионных пауз, мин
        // Возбуждение/торможение (Alex 2026-07-15, павловская пара):
        // Excitation = геом. среднее РАННИХ ошибок (перевозбуждён — жмёт раньше),
        // Inhibition = геом. среднее ПОЗДНИХ (заторможен — жмёт позже).
        val sessExcitationMs: Double = -1.0,
        val histExcitationMs: Double = -1.0,
        val sessInhibitionMs: Double = -1.0,
        val histInhibitionMs: Double = -1.0,
    )

    suspend operator fun invoke(levelId: Int, taskId: Int, finalSessionId: Int? = null): Result {
        val sessions = database.sessionDao().getSessionsByQuest(levelId, taskId)
        if (sessions.isEmpty()) return empty()

        // Реальные дни игры: количество РАЗЛИЧНЫХ календарных дней, в которые шли
        // сессии этого квеста. Раньше считался календарный ОХВАТ (от первой сессии
        // до «сегодня») — квест, начатый месяц назад и добитый за один вечер,
        // показывал «30 days», а квест, пройденный за 10 минут, — «1 day».
        val cal = java.util.Calendar.getInstance()
        val questDays = sessions.map { s ->
            cal.timeInMillis = s.startTime
            cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        }.distinct().size

        // Pure time = сумма activeTimeMs (только пока MainScreen был активен),
        // а не wall-clock. Для старых сессий activeTimeMs бэкфилл'ен из
        // (endTime − startTime) миграцией 6→7.
        val pureTimeMs = sessions.sumOf { it.activeTimeMs }
        val pureTimeMinutes = (pureTimeMs / 60_000L).toInt()

        val questAttempts = sessions
            .flatMap { database.tryDao().getAttemptsBySessionId(it.id) }
            .sortedBy { it.startTime }

        val scmEarned = questAttempts.sumOf { it.scm ?: 0.0 }

        val allAttempts = database.tryDao().getAllAttempts().sortedBy { it.startTime }

        // ── Интерпретация: победная сессия vs вся история ────────────────
        val sessAttempts = finalSessionId
            ?.let { sid -> database.tryDao().getAttemptsBySessionId(sid).sortedBy { it.startTime } }
            ?: emptyList()

        // Focus/Consistency истории считаются ПО-СЕССИОННО (пары через границу
        // сессий бессмысленны) и агрегируются.
        val allBySession = allAttempts.groupBy { it.sessionId }.values.toList()

        val sessFocus   = focusPct(listOf(sessAttempts))
        val histFocus   = focusPct(allBySession)
        val sessConsist = consistencyPct(listOf(sessAttempts))
        val histConsist = consistencyPct(allBySession)

        // Паузы между сессиями: start_i − end_{i−1} по ВСЕМ сессиям истории.
        val allSessions = database.sessionDao().getAllSessions().sortedBy { it.startTime }
        val pausesMs = allSessions.zipWithNext { prev, next ->
            (next.startTime - prev.endTime).toDouble()
        }.filter { it > 0.0 }
        val histPauseMin = if (pausesMs.isEmpty()) -1
            else ceilMinutes(geomMean(pausesMs))
        val sessPauseMin = finalSessionId?.let { sid ->
            val idx = allSessions.indexOfFirst { it.id == sid }
            if (idx > 0) {
                val p = (allSessions[idx].startTime - allSessions[idx - 1].endTime).toDouble()
                if (p > 0.0) ceilMinutes(p) else -1
            } else -1
        } ?: -1

        return Result(
            questDays         = questDays,
            pureTimeMinutes   = pureTimeMinutes,
            scmEarned         = scmEarned,
            questEarlyMs      = earlyGeomMs(questAttempts),
            overallEarlyMs    = earlyGeomMs(allAttempts),
            questLateMs       = lateGeomMs(questAttempts),
            overallLateMs     = lateGeomMs(allAttempts),
            hitRateQuestPct   = hitRatePct(questAttempts),
            hitRateOverallPct = hitRatePct(allAttempts),
            sessPrecisionMs   = if (sessAttempts.isEmpty()) -1.0
                                else geomMean(sessAttempts.map { absErrMs(it) }),
            histPrecisionMs   = if (allAttempts.isEmpty()) -1.0
                                else geomMean(allAttempts.map { absErrMs(it) }),
            sessLucky         = if (sessAttempts.isEmpty()) -1.0 else luckyIndex(sessAttempts),
            histLucky         = if (allAttempts.isEmpty()) -1.0 else luckyIndex(allAttempts),
            sessFocusPct      = sessFocus,
            histFocusPct      = histFocus,
            sessConsistPct    = sessConsist,
            histConsistPct    = histConsist,
            sessPauseMin      = sessPauseMin,
            histPauseMin      = histPauseMin,
            // 0.0 = в выборке нет ранних/поздних ошибок → сентинел -1.0 (строка скрыта)
            sessExcitationMs  = earlyGeomMs(sessAttempts).takeIf { it > 0.0 } ?: -1.0,
            histExcitationMs  = earlyGeomMs(allAttempts).takeIf { it > 0.0 } ?: -1.0,
            sessInhibitionMs  = lateGeomMs(sessAttempts).takeIf { it > 0.0 } ?: -1.0,
            histInhibitionMs  = lateGeomMs(allAttempts).takeIf { it > 0.0 } ?: -1.0,
        )
    }

    // ── Метрики интерпретации ───────────────────────────────────────────

    // ПРАВИЛО (Alex 2026-07-12): ошибка для Precision-интерпретации ВСЕГДА
    // берётся сырой, с точностью 1/1000 с — БЕЗ округления до точности уровня.
    // Попытка L1 «3.0» участвует своей реальной ошибкой (напр. 40 ms).
    // Накопленное среднее копится в тех же сырых мс и сравнивается с сессией.
    private fun errMs(a: TryDatabaseModel): Double =
        ((a.endTime - a.startTime) - AIM_MS).toDouble()

    private fun absErrMs(a: TryDatabaseModel): Double = abs(errMs(a))

    /**
     * Сосредоточенность: % смен знака ошибки (early↔late) между соседними
     * попытками. Пары с нулевой ошибкой (чистая тройка — знака нет) выпадают
     * из знаменателя. -1 если валидных пар нет.
     */
    private fun focusPct(sessionGroups: List<List<TryDatabaseModel>>): Int {
        var alternations = 0
        var validPairs = 0
        for (group in sessionGroups) {
            group.sortedBy { it.startTime }.zipWithNext { a, b ->
                val ea = errMs(a); val eb = errMs(b)
                if (ea != 0.0 && eb != 0.0) {
                    validPairs++
                    if ((ea < 0) != (eb < 0)) alternations++
                }
            }
        }
        return if (validPairs == 0) -1 else (alternations * 100 / validPairs)
    }

    /**
     * Последовательность: % соседних пар, где |ошибка| строго уменьшилась
     * (снижение до нуля — тоже снижение). -1 если пар нет.
     */
    private fun consistencyPct(sessionGroups: List<List<TryDatabaseModel>>): Int {
        var drops = 0
        var pairs = 0
        for (group in sessionGroups) {
            group.sortedBy { it.startTime }.zipWithNext { a, b ->
                pairs++
                if (absErrMs(b) < absErrMs(a)) drops++
            }
        }
        return if (pairs == 0) -1 else (drops * 100 / pairs)
    }

    /**
     * Lucky index — общая формула core/utils/LuckyIndex.kt, та же, что на
     * Analytics и в дереве (Alex 2026-10-24): зелёные попытки против их шанса
     * по разбросу каждого уровня.
     */
    private fun luckyIndex(attempts: List<TryDatabaseModel>): Double =
        online.playoftime.core.utils.luckyIndex(
            attempts.map {
                online.playoftime.core.utils.LuckyAttempt(it.endTime - it.startTime, it.accuracy, it.toleranceMs)
            }
        )

    private fun ceilMinutes(ms: Double): Int =
        (((ms.toLong()) + 59_999L) / 60_000L).toInt()

    /** Геометрическое среднее положительных значений; 0.0 если данных нет. */
    private fun geomMean(values: List<Double>): Double {
        val pos = values.filter { it > 0.0 }
        if (pos.isEmpty()) return 0.0
        return exp(pos.sumOf { ln(it) } / pos.size)
    }

    private fun earlyGeomMs(attempts: List<TryDatabaseModel>): Double =
        geomMean(attempts.mapNotNull { a ->
            ((a.endTime - a.startTime) - AIM_MS).takeIf { it < 0 }?.let { -it.toDouble() }
        })

    private fun lateGeomMs(attempts: List<TryDatabaseModel>): Double =
        geomMean(attempts.mapNotNull { a ->
            ((a.endTime - a.startTime) - AIM_MS).takeIf { it > 0 }?.toDouble()
        })

    private fun hitRatePct(attempts: List<TryDatabaseModel>): Int {
        if (attempts.isEmpty()) return 0
        val wins = attempts.count { isWin(it) }
        return wins * 100 / attempts.size
    }

    private fun isWin(attempt: TryDatabaseModel): Boolean {
        val factor = 10.0.pow(attempt.accuracy.toDouble())
        val dur = ((attempt.endTime - attempt.startTime) / 1000.0 * factor).roundToLong()
        val aim = (AIM_MS / 1000.0 * factor).roundToLong()
        return dur == aim
    }

    private fun empty() = Result(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)

    companion object {
        private const val AIM_MS = 3000L
    }
}
