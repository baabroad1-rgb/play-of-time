package online.playoftime.features.stats.ui

import android.content.Intent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import online.playoftime.R
import online.playoftime.core.BaseViewModel
import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.utils.excelFileName
import online.playoftime.core.utils.orientationMode
import online.playoftime.core.utils.portraitMode
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.features.stats.domain.usecase.StatUseCases
import online.playoftime.features.stats.ui.StatsContract.Effect
import online.playoftime.features.stats.ui.StatsContract.Event
import online.playoftime.features.stats.ui.StatsContract.State
import online.playoftime.use_case.ContextUseCase
import online.playoftime.use_case.RecalculateScmUseCase

class StatsViewModel(
    private val context: ContextUseCase,
    private val statUseCases: StatUseCases,
    private val recalculateScm: RecalculateScmUseCase,
    private val localStorage: LocalStorageService
) : BaseViewModel<Event, State, Effect>() {


    override fun setInitialState() = State()

    init {
        with(context()) {
            Intent().also { intent ->
                intent.setAction(orientationMode)
                intent.putExtra(orientationMode, portraitMode)
                sendBroadcast(intent)
            }
        }

        // Загружаем аналитику под общий фильтр периода — тот же, что MainViewModel.
        // Так значения геом средних на двух экранах будут считаться по ОДНОМУ
        // набору попыток (Σtries и средние совпадут).
        loadAnalytics(localStorage.getPeriodFilter())
        viewModelScope.launch {
            recalculateScm()
        }
    }

    private fun loadAnalytics(period: PeriodFilter, offset: Int = 0) {
        viewModelScope.launch {
            // Полноэкранный спиннер — только на самой первой загрузке.
            // При Refresh (каждый выход экрана на передний план) старые данные
            // остаются видимыми, пока считаются новые — без мигания.
            setState { copy(analyticsLoading = analytics == null) }
            runCatching {
                val analytics = statUseCases.analyticsUseCase(period, offset)

                // Дерево по умолчанию раскрыто до уровня месяцев (Alex 2026-10-07):
                // годы развёрнуты, месяцы видны, ниже — свёрнуто. Делается только
                // при первой загрузке; раскрытые/свёрнутые вручную узлы при
                // обновлении экрана не трогаем.
                setState {
                    val years = analytics.sessions.map {
                        java.util.Calendar.getInstance().apply { timeInMillis = it.startTime }
                            .get(java.util.Calendar.YEAR)
                    }.toSet()
                    val applyDefault = !treeDefaultApplied && years.isNotEmpty()
                    copy(
                        analytics        = analytics,
                        analyticsLoading = false,
                        selectedPeriod   = period,
                        expandedYears    = if (applyDefault) years else expandedYears,
                        treeDefaultApplied = treeDefaultApplied || applyDefault
                    )
                }
            }.onFailure {
                setState { copy(analyticsLoading = false) }
            }
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.SelectPeriod -> {
                // Сохраняем выбор в общем LocalStorage — MainViewModel подхватит
                // тот же период при следующем открытии главного экрана.
                localStorage.putPeriodFilter(event.period)
                loadAnalytics(event.period)
            }

            is Event.Refresh -> {
                // Свежие данные при каждом появлении экрана (свайп / таб / бабл).
                loadAnalytics(viewState.value.selectedPeriod, viewState.value.analytics?.periodOffset ?: 0)
            }

            is Event.ShiftPeriod -> {
                val current  = viewState.value.analytics?.periodOffset ?: 0
                val newOffset = (current + event.delta).coerceAtLeast(0)
                if (newOffset != current) {
                    loadAnalytics(viewState.value.selectedPeriod, newOffset)
                }
            }

            is Event.ExportToExel -> {
                setState { copy(displayAppSpinner = true) }
                viewModelScope.launch(Dispatchers.Default) {
                    runCatching {
                        val totalDaysCount = statUseCases.totalDaysCount()
                        val fileName = "$totalDaysCount$excelFileName"
                        val data = statUseCases.exportToExcelUseCase()
                        online.playoftime.core.utils.writeExcelToDownloads(
                            context.context, fileName, data
                        )
                    }.fold(
                        onSuccess = { ok ->
                            setState {
                                copy(
                                    displayExportToExcelResultDialog = true,
                                    exportToExcelResultDialogText = if (ok)
                                        context.context.getString(R.string.success_load)
                                    else
                                        context.context.getString(R.string.fail_load),
                                    displayAppSpinner = false
                                )
                            }
                        },
                        onFailure = {
                            setState {
                                copy(
                                    displayExportToExcelResultDialog = true,
                                    exportToExcelResultDialogText = context.context.getString(R.string.fail_load),
                                    displayAppSpinner = false
                                )
                            }
                        }
                    )
                }
            }

            is Event.ToggleYear -> setState {
                val years = if (event.year in expandedYears)
                    expandedYears - event.year else expandedYears + event.year
                copy(expandedYears = years)
            }

            is Event.ToggleMonth -> setState {
                val months = if (event.key in expandedMonths)
                    expandedMonths - event.key else expandedMonths + event.key
                copy(expandedMonths = months)
            }

            is Event.ToggleWeek -> setState {
                val weeks = if (event.key in expandedWeeks)
                    expandedWeeks - event.key else expandedWeeks + event.key
                copy(expandedWeeks = weeks)
            }

            is Event.ToggleDay -> setState {
                val days = if (event.key in expandedDays)
                    expandedDays - event.key else expandedDays + event.key
                copy(expandedDays = days)
            }

            is Event.ToggleSession -> setState {
                val sessions = if (event.sessionId in expandedSessions)
                    expandedSessions - event.sessionId else expandedSessions + event.sessionId
                copy(expandedSessions = sessions)
            }

            is Event.Dialog.Display -> setState {
                copy(displayExportToExcelResultDialog = true, exportToExcelResultDialogText = event.text)
            }

            is Event.Dialog.Close -> setState {
                copy(displayExportToExcelResultDialog = false, exportToExcelResultDialogText = "")
            }

            is Event.AppSpinner.Display -> setState { copy(displayAppSpinner = true) }
            is Event.AppSpinner.Close -> setState { copy(displayAppSpinner = false) }
        }
    }

}
