package ru.iflex.timemvp.features.main.domain.usecase

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.core.utils.formatTime
import ru.iflex.timemvp.core.utils.getTasksCountForLevel
import ru.iflex.timemvp.database.TimeDatabase
import ru.iflex.timemvp.database.TryDatabaseModel
import ru.iflex.timemvp.features.main.ui.SessionStatisticItemState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

class GetLogAttemptsUseCase(
    private val db: TimeDatabase,
    private val localStorage: LocalStorageService
) {
    suspend operator fun invoke(
        filter: PeriodFilter,
        sessionId: Int?
    ): List<SessionStatisticItemState> {
        val aimTimeMs = localStorage.getAimTime().toLong()
        val accuracy  = localStorage.getAccuracy()

        // Если в период попадает хотя бы одна попытка сессии — показываем ВСЕ её попытки.
        // Иначе сессия, начавшаяся вчера и продолжившаяся 1 попыткой сегодня,
        // выглядит как «сессия из 1 попытки» — отсюда жалобы пользователя.
        val rawAttempts: List<TryDatabaseModel> = when (filter) {
            PeriodFilter.SESSION  -> if (sessionId != null)
                db.tryDao().getAttemptsBySessionId(sessionId)
            else emptyList()

            PeriodFilter.TODAY    -> attemptsForSessionsTouching(PeriodFilter.TODAY.startTimeMs())
            PeriodFilter.MONTH    -> attemptsForSessionsTouching(PeriodFilter.MONTH.startTimeMs())
            PeriodFilter.ALL_TIME -> db.tryDao().getAttemptsSince(0L)
        }

        if (rawAttempts.isEmpty()) return emptyList()

        val aimStr = formatTime(aimTimeMs, accuracy)

        // Global session numbering: all sessions sorted oldest→newest, oldest = #1
        val allSessions = db.sessionDao().getAllSessions().sortedBy { it.startTime }
        val sessionNumberMap  = allSessions.mapIndexed { i, s -> s.id to (i + 1) }.toMap()
        val sessionStartTimeMap = allSessions.associate { it.id to it.startTime }

        // Заголовок сессии: ДАТА на первом месте (Alex 2026-07-12), затем номер
        // сессии, квест и время старта в 24-ч формате.
        val dayFmt  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Newest attempts first → group by session, preserving newest-session-first order
        val newestFirst  = rawAttempts.reversed()
        val sessionOrder = newestFirst.map { it.sessionId }.distinct()
        val bySession    = newestFirst.groupBy { it.sessionId }

        val result = mutableListOf<SessionStatisticItemState>()

        for (sid in sessionOrder) {
            val attempts = bySession[sid] ?: continue

            // Заголовок сессии — НАД её попытками, у КАЖДОЙ сессии (включая самую
            // новую сверху). Раньше разделитель вставлялся ПОСЛЕ блока попыток и нёс
            // номер ВЕРХНЕЙ сессии — визуально же строка «Session #N · Quest … · дата»
            // читается как заголовок блока ПОД ней. Из-за этого все подписи выглядели
            // сдвинутыми на одну сессию, а нумерация попыток — «сбитой» (жалоба Alex
            // 2026-07-06): над «Session #5» болталась чужая попытка #1, а под ним
            // лежали попытки сессии #4.
            run {
                val sessionNum   = sessionNumberMap[sid] ?: 0
                val sessionStart = sessionStartTimeMap[sid] ?: attempts.last().startTime
                val dateStr      = dateFmt.format(Date(sessionStart))
                val sessionInfo  = allSessions.firstOrNull { it.id == sid }
                // Сессия считается субквестной, если хотя бы одна её попытка была
                // зафиксирована с tolerance > 0 (попадание ROW-квеста на индульгенции).
                // Для IN_N-индульгенции (N > baseline) сейчас распознать не можем —
                // сессия начнётся с пометкой "Quest" даже на N=66/99. Для полного
                // покрытия нужно добавить поле в SessionDatabaseModel (миграция),
                // но для подавляющего случая (ROW при ±t) этого хватает.
                val sessionIsSubquest = attempts.any { it.toleranceMs > 0 }
                val questWord = if (sessionIsSubquest) "SubQuest of" else "Quest"
                val questLabel = if (sessionInfo != null && sessionInfo.questLevelId > 0) {
                    val total = getTasksCountForLevel(sessionInfo.questLevelId)
                    "Level ${sessionInfo.questLevelId}. $questWord ${sessionInfo.questTaskId} of $total"
                } else ""
                val labelParts = listOfNotNull(
                    "Session #$sessionNum",
                    questLabel.takeIf { it.isNotEmpty() },
                    dateStr
                )
                // Дата — только у САМОГО ВЕРХНЕГО заголовка лога (первый header
                // в newest-first списке), выравнивается по левому краю строки.
                val isTopmostHeader = result.isEmpty()
                result.add(SessionStatisticItemState(
                    number          = 0,
                    time            = "",
                    error           = 0.0,
                    deflection      = null,
                    isMaximum       = null,
                    isMinimum       = null,
                    isSessionHeader = true,
                    sessionLabel    = labelParts.joinToString("  ·  "),
                    sessionDateLabel = if (isTopmostHeader)
                        dayFmt.format(Date(sessionStart)) else ""
                ))
            }

            // Attempt rows for this session
            for ((idxInSession, attempt) in attempts.withIndex()) {
                val duration = attempt.endTime - attempt.startTime
                val timeStr  = formatTime(duration, accuracy)
                val rawError = timeStr.toDouble() - aimStr.toDouble()
                val error = when (accuracy) {
                    Accuracy.SECONDS      -> round(rawError)
                    Accuracy.DECISECONDS  -> round(rawError * 10.0) / 10.0
                    Accuracy.CENTISECONDS -> round(rawError * 100.0) / 100.0
                    Accuracy.MILLISECONDS -> round(rawError * 1000.0) / 1000.0
                }
                val deflection = attempt.deflection?.let { d ->
                    when (accuracy) {
                        Accuracy.SECONDS      -> d.div(1000).toInt()
                        Accuracy.DECISECONDS  -> d.div(100).toInt()
                        Accuracy.CENTISECONDS -> d.div(10).toInt()
                        Accuracy.MILLISECONDS -> d.toInt()
                    }
                }
                // Continuous 1..N numbering within a session (no wrap at 33);
                // sessions now run up to 99 attempts under indulgence.
                // attempts идут newest-first → номер (oldest = 1) считается
                // арифметически. Раньше был reversed().indexOfFirst на каждой
                // итерации — O(n²) на сессию (заметно на 99-попыточных сессиях
                // в ALL_TIME логе).
                val positionInSession = attempts.size - 1 - idxInSession
                // Win = |totalError| ≤ tolerance активный в момент попытки.
                // toleranceMs хранится в TryDatabaseModel начиная с миграции 5→6
                // (для строк до миграции default 0 — победа только при exact-нуле,
                // совпадает с прежним поведением, не даёт ложных регрессий).
                // Без этого победа в субквесте ±0.01 теряла зелёный цвет в логе,
                // как только сессия закрывалась и попытка читалась обратно из БД.
                val totalErr = error + (deflection?.toDouble() ?: 0.0)
                val isWin = kotlin.math.abs(totalErr * 1000.0) <= attempt.toleranceMs + 0.0005
                result.add(SessionStatisticItemState(
                    number     = (positionInSession + 1).toLong(),
                    time       = timeStr,
                    error      = error,
                    deflection = deflection,
                    isMaximum  = null,
                    isMinimum  = null,
                    hand       = attempt.hand,
                    isWin      = isWin,
                    scm        = attempt.scm,
                    startTimestamp = attempt.startTime,
                    endTimestamp   = attempt.endTime
                ))
            }

        }

        // Post-process: пересчитываем twin shot между смежными попытками внутри сессий.
        // БД не хранит twin-флаг, поэтому в TODAY/MONTH/ALL_TIME просмотрах его надо
        // вычислять заново. Парность только между не-победами, |error| совпадает.
        // result хранится newest-first; идём от старых к новым внутри одной сессии.
        for (i in result.indices) {
            val cur = result[i]
            if (cur.isSessionHeader || cur.isWin) continue
            // Соседний (более новый) элемент в newest-first списке — это i-1.
            val newer = result.getOrNull(i - 1) ?: continue
            if (newer.isSessionHeader || newer.isWin) continue
            if (kotlin.math.abs(kotlin.math.abs(cur.error) - kotlin.math.abs(newer.error)) < 0.0005) {
                result[i]     = cur.copy(twinShot = true)
                result[i - 1] = newer.copy(twinShot = true)
            }
        }

        return result
    }

    /**
     * Возвращает попытки всех сессий, у которых есть хотя бы одна попытка
     * с `startTime >= fromTime`. Так пограничные сессии (начались до фильтра,
     * продолжаются после) отображаются ЦЕЛИКОМ, без визуальной «потери» начала.
     */
    private suspend fun attemptsForSessionsTouching(fromTime: Long): List<TryDatabaseModel> {
        val periodAttempts = db.tryDao().getAttemptsSince(fromTime)
        if (periodAttempts.isEmpty()) return emptyList()
        val touchingSessionIds = periodAttempts.map { it.sessionId }.toSet()
        return touchingSessionIds
            .flatMap { sid -> db.tryDao().getAttemptsBySessionId(sid) }
            .sortedBy { it.startTime }
    }
}
