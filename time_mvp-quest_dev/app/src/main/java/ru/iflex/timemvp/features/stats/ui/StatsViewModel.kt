package ru.iflex.timemvp.features.stats.ui

import android.content.Intent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.BaseViewModel
import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.excelFileName
import ru.iflex.timemvp.core.utils.orientationMode
import ru.iflex.timemvp.core.utils.portraitMode
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.features.stats.domain.usecase.StatUseCases
import ru.iflex.timemvp.features.stats.ui.StatsContract.Effect
import ru.iflex.timemvp.features.stats.ui.StatsContract.Event
import ru.iflex.timemvp.features.stats.ui.StatsContract.State
import ru.iflex.timemvp.use_case.ContextUseCase
import ru.iflex.timemvp.use_case.RecalculateScmUseCase
import java.util.Calendar

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

                // Auto-expand tree path to the most recent session
                val recent = analytics.sessions.maxByOrNull { it.startTime }
                val cal    = Calendar.getInstance()
                val (autoYears, autoMonths, autoWeeks, autoDays, autoSessions) = if (recent != null) {
                    cal.timeInMillis = recent.startTime
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH)
                    val d = cal.get(Calendar.DAY_OF_MONTH)
                    val w = cal.get(Calendar.WEEK_OF_YEAR)
                    listOf(setOf(y), setOf("$y-$m"), setOf("$y-$m-$w"), setOf("$y-$m-$d"), setOf(recent.sessionId))
                } else {
                    listOf(emptySet(), emptySet(), emptySet(), emptySet(), emptySet())
                }

                setState {
                    copy(
                        analytics        = analytics,
                        analyticsLoading = false,
                        selectedPeriod   = period,
                        expandedYears    = autoYears    as Set<Int>,
                        expandedMonths   = autoMonths   as Set<String>,
                        expandedWeeks    = autoWeeks    as Set<String>,
                        expandedDays     = autoDays     as Set<String>,
                        expandedSessions = autoSessions as Set<Int>
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
                        ru.iflex.timemvp.core.utils.writeExcelToDownloads(
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
