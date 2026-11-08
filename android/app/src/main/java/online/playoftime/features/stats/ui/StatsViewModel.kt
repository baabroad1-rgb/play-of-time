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
import online.playoftime.features.rival.RivalEngine
import online.playoftime.features.stats.domain.entity.AnalyticsData
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
    private val localStorage: LocalStorageService,
    private val rival: RivalEngine
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
        // Doppelgänger догоняет историю игрока в фоне сразу при старте — к
        // первому переключению на AI ему почти нечего переигрывать.
        rival.requestSync()
    }

    /** Номер последней загрузки: ответ устаревшей (игрок успел переключить
     *  You/AI или период) отбрасывается. */
    private var loadGeneration = 0

    /** Дни соперника, попадающие в выбранный период экрана. */
    private fun daysForPeriod(all: List<RivalEngine.DayDigest>, a: AnalyticsData): List<RivalEngine.DayDigest> =
        when (a.period) {
            PeriodFilter.ALL_TIME -> all
            PeriodFilter.SESSION  -> a.selectedSessionId?.let { sid -> all.filter { sid in it.sessionIds } } ?: emptyList()
            else -> {
                val (from, to) = a.period.rangeMsForOffset(a.periodOffset)
                all.filter { it.startMs in from until to }
            }
        }

    private fun loadAnalytics(period: PeriodFilter, offset: Int = 0) {
        val generation = ++loadGeneration
        val showRival = viewState.value.showRival
        viewModelScope.launch {
            // Полноэкранный спиннер — только на самой первой загрузке.
            // При Refresh (каждый выход экрана на передний план) старые данные
            // остаются видимыми, пока считаются новые — без мигания.
            // Переключение You/AI — тоже спиннер: на экране чужие данные.
            setState { copy(analyticsLoading = analytics == null || analyticsIsRival != showRival) }
            runCatching {
                val analytics = if (showRival) rival.analytics(period, offset)
                                else statUseCases.analyticsUseCase(period, offset)
                val days = if (showRival) daysForPeriod(rival.dayDigests(), analytics) else emptyList()
                val report = if (showRival) rival.reportJson() else null
                val quest = if (showRival) rival.questLabel() else ""
                val llm = if (showRival) rival.llmEntries() else emptyMap()
                if (generation != loadGeneration) return@launch

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
                        analyticsIsRival = showRival,
                        rivalDays        = days,
                        rivalReportJson  = report,
                        rivalQuest       = quest,
                        diaryLlm         = if (showRival) llm else diaryLlm,
                        analyticsLoading = false,
                        selectedPeriod   = period,
                        expandedYears    = if (applyDefault) years else expandedYears,
                        treeDefaultApplied = treeDefaultApplied || applyDefault
                    )
                }
            }.onFailure {
                if (generation == loadGeneration) setState { copy(analyticsLoading = false) }
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

            is Event.SelectWho -> {
                if (event.rival != viewState.value.showRival) {
                    setState { copy(showRival = event.rival) }
                    // Дни/недели/месяцы у обоих одни и те же — листание сохраняем;
                    // сессии у соперника свои — для SESSION начинаем с последней.
                    val period = viewState.value.selectedPeriod
                    val offset = if (period == PeriodFilter.SESSION) 0
                                 else viewState.value.analytics?.periodOffset ?: 0
                    loadAnalytics(period, offset)
                }
            }

            is Event.ToggleDiary -> setState { copy(diaryExpanded = !diaryExpanded) }

            is Event.RequestDiaryEntry -> {
                val key = event.key
                val st = viewState.value
                if (key in st.diaryWriting || key in st.diaryTooFew) return
                if (st.diaryWriterDown && !event.retry) return
                if (key in st.diaryFailed && !event.retry) return
                setState { copy(diaryWriting = diaryWriting + key, diaryFailed = diaryFailed - key) }
                viewModelScope.launch {
                    val res = runCatching { rival.writeDayEntry(key) }
                        .getOrDefault(RivalEngine.WriteResult.Failed)
                    setState {
                        when (res) {
                            is RivalEngine.WriteResult.Written -> copy(
                                diaryWriting = diaryWriting - key,
                                diaryLlm     = diaryLlm + (key to res.entry))
                            RivalEngine.WriteResult.TooFew -> copy(
                                diaryWriting = diaryWriting - key,
                                diaryTooFew  = diaryTooFew + key)
                            RivalEngine.WriteResult.Failed -> copy(
                                diaryWriting    = diaryWriting - key,
                                diaryFailed     = diaryFailed + key,
                                diaryWriterDown = true)
                        }
                    }
                }
            }

            is Event.ToggleDiaryDay -> setState {
                val days = if (event.key in expandedDiaryDays)
                    expandedDiaryDays - event.key else expandedDiaryDays + event.key
                copy(expandedDiaryDays = days)
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
