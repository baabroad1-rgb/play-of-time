package online.playoftime.features.leaderboard.ui

import online.playoftime.core.ViewEvent
import online.playoftime.core.ViewSideEffect
import online.playoftime.core.ViewState
import online.playoftime.features.leaderboard.domain.entity.LeaderboardRow
import online.playoftime.features.leaderboard.domain.entity.LeaderboardSort

class LeaderboardContract {
    data class State(
        val sort: LeaderboardSort = LeaderboardSort.LEVEL,
        /** true — лучшие сверху (▼), false — по возрастанию (▲). */
        val descending: Boolean = true,
        val rows: List<LeaderboardRow> = emptyList(),
        val loading: Boolean = true,
        val error: Boolean = false,
        val myPlayerId: String = "",
        val nameInput: String = "",
        val nameStatus: String = ""
    ) : ViewState

    sealed class Event : ViewEvent {
        data class SelectSort(val sort: LeaderboardSort) : Event()
        data object Refresh : Event()
        data class NameInputChanged(val value: String) : Event()
        data object SaveName : Event()
    }

    sealed class Effect : ViewSideEffect
}
