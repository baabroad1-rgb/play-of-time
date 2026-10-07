package online.playoftime.features.stats.ui

import online.playoftime.core.ViewEvent
import online.playoftime.core.ViewSideEffect
import online.playoftime.core.ViewState
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.features.stats.domain.entity.AnalyticsData

class StatsContract {
    data class State(
        val selectedPeriod: PeriodFilter = PeriodFilter.ALL_TIME,
        // Аналитика
        val analytics: AnalyticsData? = null,
        val analyticsLoading: Boolean = true,
        // Дерево: какие узлы раскрыты
        val expandedYears: Set<Int> = emptySet(),
        val treeDefaultApplied: Boolean = false,
        val expandedMonths: Set<String> = emptySet(),
        val expandedWeeks: Set<String> = emptySet(),
        val expandedDays: Set<String> = emptySet(),
        val expandedSessions: Set<Int> = emptySet(),
        // ExportToExcel
        val displayExportToExcelResultDialog: Boolean = false,
        val exportToExcelResultDialogText: String = "",
        val displayAppSpinner: Boolean = false,
    ) : ViewState

    sealed class Event : ViewEvent {
        data object ExportToExel : Event()
        data class SelectPeriod(val period: PeriodFilter) : Event()
        /** Клювики влево/вправо под фильтром (Alex 2026-09-09): delta=+1 — на
         *  период назад (старше), delta=-1 — на период вперёд (к "сейчас"). */
        data class ShiftPeriod(val delta: Int) : Event()
        /** Перезагрузка данных под текущий период — StatScreen шлёт при каждом
         *  выходе на передний план (HorizontalPager держит экран живым, init
         *  отработал один раз — без Refresh данные протухали). */
        data object Refresh : Event()
        data class ToggleYear(val year: Int) : Event()
        data class ToggleMonth(val key: String) : Event()
        data class ToggleWeek(val key: String) : Event()
        data class ToggleDay(val key: String) : Event()
        data class ToggleSession(val sessionId: Int) : Event()

        sealed class Dialog : Event() {
            data class Display(val text: String) : Dialog()
            data object Close : Dialog()
        }

        sealed class AppSpinner : Event() {
            data object Display : AppSpinner()
            data object Close : AppSpinner()
        }
    }

    sealed class Effect : ViewSideEffect
}
