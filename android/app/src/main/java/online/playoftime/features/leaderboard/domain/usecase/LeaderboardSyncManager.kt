package online.playoftime.features.leaderboard.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import online.playoftime.BuildConfig
import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.resolutionFloorMs
import online.playoftime.database.SyncAttemptRow
import online.playoftime.database.TimeDatabase
import online.playoftime.features.leaderboard.data.LeaderboardApi
import kotlin.math.abs

/**
 * Зеркалит попытки на playoftime.online (тот же сервер, что и webapp) —
 * лидерборд ранжирует их через тот же MySQL. Best-effort: сеть недоступна —
 * молча откладываем до следующего вызова, геймплей никогда не блокируется
 * (тот же принцип, что и в web performSync()).
 *
 * Singleton (не factory) — нужен для дебаунса: notifyNewAttempt() должен
 * отменять/переустанавливать ОДИН и тот же таймер между вызовами.
 */
class LeaderboardSyncManager(
    private val timeDatabase: TimeDatabase,
    private val localStorage: LocalStorageService,
    private val api: LeaderboardApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    @Volatile private var inFlight = false

    companion object {
        private const val BATCH_SIZE = 500
        private const val DEBOUNCE_MS = 1200L
    }

    /** Вызывать после каждой сохранённой в БД попытки — коалесцирует частые
     *  вызовы (серия попыток подряд) в один запрос, как scheduleSync() в web. */
    fun notifyNewAttempt() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            syncPending(drainAll = false)
        }
    }

    /** Вызывать один раз при старте приложения — докачивает ВСЮ ещё
     *  неотправленную локальную историю (не только новые попытки), как
     *  web-клиент делает при первой синхронизации. Идемпотентно: если уже
     *  всё отправлено, отработает мгновенно (первый батч окажется пустым). */
    fun backfillAll() {
        scope.launch {
            // Разово (2026-09-25): курсор в 0 — вся история уходит повторно,
            // сервер дописывает старым строкам recorded_ms. Флаг ставится сразу:
            // курсор сохраняется после каждого батча, так что прерванная
            // переотправка продолжится с места, а не начнётся заново.
            if (!localStorage.isRecordedMsResendStarted()) {
                localStorage.putLastSyncedAttemptId(0)
                localStorage.markRecordedMsResendStarted()
            }
            syncPending(drainAll = true)
        }
    }

    private suspend fun syncPending(drainAll: Boolean) {
        if (inFlight) return
        inFlight = true
        try {
            do {
                val afterId = localStorage.getLastSyncedAttemptId()
                val batch = timeDatabase.tryDao().getAttemptsForSync(afterId, BATCH_SIZE)
                if (batch.isEmpty()) break

                val payload = JSONObject().apply {
                    put("playerId", localStorage.getOrCreatePlayerId())
                    put("syncToken", localStorage.getOrCreateSyncToken())
                    put("levelId", localStorage.getCurrentLevelId())
                    put("taskId", localStorage.getCurrentTaskId())
                    // Версия сборки (Alex 2026-09-27). До этого синхронизация
                    // не сообщала о себе НИЧЕГО, и при переезде на новый
                    // идентификатор пакета выяснилось, что узнать, кто ещё
                    // сидит в старом приложении, нечем — а мостовую версию
                    // нельзя убирать с сайта, пока такие есть. Сервер пишет
                    // это в players.app_version при каждой синхронизации,
                    // сводку отдаёт api/app_versions.php.
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("attempts", JSONArray(batch.map { it.toSyncJson() }))
                }

                val result = api.sync(payload) ?: break
                if (result.optBoolean("ok", false).not()) break

                // Сервер — единственный источник правды для имени (генератор
                // коротких имён и одноразовая миграция переименовывают
                // игроков на сервере, без участия клиента) — всегда
                // подхватываем то, что он вернул, а не только при первой
                // синхронизации. НЕ отправляем "name" в payload выше: если бы
                // отправляли локально закешированное значение на каждый sync,
                // оно бы затирало обратно серверное переименование при
                // следующей же синхронизации (баг, найден 2026-09-10, см. TASKS.md).
                val assigned = result.optString("displayName", "")
                if (assigned.isNotBlank()) localStorage.putDisplayName(assigned)

                // batch включает (с 2026-09-03) саму afterId-попытку повторно —
                // её scm мог только что дозреть (см. doc-comment у getAttemptsForSync).
                // Если в батче нет НИЧЕГО новее afterId, значит с прошлого раза
                // не появилось ни одной попытки — курсор после апдейта останется
                // равен afterId, и batch будет тем же самым при следующем
                // вызове тоже. Без этой проверки drainAll=true (backfillAll на
                // старте приложения) зациклился бы НАВСЕГДА, бесконечно
                // пересылая одну и ту же уже дошедшую попытку.
                val hasNewBeyondBoundary = batch.any { it.id > afterId }
                localStorage.putLastSyncedAttemptId(batch.last().id)
                if (!hasNewBeyondBoundary) break
            } while (drainAll)
        } finally {
            inFlight = false
        }
    }

    private fun SyncAttemptRow.toSyncJson(): JSONObject {
        val durationMs = (endTime - startTime).toInt()
        val aimMs = 3000
        val errorMs = durationMs - aimMs
        val winTolMs = maxOf(toleranceMs, resolutionFloorMs(accuracy))
        val isWin = abs(errorMs) <= winTolMs
        return JSONObject().apply {
            put("localId", id)
            put("sessionId", sessionId)
            put("levelId", questLevelId ?: 0)
            put("taskId", questTaskId ?: 0)
            put("accuracy", accuracy)
            put("durationMs", durationMs)
            put("errorMs", errorMs)
            put("isWin", if (isWin) 1 else 0)
            put("toleranceMs", toleranceMs)
            put("sc", scm ?: 0.0)
            put("recordedAt", startTime)
        }
    }
}
