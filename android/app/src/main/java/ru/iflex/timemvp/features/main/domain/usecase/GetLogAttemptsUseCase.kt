package ru.iflex.timemvp.features.main.domain.usecase

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.core.utils.formatTime
import ru.iflex.timemvp.core.utils.getTasksCountForLevel
import ru.iflex.timemvp.core.utils.resolutionFloorMs
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

            PeriodFilter.TODAY    -> attemptsForSessionsTouching(PeriodFilter.TODAY.startTimeMs(), sessionId)
            PeriodFilter.WEEK     -> attemptsForSessionsTouching(PeriodFilter.WEEK.startTimeMs(), sessionId)
            PeriodFilter.MONTH    -> attemptsForSessionsTouching(PeriodFilter.MONTH.startTimeMs(), sessionId)
            PeriodFilter.ALL_TIME -> db.tryDao().getAttemptsSince(0L)
        }

        if (rawAttempts.isEmpty()) return emptyList()

        // Нумерация ПО ДНЮ, не глобальная за всю историю (Alex 2026-09-22) —
        // см. perDaySessionNumbers.
        val allSessions = db.sessionDao().getAllSessions().sortedBy { it.startTime }
        val sessionNumberMap  = ru.iflex.timemvp.core.utils.perDaySessionNumbers(allSessions)
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
                // Субквест по ROW-индульгенции: t выше пола разрешения (на L3
                // baseline t=4 — это НЕ субквест, это физический пол, не показываем).
                // Самая глубокая ступень индульгенции сессии = по max toleranceMs.
                val maxTolAtt = attempts
                    .filter { it.toleranceMs > resolutionFloorMs(it.accuracy) }
                    .maxByOrNull { it.toleranceMs }
                val sessionIsSubquest = maxTolAtt != null
                val subStep = if (maxTolAtt != null) {
                    val stepMs = when (maxTolAtt.accuracy) { 0 -> 1000; 1 -> 100; 2 -> 10; else -> 1 }
                    (maxTolAtt.toleranceMs / stepMs).coerceAtLeast(1)
                } else 0
                // Нумерация (Alex 2026-07-30): субквест — "SubQuest N.k", без "of Z";
                // квест — "Quest N of Z".
                val questLabel = if (sessionInfo != null && sessionInfo.questLevelId > 0) {
                    val total = getTasksCountForLevel(sessionInfo.questLevelId)
                    if (sessionIsSubquest)
                        "Level ${sessionInfo.questLevelId}. SubQuest ${sessionInfo.questTaskId}.$subStep"
                    else
                        "Level ${sessionInfo.questLevelId}. Quest ${sessionInfo.questTaskId} of $total"
                } else ""
                val labelParts = listOfNotNull(
                    "Session #$sessionNum",
                    questLabel.takeIf { it.isNotEmpty() },
                    dateStr
                )
                // Дата — у КАЖДОГО заголовка (Alex 2026-07-23): заголовки теперь
                // sticky при скролле, и прилипший заголовок старой сессии обязан
                // показывать СВОЮ дату.
                result.add(SessionStatisticItemState(
                    number          = 0,
                    time            = "",
                    error           = 0.0,
                    deflection      = null,
                    isMaximum       = null,
                    isMinimum       = null,
                    isSessionHeader = true,
                    sessionLabel    = labelParts.joinToString("  ·  "),
                    sessionDateLabel = dayFmt.format(Date(sessionStart)),
                    // Палец вверх/вниз (Alex 2026-07-23): активная сессия — без
                    // индикатора, завершённые — по isQuestComplete.
                    sessionThumbUp  = if (sid == sessionId) null
                                      else sessionInfo?.isQuestComplete == true
                ))
            }

            // Attempt rows for this session
            for ((idxInSession, attempt) in attempts.withIndex()) {
                val duration = attempt.endTime - attempt.startTime
                // Разрядность — та, в которой ИГРАЛАСЬ попытка (Alex 2026-07-23):
                // после перехода на следующий уровень старые попытки НЕ
                // пересчитываются в новую точность. Иначе чистая 3.0 уровня L1
                // при глобальной милли-точности превращалась в 2.953 и краснела.
                val attAccuracy = Accuracy.values().getOrNull(attempt.accuracy) ?: accuracy
                val timeStr   = formatTime(duration, attAccuracy)
                val aimStrAtt = formatTime(aimTimeMs, attAccuracy)
                val rawError  = timeStr.toDouble() - aimStrAtt.toDouble()
                val error = when (attAccuracy) {
                    Accuracy.SECONDS      -> round(rawError)
                    Accuracy.DECISECONDS  -> round(rawError * 10.0) / 10.0
                    Accuracy.CENTISECONDS -> round(rawError * 100.0) / 100.0
                    Accuracy.MILLISECONDS -> round(rawError * 1000.0) / 1000.0
                }
                val deflection = attempt.deflection?.let { d ->
                    when (attAccuracy) {
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
                // Пол разрешения через max — исторические L3-попытки (toleranceMs=0
                // до обновления) тоже красятся зелёным в пределах ±4 мс.
                val winTolMs = maxOf(attempt.toleranceMs, resolutionFloorMs(attempt.accuracy))
                val isWin = kotlin.math.abs(totalErr * 1000.0) <= winTolMs + 0.0005
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
                    endTimestamp   = attempt.endTime,
                    accuracy   = attAccuracy
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
            // Шаг «соседства» — одна клетка точности, В КОТОРОЙ игралась попытка.
            val neighborStepSec = when (cur.accuracy ?: accuracy) {
                Accuracy.DECISECONDS  -> 0.1
                Accuracy.CENTISECONDS -> 0.01
                Accuracy.MILLISECONDS -> 0.001
                Accuracy.SECONDS      -> Double.MAX_VALUE
            }
            val diff = kotlin.math.abs(kotlin.math.abs(cur.error) - kotlin.math.abs(newer.error))
            if (diff < 0.0005) {
                result[i]     = result[i].copy(twinShot = true)
                result[i - 1] = result[i - 1].copy(twinShot = true)
            } else if (kotlin.math.abs(diff - neighborStepSec) < 0.0005) {
                // Neighbor shot (Alex 2026-07-23): |error| отличается ровно на
                // одну клетку — значения обводятся жёлтым в логе.
                result[i]     = result[i].copy(neighborShot = true)
                result[i - 1] = result[i - 1].copy(neighborShot = true)
            }
        }

        return result
    }

    /**
     * Возвращает попытки всех сессий, у которых есть хотя бы одна попытка
     * с `startTime >= fromTime`. Так пограничные сессии (начались до фильтра,
     * продолжаются после) отображаются ЦЕЛИКОМ, без визуальной «потери» начала.
     */
    private suspend fun attemptsForSessionsTouching(
        fromTime: Long,
        currentSessionId: Int? = null
    ): List<TryDatabaseModel> {
        val periodAttempts = db.tryDao().getAttemptsSince(fromTime)
        // Смена суток в течение сессии (Alex 2026-07-23): АКТИВНАЯ сессия
        // остаётся в логе до своего конца, даже если все её попытки легли до
        // полуночи. Иначе после 00:00 фильтр TODAY очищал лог посреди сессии.
        val touchingSessionIds =
            (periodAttempts.map { it.sessionId } + listOfNotNull(currentSessionId)).toSet()
        if (touchingSessionIds.isEmpty()) return emptyList()
        return touchingSessionIds
            .flatMap { sid -> db.tryDao().getAttemptsBySessionId(sid) }
            .sortedBy { it.startTime }
    }
}
