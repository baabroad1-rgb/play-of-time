package ru.iflex.timemvp.features.main.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.BaseViewModel
import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.services.VibrationService
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.core.utils.ButtonResultType
import ru.iflex.timemvp.core.utils.StopWatchCycle
import ru.iflex.timemvp.core.utils.Quest
import ru.iflex.timemvp.core.utils.VictoryCondition
import ru.iflex.timemvp.core.utils.formatTime
import ru.iflex.timemvp.core.utils.getCurrentQuest
import ru.iflex.timemvp.core.utils.getLocalTimeInMillis
import ru.iflex.timemvp.core.utils.getTasksCountForLevel
import ru.iflex.timemvp.core.utils.maxAttemptDuration
import ru.iflex.timemvp.core.utils.minAttemptDuration
import ru.iflex.timemvp.core.utils.sessionAutoCloseAttempts
import ru.iflex.timemvp.core.utils.orientationMode
import ru.iflex.timemvp.core.utils.portraitMode
import ru.iflex.timemvp.features.main.ui.MainContract.Effect
import ru.iflex.timemvp.features.main.ui.MainContract.Event
import ru.iflex.timemvp.features.main.ui.MainContract.Event.GreetingView
import ru.iflex.timemvp.features.main.ui.MainContract.Event.StopWatchButton
import ru.iflex.timemvp.features.main.ui.MainContract.State
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.features.main.domain.QuestCompletionStats
import ru.iflex.timemvp.features.main.domain.usecase.GetLogAttemptsUseCase
import ru.iflex.timemvp.features.main.domain.usecase.MainMetricsUseCase
import ru.iflex.timemvp.features.main.domain.usecase.SerenityBalanceUseCase
import ru.iflex.timemvp.features.stats.domain.usecase.YourAnrUseCase
import ru.iflex.timemvp.ui_lib.stop_watch.StopWatch
import ru.iflex.timemvp.core.utils.CheerUpMessages
import ru.iflex.timemvp.core.utils.CheerUpTrigger
import ru.iflex.timemvp.core.utils.ErrorPattern
import ru.iflex.timemvp.use_case.AddAttemptUseCase
import ru.iflex.timemvp.use_case.DetectCheerUpTriggerUseCase
import ru.iflex.timemvp.use_case.DetectErrorPatternsUseCase
import ru.iflex.timemvp.use_case.AddSessionUseCase
import ru.iflex.timemvp.use_case.ContextUseCase
import ru.iflex.timemvp.use_case.GetCurrentSessionIdUseCase
import ru.iflex.timemvp.use_case.SaveScmAfterAttemptUseCase
import ru.iflex.timemvp.use_case.MarkSessionQuestCompleteUseCase
import ru.iflex.timemvp.use_case.UpdateSessionUseCase
import java.text.DecimalFormat
import kotlin.math.round

class MainViewModel(
    private val context: ContextUseCase,
    private val localStorage: LocalStorageService,
    private val addAttempt: AddAttemptUseCase,
    private val addSession: AddSessionUseCase,
    private val getCurrentSessionId: GetCurrentSessionIdUseCase,
    private val updateSession: UpdateSessionUseCase,
    private val vibrationService: VibrationService,
    private val yourAnrUseCase: YourAnrUseCase,
    private val saveScmAfterAttempt: SaveScmAfterAttemptUseCase,
    private val mainMetricsUseCase: MainMetricsUseCase,
    private val serenityBalanceUseCase: SerenityBalanceUseCase,
    private val getLogAttempts: GetLogAttemptsUseCase,
    private val markSessionQuestComplete: MarkSessionQuestCompleteUseCase,
    private val detectErrorPatterns: DetectErrorPatternsUseCase,
    private val detectCheerUpTrigger: DetectCheerUpTriggerUseCase,
    ) : BaseViewModel<Event, State, Effect>() {
    override fun setInitialState() = State()

    /** Триггеры, уже показанные в текущей сессии — не показываем повторно */
    private val shownCheerUps = mutableSetOf<CheerUpTrigger>()


    private var stopWatch: StopWatch = StopWatch(
        localStorage = localStorage,
        context = context,
        setTime = { timeText, time ->
            setState {
                copy(
                    time = timeText,
                    isStopButtonActive = time >= minAttemptDuration
                )
            }

            if (time >= maxAttemptDuration) {
//                setState {
//                    copy(
//                        stopWatchState = StopWatchCycle.ON_STOP,
//                        isStopButtonActive = false
//                    )
//                }

                setState {
                    copy(
                        stopWatchState = StopWatchCycle.ON_START,
                        time = timePlaceholder(),
                        startTime = -1L,
                        endTime = -1L,
                        isStopButtonActive = false
                    )
                }

            }
        },
        vibrationService = vibrationService
    )

    private val timePlaceholder = {
        val accuracy =
            localStorage.getAccuracy() //здесь спецом отдельной строкой, иначе не пересчитывается сразу и не перерисовывается
        when (accuracy) {
            Accuracy.SECONDS -> "0"
            Accuracy.DECISECONDS -> "0.0"
            Accuracy.CENTISECONDS -> "0.00"
            Accuracy.MILLISECONDS -> "0.000"
        }
    }

    private var currentAimTime = localStorage.getAimTime()
    private val decimalFormat = DecimalFormat("#.00")
    private val audioManager =
        context.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager


    private val aimTime = {
        val accuracy = localStorage.getAccuracy()
        when (accuracy) {
            Accuracy.SECONDS -> {
                currentAimTime / 1000
            }

            Accuracy.DECISECONDS -> {
                String.format("%,.1f", currentAimTime / 1000.0).replace(",", ".")
            }

            Accuracy.CENTISECONDS -> {
                String.format("%,.2f", currentAimTime / 1000.0).replace(",", ".")
            }

            Accuracy.MILLISECONDS -> {
                String.format("%,.3f", currentAimTime / 1000.0).replace(",", ".")
            }
        }
    }

    private val taskDescription = {
        val levelId = localStorage.getCurrentLevelId()
        val taskId  = localStorage.getCurrentTaskId()
        val quest   = getCurrentQuest(levelId, taskId)

        // Подставляем реальные N и t в текст квеста
        val effectiveN = if (localStorage.getQuestCurrentN() == -1)
            quest.maxAttemptsDefault else localStorage.getQuestCurrentN()
        val effectiveT = localStorage.getQuestCurrentToleranceMs()  // мс

        val questText = when {
            // Level 3: T±t квесты — показываем цель "T ± t.ttt"
            quest.adaptiveT && effectiveT > 0 -> {
                val tStr = "%.3f".format(effectiveT / 1000.0)  // e.g. "0.002"
                val nPart = if (effectiveN != null) " in $effectiveN attempts" else ""
                val victoryPart = when (quest.victory.victoryCount) {
                    1 -> ""
                    2 -> if (quest.victory.victoryCondition == VictoryCondition.ROW) " two times in a row" else " two times"
                    else -> if (quest.victory.victoryCondition == VictoryCondition.ROW) " three times in a row" else " three times"
                }
                "${aimTime()} ± $tStr$victoryPart$nPart"
            }
            // IN_N квесты — показываем реальное N
            quest.victory.victoryCondition == VictoryCondition.IN_N && effectiveN != null -> {
                val countWord = if (quest.victory.victoryCount == 2) "two" else "three"
                "${aimTime()},\n$countWord times in $effectiveN attempts"
            }
            // Остальные — как раньше
            else -> {
                val comma = if (quest.victory.taskDescription == "") "" else ",\n"
                "${aimTime()}$comma${quest.victory.taskDescription}"
            }
        }

        context.context.getString(
            R.string.task_and_goal_for_quest,
            levelId.toString(), taskId.toString(), getTasksCountForLevel(levelId).toString()
        ) + " " + questText
    }

    private val quest = {
        val levelId = localStorage.getCurrentLevelId()
        val taskId = localStorage.getCurrentTaskId()
        getCurrentQuest(levelId, taskId)
    }

    init {

        with(context()) {
            Intent().also { intent ->
                intent.setAction(orientationMode)
                intent.putExtra(orientationMode, portraitMode)
                sendBroadcast(intent)
            }
        }

        viewModelScope.launch {
            val yourAnr = decimalFormat.format(yourAnrUseCase())
            setState {
                val accuracy = localStorage.getAccuracy()
                val selectedHand = localStorage.getSelectedHand()

                copy(
                    title = context.context.getString(R.string.main_screen_title),
                    yourAnr = yourAnr,
                    attempts = listOf(),
                    accuracy = accuracy,
                    time = timePlaceholder(),
                    taskTitle = taskDescription(),
                    aimTime = aimTime.toString(),
                    currentQuest = quest(),
                    selectedHand = selectedHand
                )
            }

            // Инициализируем адаптивные N/t, если ещё не записаны
            initQuestParamsIfNeeded()

            // При старте показываем исторические данные за всё время,
            // пока пользователь не сделал первую попытку в текущей сессии
            loadMetrics(PeriodFilter.ALL_TIME, updateLog = false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun handleEvents(event: Event) {
        when (event) {
            StopWatchButton.Press -> {
                when (viewState.value.stopWatchState) {
                    StopWatchCycle.ON_START -> {

                        val startTime = getLocalTimeInMillis()

                        setState {
                            copy(
                                stopWatchState = StopWatchCycle.IS_RUNNING,
                                startTime = startTime
                            )
                        }

                        stopWatch.setState(viewState.value.stopWatchState)
                        stopWatch.start()
                        stopWatch.startMetronome()
                        stopWatch.startVibration()

                        if (viewState.value.currentSession == null) {
                            shownCheerUps.clear() // новая сессия — сбрасываем историю чирапов
                            viewModelScope.launch {
                                addSession(
                                    startTime    = startTime,
                                    questLevelId = localStorage.getCurrentLevelId(),
                                    questTaskId  = localStorage.getCurrentTaskId()
                                )

                                val currentSession = getCurrentSessionId()

                                setState {
                                    copy(
                                        currentSession = currentSession
                                    )
                                }
                            }
                        }

                    }

                    StopWatchCycle.IS_RUNNING -> {
                        val endTime = getLocalTimeInMillis()
                        // Захватываем startTime ДО любого suspend — авто-ресет таймера
                        // может записать -1L в State после delay(), что даст гигантскую длительность
                        val capturedStartTime = viewState.value.startTime
                        val attemptDuration = endTime - capturedStartTime

                        if (attemptDuration < minAttemptDuration) {
                            kotlin.runCatching {
                                ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                    .startTone(ToneGenerator.TONE_PROP_NACK, 200)
                            }
                        } else if (attemptDuration > maxAttemptDuration) {
                            // Попытка слишком длинная — уже обработана авто-ресетом таймера, игнорируем
                        } else {

                            stopWatch.setState(StopWatchCycle.ON_STOP)

                            val formattedTime = formatTime(
                                attemptDuration,
                                localStorage.getAccuracy()
                            )

                            viewModelScope.launch {
                                delay(20)

                                setState {
                                    copy(
                                        stopWatchState = StopWatchCycle.ON_STOP,
                                        endTime = endTime,
                                        time = formattedTime,
                                        isStopButtonActive = false
                                    )
                                }

                                // Ждём создания сессии, если она ещё не создана
                                // (race condition: первый Press запускает addSession асинхронно)
                                val sessionId = viewState.value.currentSession?.id
                                    ?: getCurrentSessionId()?.id
                                    ?: return@launch

                                addAttempt(
                                    sessionId,
                                    capturedStartTime,
                                    endTime,
                                    null
                                )

                                saveScmAfterAttempt()
                                loadMetrics()

                                addAttemptToList(
                                    time           = attemptDuration,
                                    deflection     = null,
                                    yourAnr        = decimalFormat.format(yourAnrUseCase()),
                                    startTimestamp = capturedStartTime,
                                    endTimestamp   = endTime
                                )

                                val currentSession =
                                    viewState.value.currentSession?.copy(endTime = endTime)
                                        ?: return@launch

                                val autoClose = viewState.value.attempts.size >= sessionAutoCloseAttempts
                                    && viewState.value.currentSession != null

                                if (autoClose) {
                                    val cheerEnabled = localStorage.getCheerUpsEnabled()
                                    setState {
                                        copy(
                                            currentSession      = null,
                                            attempts            = listOf(),
                                            displayedAttempts   = listOf(),
                                            // Визуальный сброс экрана
                                            time                = timePlaceholder(),
                                            stopWatchState      = StopWatchCycle.ON_START,
                                            buttonResultType    = ButtonResultType.NONE,
                                            errorPattern        = ErrorPattern.NONE,
                                            startTime           = -1L,
                                            endTime             = -1L,
                                            // CheerUp
                                            cheerUpTrigger      = if (cheerEnabled) CheerUpTrigger.AFTER_SESSION else CheerUpTrigger.NONE,
                                            cheerUpSpecificText = if (cheerEnabled) CheerUpMessages.specificText(CheerUpTrigger.AFTER_SESSION) else "",
                                            cheerUpGeneralText  = ""
                                        )
                                    }
                                } else {
                                    setState { copy(currentSession = currentSession) }
                                }

                                updateSession(currentSession)
                            }
                        }

                    }

                    StopWatchCycle.ON_STOP -> {
                        setState {
                            copy(
                                stopWatchState = StopWatchCycle.ON_START,
                                time = timePlaceholder(),
                                startTime = -1L,
                                endTime = -1L,
                                buttonResultType = ButtonResultType.NONE
                            )
                        }

                        stopWatch.setState(viewState.value.stopWatchState)
                    }
                }
            }

            StopWatchButton.Release -> {}

            is GreetingView.Display -> {
                setState {
                    copy(
                        displayGreetingView = true
                    )
                }
            }

            is GreetingView.Close -> {
                val pending = viewState.value.pendingCheerUpTrigger
                val cheerEnabled = localStorage.getCheerUpsEnabled()
                if (pending != CheerUpTrigger.NONE && cheerEnabled) {
                    setState {
                        copy(
                            displayGreetingView   = false,
                            questCompletionStats  = null,
                            pendingCheerUpTrigger = CheerUpTrigger.NONE,
                            cheerUpTrigger        = pending,
                            cheerUpSpecificText   = CheerUpMessages.specificText(pending),
                            cheerUpGeneralText    = if (CheerUpMessages.hasGeneralPart(pending))
                                                        CheerUpMessages.generalTexts.random()
                                                    else ""
                        )
                    }
                } else {
                    setState {
                        copy(
                            displayGreetingView   = false,
                            questCompletionStats  = null,
                            pendingCheerUpTrigger = CheerUpTrigger.NONE
                        )
                    }
                }
            }

            is Event.SelectHand -> {
                localStorage.putSelectedHand(event.hand)
                setState { copy(selectedHand = event.hand) }
            }

            is Event.SelectPeriod -> {
                setState { copy(periodFilter = event.filter) }
                loadMetrics(event.filter)
            }

            is Event.DismissCheerUp -> {
                setState {
                    copy(
                        cheerUpTrigger      = CheerUpTrigger.NONE,
                        cheerUpSpecificText = "",
                        cheerUpGeneralText  = ""
                    )
                }
            }
        }
    }

    private fun loadMetrics(
        filter: PeriodFilter = viewState.value.periodFilter,
        updateLog: Boolean = true          // false при старте — лог остаётся пустым
    ) {
        viewModelScope.launch {
            val sessionId = viewState.value.currentSession?.id
            val metrics = mainMetricsUseCase(filter, sessionId)
            val (total, todayDelta) = serenityBalanceUseCase()

            // Для Today / All time загружаем попытки из БД;
            // для Session используем in-memory список (он уже обновлён addAttemptToList)
            val logAttempts = when {
                !updateLog           -> viewState.value.displayedAttempts  // не трогаем лог
                filter == PeriodFilter.SESSION -> viewState.value.attempts
                else                 -> getLogAttempts(filter, sessionId)
            }

            setState {
                copy(
                    totalSerenityBalance = total,
                    todaySerenityDelta = todayDelta,
                    redCount = metrics.redCount,
                    blueCount = metrics.blueCount,
                    balanceSide = metrics.balanceSide,
                    trueThreeCount = metrics.trueThreeCount,
                    trueThreeHitRate = metrics.trueThreeHitRate,
                    geomMeanEarlyMs = metrics.geomMeanEarlyMs,
                    geomMeanLateMs = metrics.geomMeanLateMs,
                    geomMeanAbsoluteOffsetMs = metrics.geomMeanAbsoluteOffsetMs,
                    scaleMaxMs = metrics.scaleMaxMs,
                    distribBuckets = metrics.distribBuckets,
                    displayedAttempts = logAttempts
                )
            }
        }
    }

    // ── Адаптивные параметры квеста ──────────────────────────────────────────

    /**
     * Возвращает текущее N для квеста. Если LocalStorage ещё не инициализирован (-1),
     * берём дефолт из Quest и сохраняем.
     */
    private fun getEffectiveN(): Int {
        val stored = localStorage.getQuestCurrentN()
        if (stored == -1) {
            val default = viewState.value.currentQuest.maxAttemptsDefault ?: -1
            localStorage.putQuestCurrentN(default)
            return default
        }
        return stored
    }

    /** Текущий допуск t в мс. */
    private fun getEffectiveToleranceMs(): Int = localStorage.getQuestCurrentToleranceMs()

    /**
     * Инициализирует N и t при первом запуске или после смены квеста.
     * Вызывается один раз из init{}.
     */
    private fun initQuestParamsIfNeeded() {
        if (localStorage.getQuestCurrentN() == -1) {
            val quest = viewState.value.currentQuest
            localStorage.putQuestCurrentN(quest.maxAttemptsDefault ?: -1)
            localStorage.putQuestCurrentToleranceMs(quest.toleranceMsDefault)
            localStorage.putQuestWindowAttempts(0)
            localStorage.putQuestWindowWins(0)
        }
    }

    /**
     * Переводит параметры на следующий квест: сбрасывает N, t и счётчики окна.
     */
    private fun resetQuestAdaptiveParams(newQuest: Quest) {
        localStorage.putQuestCurrentN(newQuest.maxAttemptsDefault ?: -1)
        localStorage.putQuestCurrentToleranceMs(newQuest.toleranceMsDefault)
        localStorage.putQuestWindowAttempts(0)
        localStorage.putQuestWindowWins(0)
    }

    /**
     * Провал N-окна: адаптируем N/t вверх и сбрасываем счётчики.
     */
    private fun onQuestWindowFailure(quest: Quest) {
        if (quest.adaptiveN) {
            val currentN = getEffectiveN()
            localStorage.putQuestCurrentN(if (currentN == -1) 33 else currentN + 33)
        }
        if (quest.adaptiveT) {
            localStorage.putQuestCurrentToleranceMs(getEffectiveToleranceMs() + 1)
        }
        localStorage.putQuestWindowAttempts(0)
        localStorage.putQuestWindowWins(0)
        // Обновляем taskTitle с новыми N/t
        setState { copy(taskTitle = taskDescription()) }
    }

    /**
     * Успех квеста: адаптируем N/t вниз (перед сбросом на новый квест).
     */
    private fun onQuestWindowSuccess(quest: Quest) {
        if (quest.adaptiveN) {
            val minN = quest.maxAttemptsDefault ?: 33
            val newN = maxOf(minN, getEffectiveN() - 33)
            localStorage.putQuestCurrentN(newN)
        }
        if (quest.adaptiveT) {
            localStorage.putQuestCurrentToleranceMs(maxOf(0, getEffectiveToleranceMs() - 1))
        }
        localStorage.putQuestWindowAttempts(0)
        localStorage.putQuestWindowWins(0)
    }

    // ── Основная логика попытки ───────────────────────────────────────────────

    private fun addAttemptToList(
        time: Long,
        deflection: Int?,
        yourAnr: String,
        startTimestamp: Long = 0L,
        endTimestamp: Long = 0L
    ) {
        val attempts = viewState.value.attempts.reversed().toMutableList()
        val error = formatTime(time, localStorage.getAccuracy()).toDouble() - formatTime(
            currentAimTime.toLong(),
            localStorage.getAccuracy()
        ).toDouble()

        val tmpError = when (localStorage.getAccuracy()) {
            Accuracy.SECONDS      -> round(error)
            Accuracy.DECISECONDS  -> round(error * 10.0) / 10.0
            Accuracy.CENTISECONDS -> round(error * 100.0) / 100.0
            Accuracy.MILLISECONDS -> round(error * 1000.0) / 1000.0
        }

        val tmpDeflection = when (localStorage.getAccuracy()) {
            Accuracy.SECONDS      -> deflection?.div(1000)
            Accuracy.DECISECONDS  -> deflection?.div(100)
            Accuracy.CENTISECONDS -> deflection?.div(10)
            Accuracy.MILLISECONDS -> deflection
        }

        // Победа с учётом допуска T±t
        val totalError    = tmpError + (tmpDeflection?.toDouble() ?: 0.0)
        val toleranceMs   = getEffectiveToleranceMs()
        // toleranceMs в мс, totalError в секундах → переводим totalError в мс для сравнения
        val isWin = kotlin.math.abs(totalError * 1000.0) <= toleranceMs + 0.0005

        val resultType = when {
            isWin        -> ButtonResultType.TRUE_THREE
            totalError < 0.0 -> ButtonResultType.EARLY
            else         -> ButtonResultType.LATE
        }

        attempts.add(
            SessionStatisticItemState(
                number         = attempts.size.toLong() + 1,
                time           = formatTime(time, localStorage.getAccuracy()),
                error          = tmpError,
                deflection     = tmpDeflection,
                isMaximum      = null,
                isMinimum      = null,
                hand           = localStorage.getSelectedHand().value,
                isWin          = isWin,
                startTimestamp = startTimestamp,
                endTimestamp   = endTimestamp
            )
        )

        if (attempts.size > 1) {
            attempts.forEach { it.isMinimum = null }
            attempts.forEach { it.isMaximum = null }
            attempts.minBy { it.error }.apply { isMinimum = true }
            attempts.maxBy { it.error }.apply { isMaximum = true }
        }

        val reversedAttempts = attempts.reversed().toList()

        // Детектируем паттерн ошибок на основе хронологического списка (attempts = reversed)
        val errorsChronological = attempts.map { it.error }
        val detectedPattern = detectErrorPatterns(errorsChronological)

        // Детектируем чирап-триггер (не повторяем уже показанные в этой сессии)
        val cheerTrigger = if (localStorage.getCheerUpsEnabled()) {
            // Паузы между попытками (хронологический порядок, как в attempts)
            // pause[i] = startTimestamp[i+1] - endTimestamp[i]
            val pausesMs: List<Long> = (1 until attempts.size).map { i ->
                val prev = attempts[i - 1]
                val curr = attempts[i]
                if (prev.endTimestamp > 0L && curr.startTimestamp > 0L)
                    curr.startTimestamp - prev.endTimestamp
                else 0L
            }

            val patternTrigger = detectCheerUpTrigger(errorsChronological)
                .takeIf { it != CheerUpTrigger.NONE && it !in shownCheerUps }

            val pauseTrigger = detectCheerUpTrigger.detectPauses(errorsChronological, pausesMs)
                .takeIf { it != CheerUpTrigger.NONE && it !in shownCheerUps }

            // Паттерн-триггеры имеют приоритет над паузо-триггерами
            patternTrigger ?: pauseTrigger
        } else null

        if (cheerTrigger != null) {
            shownCheerUps.add(cheerTrigger)
            val randomGeneral = if (CheerUpMessages.hasGeneralPart(cheerTrigger))
                CheerUpMessages.generalTexts.random()
            else ""
            setState {
                copy(
                    attempts          = reversedAttempts,
                    displayedAttempts = if (periodFilter == PeriodFilter.SESSION) reversedAttempts
                                        else displayedAttempts,
                    yourAnr           = yourAnr,
                    buttonResultType  = resultType,
                    errorPattern      = detectedPattern,
                    cheerUpTrigger      = cheerTrigger,
                    cheerUpSpecificText = CheerUpMessages.specificText(cheerTrigger),
                    cheerUpGeneralText  = randomGeneral
                )
            }
        } else {
            setState {
                copy(
                    attempts          = reversedAttempts,
                    displayedAttempts = if (periodFilter == PeriodFilter.SESSION) reversedAttempts
                                        else displayedAttempts,
                    yourAnr           = yourAnr,
                    buttonResultType  = resultType,
                    errorPattern      = detectedPattern
                )
            }
        }

        // Обновляем счётчики N-окна и проверяем победу / провал
        checkVictory(isWin)
    }

    private fun checkVictory(isWin: Boolean) {
        val currentQuest = viewState.value.currentQuest

        // Обновляем счётчик попыток окна (если квест имеет N-лимит)
        if (currentQuest.maxAttemptsDefault != null) {
            val windowAttempts = localStorage.getQuestWindowAttempts() + 1
            localStorage.putQuestWindowAttempts(windowAttempts)
            if (isWin) {
                localStorage.putQuestWindowWins(localStorage.getQuestWindowWins() + 1)
            }
        }

        // Считаем кол-во побед согласно условию
        val allAttempts = viewState.value.attempts.sortedBy { it.number }
        val winAttempts = allAttempts.filter { it.isWin }

        val winsCount = when (currentQuest.victory.victoryCondition) {

            VictoryCondition.SESSION -> winAttempts.size

            VictoryCondition.ROW -> {
                // Максимальная серия подряд идущих побед (по номеру попытки)
                var maxRow = 0
                var curRow = 0
                var lastNum = -100L
                allAttempts.forEach { item ->
                    if (item.isWin) {
                        curRow = if (item.number == lastNum + 1L) curRow + 1 else 1
                        if (curRow > maxRow) maxRow = curRow
                    } else {
                        curRow = 0
                    }
                    lastNum = item.number
                }
                maxRow
            }

            VictoryCondition.IN_N -> {
                // Побед в текущем N-окне (из LocalStorage)
                localStorage.getQuestWindowWins()
            }
        }

        val questWon = winsCount >= currentQuest.victory.victoryCount

        if (questWon) {
            // Помечаем сессию как «квест пройден» (для истории / короны в дереве)
            viewState.value.currentSession?.id?.let { sid ->
                viewModelScope.launch { markSessionQuestComplete(sid) }
            }

            // Собираем статистику сессии до сброса списка попыток
            val sessionAttempts = viewState.value.attempts.size
            val sessionWins = viewState.value.attempts.count { it.isWin }
            val completionStats = QuestCompletionStats(
                levelId              = currentQuest.levelId,
                taskId               = currentQuest.taskId,
                sessionAttempts      = sessionAttempts,
                sessionWins          = sessionWins,
                congratulationText   = currentQuest.congratulationDescription ?: ""
            )

            // Адаптируем N/t вниз при успехе
            onQuestWindowSuccess(currentQuest)

            val isLastLevel = currentQuest.levelId >= 3
            val isLastTask  = currentQuest.taskId >= getTasksCountForLevel(currentQuest.levelId)
            val isAllDone   = isLastLevel && isLastTask

            val nextQuest: Quest? = when {
                isAllDone  -> null
                isLastTask -> getCurrentQuest(currentQuest.levelId + 1, 1)
                else       -> getCurrentQuest(currentQuest.levelId, currentQuest.taskId + 1)
            }

            if (nextQuest != null) {
                localStorage.putCurrentTaskId(nextQuest.taskId)
                localStorage.putCurrentLevelId(nextQuest.levelId)
                localStorage.putAccuracy(nextQuest.accuracy)
                resetQuestAdaptiveParams(nextQuest)
            }

            val pendingTrigger = when {
                isAllDone  -> CheerUpTrigger.AFTER_GAME
                isLastTask -> CheerUpTrigger.AFTER_LEVEL
                else       -> CheerUpTrigger.AFTER_QUEST
            }
            setState {
                val accuracy = localStorage.getAccuracy()
                copy(
                    attempts              = listOf(),
                    displayedAttempts     = listOf(),
                    displayGreetingView   = true,
                    greetingViewText      = completionStats.congratulationText,
                    questCompletionStats  = completionStats,
                    accuracy              = accuracy,
                    time                  = timePlaceholder(),
                    taskTitle             = taskDescription(),
                    currentQuest          = quest(),
                    stopWatchState        = StopWatchCycle.ON_START,
                    buttonResultType      = ButtonResultType.NONE,
                    currentSession        = null,
                    pendingCheerUpTrigger = pendingTrigger
                )
            }
            return
        }

        // Проверка провала N-окна (только для квестов с лимитом)
        if (currentQuest.maxAttemptsDefault != null) {
            val windowAttempts = localStorage.getQuestWindowAttempts()
            val currentN       = getEffectiveN()
            if (currentN > 0 && windowAttempts >= currentN) {
                onQuestWindowFailure(currentQuest)
            }
        }
    }
}
