package online.playoftime.features.leaderboard.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import online.playoftime.core.BaseViewModel
import online.playoftime.core.data.LocalStorageService
import online.playoftime.features.leaderboard.data.LeaderboardApi
import online.playoftime.features.leaderboard.domain.entity.LeaderboardRow
import online.playoftime.features.leaderboard.domain.entity.LeaderboardSort
import online.playoftime.features.leaderboard.domain.entity.TierStats
import online.playoftime.features.leaderboard.ui.LeaderboardContract.Effect
import online.playoftime.features.leaderboard.ui.LeaderboardContract.Event
import online.playoftime.features.leaderboard.ui.LeaderboardContract.State

class LeaderboardViewModel(
    private val localStorage: LocalStorageService,
    private val api: LeaderboardApi
) : BaseViewModel<Event, State, Effect>() {

    // Инициализация БЕЗ обращения к localStorage: BaseViewModel вычисляет
    // initialState через `by lazy` в СВОЁМ property-инициализаторе, который
    // выполняется ДО того как отработают property-инициализаторы подкласса —
    // на этот момент localStorage (val из конструктора) ещё null → NPE.
    // Реальные значения выставляются ниже в init{} через setState, когда
    // конструктор уже полностью отработал.
    override fun setInitialState() = State()

    init {
        setState {
            copy(
                myPlayerId = localStorage.getOrCreatePlayerId(),
                nameInput  = localStorage.getDisplayName() ?: ""
            )
        }
        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        viewModelScope.launch {
            setState { copy(loading = rows.isEmpty(), error = false) }
            val result = api.fetchLeaderboard(LeaderboardSort.LEVEL.apiValue)
            if (result == null) {
                setState { copy(loading = false, error = true) }
                return@launch
            }
            val list = mutableListOf<LeaderboardRow>()
            result.optJSONArray("players")?.let { players ->
                for (i in 0 until players.length()) {
                    val o = players.optJSONObject(i) ?: continue
                    list.add(
                        LeaderboardRow(
                            playerId      = o.optString("playerId"),
                            displayName   = o.optString("displayName", "").takeIf { it.isNotBlank() },
                            levelId       = o.optInt("levelId"),
                            taskId        = o.optInt("taskId"),
                            scTotal       = o.optDouble("scTotal", 0.0),
                            attemptsTotal = o.optInt("attemptsTotal"),
                            winsTotal     = o.optInt("winsTotal"),
                            hitRatePct    = o.optDouble("hitRatePct", 0.0),
                            combinedScore = o.optDouble("combinedScore", 0.0),
                            luckyIndex    = o.optDouble("luckyIndex", 0.0),
                            firstAttemptAt = o.optString("firstAttemptAt", "").takeIf { it.isNotBlank() && it != "null" },
                            tiers = buildMap {
                                o.optJSONObject("tiers")?.let { t ->
                                    for (k in t.keys()) {
                                        val ti = t.optJSONObject(k) ?: continue
                                        val level = k.toIntOrNull() ?: continue
                                        put(
                                            level,
                                            TierStats(
                                                attempts       = ti.optInt("attempts"),
                                                wins           = ti.optInt("wins"),
                                                scTotal        = ti.optDouble("scTotal", 0.0),
                                                hitRatePct     = ti.optDouble("hitRatePct", 0.0),
                                                luckyIndex     = ti.optDouble("luckyIndex", 0.0),
                                                firstAttemptAt = ti.optString("firstAttemptAt", "").takeIf { it.isNotBlank() && it != "null" }
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    )
                }
            }
            setState { copy(rows = list, loading = false, error = false) }
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.SelectSort -> {
                // Сортировка клиентская (см. sortedBestFirst): повторный тап по
                // активной колонке меняет направление, тап по другой — новая
                // колонка, лучшие сверху. Сервер всегда отдаёт один и тот же
                // набор игроков, перезапрос не нужен.
                setState {
                    if (event.sort == sort) copy(descending = !descending)
                    else copy(sort = event.sort, descending = true)
                }
            }

            is Event.Refresh -> fetchLeaderboard()

            is Event.NameInputChanged -> setState { copy(nameInput = event.value) }

            is Event.SaveName -> {
                val name = viewState.value.nameInput.trim().take(24)
                setState { copy(nameStatus = "Saving…") }
                viewModelScope.launch {
                    val payload = JSONObject().apply {
                        put("playerId", localStorage.getOrCreatePlayerId())
                        put("syncToken", localStorage.getOrCreateSyncToken())
                        put("name", name)
                    }
                    val result = api.setName(payload)
                    if (result != null) {
                        val resolved = result.optString("displayName", "")
                        localStorage.putDisplayName(resolved.ifBlank { null })
                        // Своё имя — только если игрок ввёл непустое (пустое = сервер
                        // выдал автоимя) — им заменяется «You» (Alex 2026-11-11).
                        localStorage.putOwnName(if (name.isBlank()) null else resolved.ifBlank { null })
                        setState {
                            copy(
                                nameInput  = resolved,
                                nameStatus = "Saved."
                            )
                        }
                        fetchLeaderboard()
                    } else {
                        setState { copy(nameStatus = "Could not save — check your connection and try again.") }
                    }
                }
            }
        }
    }
}
