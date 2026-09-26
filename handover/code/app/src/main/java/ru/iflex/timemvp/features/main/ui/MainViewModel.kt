package ru.iflex.timemvp.features.main.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
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
import ru.iflex.timemvp.features.main.domain.FailCardData
import ru.iflex.timemvp.features.main.domain.QuestCompletionStats
import ru.iflex.timemvp.features.main.domain.usecase.GetLogAttemptsUseCase
import ru.iflex.timemvp.features.main.domain.usecase.MainMetricsUseCase
import ru.iflex.timemvp.features.main.domain.usecase.SerenityBalanceUseCase
import ru.iflex.timemvp.features.stats.domain.usecase.YourAnrUseCase
import ru.iflex.timemvp.ui_lib.stop_watch.StopWatch
import ru.iflex.timemvp.core.utils.CheerUpMessages
import ru.iflex.timemvp.core.utils.CheerUpTrigger
import ru.iflex.timemvp.core.utils.ErrorPattern
import ru.iflex.timemvp.core.utils.describePhase
import ru.iflex.timemvp.use_case.AddAttemptUseCase
import ru.iflex.timemvp.use_case.DetectErrorPatternsUseCase
import ru.iflex.timemvp.use_case.GetQuestSummaryUseCase
import ru.iflex.timemvp.use_case.GetSessionComparisonUseCase
import ru.iflex.timemvp.use_case.AddSessionUseCase
import ru.iflex.timemvp.use_case.ContextUseCase
import ru.iflex.timemvp.use_case.GetCurrentSessionIdUseCase
import ru.iflex.timemvp.use_case.SaveScmAfterAttemptUseCase
import ru.iflex.timemvp.use_case.MarkSessionQuestCompleteUseCase
import ru.iflex.timemvp.use_case.UpdateSessionUseCase
import androidx.compose.runtime.State as ComposeState
import androidx.compose.runtime.mutableStateOf
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val getQuestSummary: GetQuestSummaryUseCase,
    private val getSessionComparison: GetSessionComparisonUseCase,
    private val timeDatabase: ru.iflex.timemvp.database.TimeDatabase,
    ) : BaseViewModel<Event, State, Effect>() {
    override fun setInitialState() = State()

    /** Триггеры, уже показанные в текущей сессии — не показываем повторно */
    private val shownCheerUps = mutableSetOf<CheerUpTrigger>()


    // TEMP DEBUG (rolled-back after quest tuning is finalized — see CLAUDE.md backlog):
    // When the user selects the LEFT-hand toggle, the stopwatch auto-stops at the
    // current aim time (3.000 s) so the quest-level logic can be exercised without
    // human reaction-time jitter.
    private var autoStopFired: Boolean = false

    /** SystemClock.uptimeMillis() момента START-тапа (kernel-level timestamp касания).
     *  Используется для точного расчёта attemptDuration = endUptime - startUptime,
     *  который монотонен и НЕ подвержен NTP-скачкам wall-clock. state.startTime
     *  продолжает хранить wall-clock для БД (человеко-читаемая метка попытки). */
    private var attemptStartUptime: Long = -1L

    // ── Отдельный state для отображения секундомера (Alex 2026-07-14) ────────
    // Тик обновляет ТОЛЬКО этот state, а не общий viewState. MainScreen НЕ
    // читает stopwatchTime.value в своём scope (передаёт State<String> объектом
    // вниз в StopWatchButton), поэтому 720-строчный composable не пересобирается
    // 60 раз в секунду — только StopwatchTimeText внутри кнопки.
    private val _stopwatchTime = mutableStateOf(defaultPlaceholderText())
    val stopwatchTime: ComposeState<String> = _stopwatchTime

    /** Начальный placeholder до первого init'а (заменится в init через timePlaceholder()). */
    private fun defaultPlaceholderText(): String = "0.000"

    /** Job создания новой сессии в БД (запущен в ON_START). STOP-обработчик дожидается
     *  его перед сохранением попытки — иначе при быстром STOP попытка пишется в БД с
     *  sessionId предыдущей сессии (фоллбэк getCurrentSessionId() возвращает то, что
     *  УЖЕ есть в БД). Особенно заметно на L2/L3 после субквестных контракций, где
     *  сессия сбрасывается и тут же создаётся новая. */
    private var pendingAddSession: kotlinx.coroutines.Job? = null

    // ── Play-time трекер ─────────────────────────────────────────────────────
    /** true, пока MainScreen находится в состоянии RESUMED (виден и активен). */
    private var mainScreenActive: Boolean = false
    /** Timestamp начала текущего тика (мс, 0 = не тикает). Тикер запущен, когда
     *  ОДНОВРЕМЕННО mainScreenActive == true И currentSession != null.
     *  Flush на ON_PAUSE и на каждом переходе currentSession → null. */
    private var tickStartMs: Long = 0L

    /** Запускает тикер, если MainScreen активен и сессия открыта.
     *  Идемпотентно (если уже тикает — ничего не делает). */
    private fun startTickIfEligible() {
        if (mainScreenActive && viewState.value.currentSession != null && tickStartMs == 0L) {
            tickStartMs = getLocalTimeInMillis()
        }
    }

    /** Останавливает тикер и плюсует натиканное к activeTimeMs текущей сессии.
     *  Идемпотентно (если не тикал — no-op). Пишет в БД асинхронно. */
    private fun flushTick() {
        if (tickStartMs == 0L) return
        val elapsed = getLocalTimeInMillis() - tickStartMs
        tickStartMs = 0L
        if (elapsed <= 0L) return
        val sess = viewState.value.currentSession ?: return
        val updated = sess.copy(activeTimeMs = sess.activeTimeMs + elapsed)
        setState { copy(currentSession = updated) }
        viewModelScope.launch { updateSession(updated) }
    }

    private var stopWatch: StopWatch = StopWatch(
        localStorage = localStorage,
        context = context,
        setTime = { timeText, time ->
            // ГОРЯЧИЙ ПУТЬ: тик каждые 16 мс.
            // Записываем время в изолированный state — MainScreen не читает
            // stopwatchTime.value в своём scope → не пересобирается. Только
            // StopwatchTimeText внутри кнопки видит смену.
            _stopwatchTime.value = timeText

            // TEMP DEBUG — left-hand auto-stop at the aim time.
            // Значения touchWall/touchUptime не используются (autoStopFired
            // ставит duration = currentAimTime), но сигнатура требует пары —
            // передаём текущие метки времени обоих часов.
            if (!autoStopFired &&
                viewState.value.selectedHand == ru.iflex.timemvp.core.utils.Hand.LEFT &&
                viewState.value.stopWatchState == StopWatchCycle.IS_RUNNING &&
                time >= currentAimTime
            ) {
                autoStopFired = true
                setEvent(
                    StopWatchButton.Press(
                        touchWallMs = getLocalTimeInMillis(),
                        touchUptimeMs = android.os.SystemClock.uptimeMillis()
                    )
                )
            }

            if (time >= maxAttemptDuration) {
                _stopwatchTime.value = timePlaceholder()
                setState {
                    copy(
                        stopWatchState = StopWatchCycle.ON_START,
                        startTime = -1L,
                        endTime = -1L
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
                // Формат «±» должен соответствовать точности уровня:
                //   MILLISECONDS  → 0.001 / 0.002 / 0.003
                //   CENTISECONDS  → 0.01  / 0.02  / 0.03
                //   DECISECONDS   → 0.1   / 0.2   / 0.3
                val tStr = when (quest.accuracy) {
                    Accuracy.SECONDS      -> "%.0f".format(effectiveT / 1000.0)
                    Accuracy.DECISECONDS  -> "%.1f".format(effectiveT / 1000.0)
                    Accuracy.CENTISECONDS -> "%.2f".format(effectiveT / 1000.0)
                    Accuracy.MILLISECONDS -> "%.3f".format(effectiveT / 1000.0)
                }
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

        // Заголовок переключается на "SubQuest of Y of Z" когда квест на индульгентных
        // параметрах (N > baseline или t > baseline). Сигналит игроку, что текущая
        // победа вернёт его к родительскому (менее индульгентному) квесту, а не к
        // следующему квесту в списке.
        val headerRes = if (isInSubquest())
            R.string.task_and_goal_for_subquest
        else
            R.string.task_and_goal_for_quest

        context.context.getString(
            headerRes,
            levelId.toString(), taskId.toString(), getTasksCountForLevel(levelId).toString()
        ) + " " + questText
    }

    /**
     * Игрок находится в субквесте (индульгентной версии основного квеста),
     * если N или t отклонены от baseline. Используется для смены заголовка
     * и метки в логе сессий.
     */
    private fun isInSubquest(): Boolean {
        val q = quest()   // читаем свежий из LocalStorage, см. currentWindowLimit()
        val baseN = q.maxAttemptsDefault ?: Int.MAX_VALUE
        val baseT = q.toleranceMsDefault
        // Внимание: getEffectiveN/getEffectiveToleranceMs читают state.currentQuest,
        // который на init может ещё не быть свежим квестом → значения не совпадают
        // с реально сохранённым. Читаем напрямую из LocalStorage.
        val storedN = localStorage.getQuestCurrentN()
        val effectiveN = if (storedN == -1) baseN else storedN
        val storedT = localStorage.getQuestCurrentToleranceMs()
        val onIndulgedN = q.adaptiveN && effectiveN > baseN
        val onIndulgedT = q.adaptiveT && storedT > baseT
        return onIndulgedN || onIndulgedT
    }

    private val quest = {
        val levelId = localStorage.getCurrentLevelId()
        val taskId = localStorage.getCurrentTaskId()
        getCurrentQuest(levelId, taskId)
    }

    /** Форматирует aim time в строку с точностью текущего уровня квеста. */
    private fun aimTimeFormatted(): String = when (localStorage.getAccuracy()) {
        Accuracy.SECONDS      -> "${currentAimTime / 1000}"
        Accuracy.DECISECONDS  -> String.format("%,.1f", currentAimTime / 1000.0).replace(",", ".")
        Accuracy.CENTISECONDS -> String.format("%,.2f", currentAimTime / 1000.0).replace(",", ".")
        Accuracy.MILLISECONDS -> String.format("%,.3f", currentAimTime / 1000.0).replace(",", ".")
    }

    /** Форматирует ± допуск. Возвращает "" если t = 0 (baseline). */
    private fun toleranceFormatted(): String {
        val t = getEffectiveToleranceMs()
        if (t <= 0) return ""
        // NBSP после "±" — значение допуска не должно разрываться на две строки
        // в инфо-строке QuestProgress (переносы разрешены только на "·").
        return when (localStorage.getAccuracy()) {
            Accuracy.SECONDS      -> "± ${t / 1000}"
            Accuracy.DECISECONDS  -> "± " + String.format("%,.1f", t / 1000.0).replace(",", ".")
            Accuracy.CENTISECONDS -> "± " + String.format("%,.2f", t / 1000.0).replace(",", ".")
            Accuracy.MILLISECONDS -> "± " + String.format("%,.3f", t / 1000.0).replace(",", ".")
        }
    }

    /** Актуальное N-окно (33/66/99) для текущего квеста; null = без лимита.
     *  Читаем quest() (свежий из LocalStorage), а не state.currentQuest — на
     *  init state.currentQuest ещё содержит первое из questList, из-за чего
     *  расчёт возвращал бы null для любого стартового state.
     */
    private fun currentWindowLimit(): Int? {
        val q = quest()
        return if (q.maxAttemptsDefault == null) null else {
            val baseline = q.maxAttemptsDefault
            val stored = localStorage.getQuestCurrentN()
            if (stored == -1) baseline else stored
        }
    }

    init {

        // Welcome-окно первого запуска: показываем один раз, пока пользователь
        // не закрыл его кнопкой (Event.DismissWelcome пишет флаг в LocalStorage).
        if (!localStorage.isWelcomeShown()) {
            setState { copy(showWelcome = true) }
        }

        with(context()) {
            Intent().also { intent ->
                intent.setAction(orientationMode)
                intent.putExtra(orientationMode, portraitMode)
                sendBroadcast(intent)
            }
        }

        viewModelScope.launch {
            // Форсируем accuracy = accuracy текущего квеста. Раньше localStorage.accuracy
            // мог остаться от предыдущего уровня (например CENTI после L2, а текущий
            // квест уже L1 после ре-стартапа / импорта БД), из-за чего лог показывал
            // ошибки в сотых, хотя aim рисовался в десятых. Все downstream-места
            // (loadMetrics, GetLogAttempts, formatTime) читают localStorage → синк
            // здесь исправляет весь визуальный рассинхрон разом.
            val questNow = getCurrentQuest(
                localStorage.getCurrentLevelId(),
                localStorage.getCurrentTaskId()
            )
            if (localStorage.getAccuracy() != questNow.accuracy) {
                localStorage.putAccuracy(questNow.accuracy)
            }

            val yourAnr = decimalFormat.format(yourAnrUseCase())
            setState {
                val accuracy = localStorage.getAccuracy()
                val selectedHand = localStorage.getSelectedHand()
                // На главном экране ВСЕГДА стартуем с фильтра TODAY. Так пользователь
                // при каждом открытии видит статистику за сегодня. Значение пишем в
                // общий LocalStorage, чтобы StatsViewModel показал тот же период.
                // Пользователь может переключить фильтр вручную в пределах сессии,
                // но при следующем открытии главного он снова сбросится на TODAY.
                localStorage.putPeriodFilter(PeriodFilter.TODAY)
                val startPeriod = PeriodFilter.TODAY

                // Инициализируем placeholder времени в изолированном state
                _stopwatchTime.value = timePlaceholder()

                copy(
                    yourAnr = yourAnr,
                    attempts = listOf(),
                    accuracy = accuracy,
                    taskTitle = taskDescription(),
                    questAimStr = aimTimeFormatted(),
                    questToleranceStr = toleranceFormatted(),
                    questIsSubquest = isInSubquest(),
                    questWindowLimit = currentWindowLimit(),
                    currentQuest = quest(),
                    selectedHand = selectedHand,
                    periodFilter = startPeriod
                )
            }

            // Инициализируем адаптивные N/t, если ещё не записаны
            initQuestParamsIfNeeded()

            // Восстанавливаем «в-памяти» сессию после возврата с другого экрана:
            // если последняя сессия в БД ещё не закрыта (квест не пройден, попыток < лимита,
            // И сессия начата СЕГОДНЯ — после полуночи неоконченная сессия не подхватывается,
            // следующее открытие приложения начнёт новую сессию с #1).
            runCatching {
                val latest = getCurrentSessionId()
                val startedToday = latest?.let { isSameCalendarDay(it.startTime, getLocalTimeInMillis()) } ?: false
                // Восстанавливаем ТОЛЬКО если сессия принадлежит текущему квесту.
                // Иначе после advance L1.1 → L1.2 (и рестарта / навигации), init
                // подтягивал L1.1-попытки в state.attempts, а QuestProgress для L1.2
                // считал их своими wins — Alex видел "1/2" до первого L1.2 RUN.
                // Также страхует от случая, когда markSessionQuestComplete не отработал
                // до updateSession и в БД isQuestComplete осталось false.
                val questMatch = latest != null &&
                    latest.questLevelId == localStorage.getCurrentLevelId() &&
                    latest.questTaskId  == localStorage.getCurrentTaskId()
                if (latest != null && !latest.isQuestComplete && startedToday && questMatch) {
                    val raw = getLogAttempts(PeriodFilter.SESSION, latest.id)
                    // One session = one N-window. Limit = current effective N
                    // (33 / 66 / 99), not the indulgence cap.
                    val limit = getEffectiveN().takeIf { it > 0 } ?: sessionAutoCloseAttempts
                    val attemptsCount = raw.count { !it.isSessionHeader }
                    if (attemptsCount in 1 until limit) {
                        // Перенумеровываем 1..N. `raw` — newest-first; oldest = последний.
                        // state.attempts ОБЯЗАН быть newest-first (это контракт всего
                        // остального кода) — иначе addAttemptToList реверсит уже
                        // неверный порядок и нумерация плывёт.
                        val onlyAttempts = raw.filter { !it.isSessionHeader }  // newest-first
                        val total = onlyAttempts.size
                        val restoredAttempts = onlyAttempts.mapIndexed { newestFirstIdx, item ->
                            // Самый старый attempt получает #1, самый новый — #total.
                            item.copy(number = (total - newestFirstIdx).toLong())
                        }
                        setState {
                            copy(
                                currentSession    = latest,
                                attempts          = restoredAttempts,
                                logBuffer         = restoredAttempts,
                                displayedAttempts = restoredAttempts
                            )
                        }
                        // Синхронизируем counters окна с реально восстановленным числом
                        // попыток и побед. Иначе если до перезапуска counter ушёл вперёд
                        // (DB-write одной из последних попыток не зафиксировался, либо
                        // навигация туда-обратно сбила учёт) — failure-чек сработает на
                        // N−1 вместо N. См. жалобу Alex 2026-06-28 «закончился на 32».
                        localStorage.putQuestWindowAttempts(restoredAttempts.size)
                        localStorage.putQuestWindowWins(restoredAttempts.count { it.isWin })
                        // Восстанавливаем worst-win трекер — считаем max |error| только
                        // по попыткам ТЕКУЩЕГО trailing streak'а (от новейшей назад до
                        // первого промаха). restoredAttempts newest-first → takeWhile
                        // { isWin } возвращает ровно этот streak. Если последний
                        // attempt был промахом, streak пуст → worstErrMs = 0.
                        // Раньше считали по всем победам окна, из-за чего ранняя
                        // in-tolerance победа сохранялась как "worst" и портила
                        // baseline-quality проверку в Rule 2.
                        val trailingStreak = restoredAttempts.takeWhile { it.isWin }
                        val worstErrMs = trailingStreak.maxOfOrNull {
                            kotlin.math.abs(it.error * 1000.0).toInt()
                        } ?: 0
                        localStorage.putQuestWindowMaxErrorMs(worstErrMs)
                        // lastWinIndex = позиция последней победы в окне (нумерация 1..N
                        // по порядку попыток, как считается в checkVictory).
                        val sortedOldestFirst = restoredAttempts.sortedBy { it.number }
                        val lastWinIdx = sortedOldestFirst.indexOfLast { it.isWin }
                            .let { if (it >= 0) it + 1 else 0 }
                        localStorage.putQuestWindowLastWinIndex(lastWinIdx)
                    }
                }
            }

            // Главный экран зафиксирован на TODAY (переключатель периода убран
            // 2026-07-11) — метрики и лог всегда за сегодня, независимо от периода,
            // выбранного на аналитике. updateLog=true — иначе гистограмма и геом
            // средние считались бы по периоду, а Σtries/лог — от restored-сессии.
            loadMetrics(PeriodFilter.TODAY, updateLog = true)
        }
    }

    /**
     * Прямой вход для тапа секундомера (Alex 2026-07-14).
     * Обходит MutableSharedFlow из BaseViewModel: MainScreen вызывает нас
     * СИНХРОННО из pointerInput-корутины, без tryEmit/collect. На загруженном
     * Main-диспетчере это ещё один пропущенный round-trip между касанием
     * сенсора и записью endTime. handleEvents для того же события тоже
     * работает (используется TEMP DEBUG auto-stop'ом), логика одна.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun onStopwatchPress(touchWallMs: Long, touchUptimeMs: Long) {
        handleEvents(StopWatchButton.Press(touchWallMs, touchUptimeMs))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun handleEvents(event: Event) {
        when (event) {
            is StopWatchButton.Press -> {
                // touchWallMs / touchUptimeMs захвачены в pointerInput В МОМЕНТ
                // касания сенсора (PointerInputChange.uptimeMillis = kernel-level
                // timestamp). Compose input dispatch и SharedFlow-очередь между
                // касанием и хендлером НЕ вносят задержку в записанный момент.
                //
                // Длительность попытки считается ТОЛЬКО в uptime (монотонный,
                // не подвержен NTP-скачкам). Wall-clock хранится в БД для
                // читаемой временной метки.
                val touchWallMs = event.touchWallMs
                val touchUptimeMs = event.touchUptimeMs
                when (viewState.value.stopWatchState) {
                    StopWatchCycle.ON_START -> {

                        val startTime = touchWallMs
                        attemptStartUptime = touchUptimeMs
                        autoStopFired = false  // TEMP DEBUG — reset per attempt for LEFT-hand auto-stop

                        // ── Батченное обновление state (Alex 2026-07-13) ──────────
                        // Раньше было три раздельных setState { copy(...) }:
                        //   1) stopWatchState + startTime + transitionMessage
                        //   2) currentSession = null + attempts = [] (если mismatch)
                        //   3) currentSession = X (после addSession, async)
                        // Compose скипает рекомпозицию только если State стабилен,
                        // но наш MainContract.State содержит List-поля →
                        // «unstable» → каждый setState приводит к полной
                        // рекомпозиции всего дерева MainScreen. Три пасса × ~8 мс
                        // на entry-level телефоне = 20-30 мс лага кнопки.
                        //
                        // Здесь синхронно считаем questMismatch и ОДНИМ setState'ом
                        // обновляем всё сразу. Async addSession остаётся отдельным
                        // сhopping'ом, но он уже не блокирует восприятие кнопки.
                        val sess = viewState.value.currentSession
                        val questMismatch = sess != null && (
                            sess.questLevelId != localStorage.getCurrentLevelId() ||
                            sess.questTaskId  != localStorage.getCurrentTaskId()
                        )

                        setState {
                            copy(
                                stopWatchState    = StopWatchCycle.IS_RUNNING,
                                startTime         = startTime,
                                currentSession    = if (questMismatch) null else currentSession,
                                attempts          = if (questMismatch) listOf() else attempts
                            )
                        }

                        stopWatch.setState(StopWatchCycle.IS_RUNNING)
                        // Передаём startUptime — тик считает elapsed через
                        // SystemClock.uptimeMillis (монотонный) с drift-корректным
                        // расписанием (см. StopWatch.start). Видимый счётчик
                        // синхронизирован с моментом касания сенсора.
                        stopWatch.start(touchUptimeMs)
                        stopWatch.startMetronome()
                        stopWatch.startVibration()

                        if (viewState.value.currentSession == null) {
                            shownCheerUps.clear() // новая сессия — сбрасываем историю чирапов
                            // Сохраняем Job — STOP подождёт его перед addAttempt,
                            // чтобы попытка точно попала в новую сессию, а не в предыдущую.
                            pendingAddSession = viewModelScope.launch {
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
                                // Сессия появилась в state — начинаем тикать Play Time
                                // (если MainScreen активен).
                                startTickIfEligible()
                            }
                        }

                    }

                    StopWatchCycle.IS_RUNNING -> {
                        // Захватываем startTime ДО любого suspend — авто-ресет таймера
                        // может записать -1L в State после delay(), что даст гигантскую длительность
                        val capturedStartTime = viewState.value.startTime
                        val capturedStartUptime = attemptStartUptime
                        // Длительность в uptime (kernel-level, монотонная):
                        //   attemptDuration = touchUptime_STOP - touchUptime_START
                        // Обе метки — kernel timestamps касаний сенсора. Ни Compose-input
                        // dispatch, ни SharedFlow-очередь, ни NTP-коррекции wall clock
                        // сюда не попадают. START и STOP симметричны по определению.
                        val attemptDuration: Long = if (autoStopFired) {
                            currentAimTime.toLong()
                        } else if (capturedStartUptime > 0L) {
                            touchUptimeMs - capturedStartUptime
                        } else {
                            // Фоллбэк на wall-clock, если startUptime не был захвачен
                            // (например, попытка началась ДО обновления и restore).
                            touchWallMs - capturedStartTime
                        }
                        // endTime для БД = wall-clock конца попытки.
                        // Строим его от startTime + duration, чтобы attemptDuration
                        // в БД совпадал с расчётным (иначе рассинхрон с DAO-запросами
                        // "clean threes = endTime - startTime == aimTime").
                        val endTime = capturedStartTime + attemptDuration

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

                            // Заморозка отображения — сначала пишем в изолированный
                            // stopwatchTime (мгновенно, минимальная инвалидация:
                            // только StopwatchTimeText), потом остальной state.
                            _stopwatchTime.value = formattedTime
                            // Вибро именно в момент фиксации числа (Alex 2026-07-15):
                            // тактильный сигнал «результат записан». На нажатие STOP
                            // кнопка больше не вибрирует (см. StopWatchButton).
                            vibrationService.heavy()
                            setState {
                                copy(
                                    stopWatchState = StopWatchCycle.ON_STOP,
                                    endTime = endTime
                                )
                            }

                            viewModelScope.launch {

                                // Ждём завершения addSession (запущенного в ON_START), иначе
                                // фоллбэк getCurrentSessionId() вернёт ПРЕДЫДУЩУЮ сессию
                                // и попытка запишется в неё. На L2/L3 после субквестной
                                // контракции это даёт «привидение»: попытка лежит в БД
                                // в старой сессии, а в state.attempts уже идёт в новой.
                                pendingAddSession?.join()
                                pendingAddSession = null
                                val sessionId = viewState.value.currentSession?.id
                                    ?: getCurrentSessionId()?.id
                                    ?: return@launch

                                val balanceBefore = serenityBalanceUseCase().first

                                addAttempt(
                                    sessionId,
                                    capturedStartTime,
                                    endTime,
                                    null,
                                    toleranceMs = getEffectiveToleranceMs()
                                )

                                saveScmAfterAttempt()
                                val balanceAfter  = serenityBalanceUseCase().first
                                // delta belongs to attempt N-1 (SaveScmAfterAttemptUseCase
                                // computes for the middle of three: prev, curr, next=N)
                                val prevAttemptScm = (balanceAfter - balanceBefore)
                                    .takeIf { it > 0.0 }

                                loadMetrics()

                                addAttemptToList(
                                    time           = attemptDuration,
                                    deflection     = null,
                                    yourAnr        = decimalFormat.format(yourAnrUseCase()),
                                    startTimestamp = capturedStartTime,
                                    endTimestamp   = endTime,
                                    prevAttemptScm = prevAttemptScm
                                )

                                val currentSession =
                                    viewState.value.currentSession?.copy(endTime = endTime)
                                        ?: return@launch

                                // Сессия закрывается по одной из причин:
                                //   • победа квеста на baseline (awaitingHurray, в checkVictory)
                                //   • провал окна N (awaitingFailResult, в checkVictory)
                                //   • достигнут лимит сессии = текущее эффективное N (33/66/99).
                                //     Контракция от субквеста сбрасывает windowAttempts,
                                //     но НЕ должна давать продолжать сессию свыше N — иначе
                                //     накопится 65+ попыток (баг от 2026-06-27).
                                val sessionLimit = getEffectiveN().takeIf { it > 0 } ?: sessionAutoCloseAttempts
                                val autoClose = viewState.value.attempts.size >= sessionLimit
                                if (autoClose && !viewState.value.awaitingHurray &&
                                    !viewState.value.awaitingFailResult &&
                                    !viewState.value.awaitingSessionResult &&
                                    !viewState.value.awaitingContractionResult) {
                                    // Параметры закрывающейся сессии для inline-сообщения.
                                    // SessionSummaryOverlay загружается async — пока он не готов,
                                    // пользователь видит ЭТОТ текст и понимает: сессия завершена.
                                    val totalAttempts = viewState.value.attempts.size
                                    val wins = viewState.value.attempts.count { it.isWin }
                                    setState { copy(awaitingSessionResult = true) }
                                    val sid = currentSession.id
                                    viewModelScope.launch {
                                        val stats = getSessionComparison(sid)
                                        setState { copy(sessionComparisonStats = stats) }
                                    }
                                } else {
                                    setState { copy(currentSession = currentSession) }
                                }

                                updateSession(currentSession)
                            }
                        }

                    }

                    StopWatchCycle.ON_STOP -> {
                        when {
                            viewState.value.awaitingContractionResult -> {
                                // Пользователь увидел победный результат субквеста на кнопке
                                // и тапнул RESET. Чистим state, стартуем новую сессию на
                                // родительском уровне (N/t уже понижены в onQuestWindowSuccess),
                                // и показываем AFTER_SUBQUEST_STEPBACK чирап.
                                flushTick()   // сессия закрывается — накопить activeTimeMs
                                val cheerEnabled = localStorage.getCheerUpsEnabled()
                                val pending = viewState.value.pendingCheerUpTrigger
                                _stopwatchTime.value = timePlaceholder()
                                setState {
                                    copy(
                                        awaitingContractionResult = false,
                                        currentSession            = null,
                                        attempts                  = listOf(),
                                        // Чистим лог ТОЛЬКО в фильтре SESSION. В TODAY/MONTH/ALL_TIME
                                        // лог показывает данные из БД, они не зависят от границы сессии.
                                        displayedAttempts         = if (viewState.value.periodFilter ==
                                                                      PeriodFilter.SESSION)
                                                                        listOf()
                                                                    else viewState.value.displayedAttempts,
                                        stopWatchState            = StopWatchCycle.ON_START,
                                        buttonResultType          = ButtonResultType.NONE,
                                        startTime                 = -1L,
                                        endTime                   = -1L,
                                        errorPattern              = ErrorPattern.NONE,
                                        pendingCheerUpTrigger     = CheerUpTrigger.NONE,
                                        cheerUpTrigger            = if (cheerEnabled) pending else CheerUpTrigger.NONE,
                                        cheerUpSpecificText       = if (cheerEnabled) composeBubbleText(pending) else "",
                                        cheerUpAttemptsLeft       = if (cheerEnabled) 1 else 0
                                    )
                                }
                                stopWatch.setState(StopWatchCycle.ON_START)
                            }

                            viewState.value.awaitingSessionResult -> {
                                // Пользователь нажал "OK" после автозакрытия — показываем
                                // оверлей итогов. displayedAttempts тоже чистим, чтобы
                                // лог не оставался с попытками только что закрытой сессии.
                                flushTick()   // сессия закрывается — накопить activeTimeMs
                                val sid = viewState.value.currentSession?.id
                                _stopwatchTime.value = timePlaceholder()
                                setState {
                                    copy(
                                        awaitingSessionResult = false,
                                        currentSession        = null,
                                        attempts              = listOf(),
                                        displayedAttempts     = if (viewState.value.periodFilter ==
                                                                    PeriodFilter.SESSION)
                                                                      listOf()
                                                                  else viewState.value.displayedAttempts,
                                        stopWatchState        = StopWatchCycle.ON_START,
                                        buttonResultType      = ButtonResultType.NONE,
                                        startTime             = -1L,
                                        endTime               = -1L,
                                        errorPattern          = ErrorPattern.NONE,
                                    )
                                }
                                stopWatch.setState(StopWatchCycle.ON_START)
                                // Если статистика ещё не загружена — грузим сейчас
                                if (viewState.value.sessionComparisonStats == null && sid != null) {
                                    viewModelScope.launch {
                                        val stats = getSessionComparison(sid)
                                        setState { copy(sessionComparisonStats = stats) }
                                    }
                                }
                            }

                            viewState.value.awaitingFailResult -> {
                                // Пользователь нажал "OK" после провала окна — показываем
                                // fail-карточку (Alex 2026-07-13, симметрия с GreetingView).
                                // Бабл активируется ПОЗЖЕ, по тапу на GOT IT в карточке
                                // (Event.FailCard.Close). Пока карточка на экране —
                                // pendingCheerUpTrigger хранит триггер бабла.
                                flushTick()   // сессия закрывается — накопить activeTimeMs
                                val cheerEnabled = localStorage.getCheerUpsEnabled()
                                _stopwatchTime.value = timePlaceholder()
                                setState {
                                    copy(
                                        awaitingFailResult    = false,
                                        stopWatchState        = StopWatchCycle.ON_START,
                                        buttonResultType      = ButtonResultType.NONE,
                                        startTime             = -1L,
                                        endTime               = -1L,
                                        currentSession        = null,
                                        attempts              = listOf(),
                                        displayedAttempts     = if (viewState.value.periodFilter ==
                                                                    PeriodFilter.SESSION)
                                                                      listOf()
                                                                  else viewState.value.displayedAttempts,
                                        errorPattern          = ErrorPattern.NONE,
                                        taskTitle             = taskDescription(),
                                        questAimStr           = aimTimeFormatted(),
                                        questToleranceStr     = toleranceFormatted(),
                                        questIsSubquest       = isInSubquest(),
                                        questWindowLimit      = currentWindowLimit(),
                                        // Fail-карточка показывается только если чирапы включены
                                        // — иначе игрок сразу видит новый пустой главный экран.
                                        displayFailCard       = cheerEnabled && failCardData != null
                                    )
                                }
                                stopWatch.setState(StopWatchCycle.ON_START)
                                // Звук разочарования — играет одновременно с появлением
                                // fail-карточки.
                                if (cheerEnabled) {
                                    playSorryIfAvailable()
                                }
                            }

                            viewState.value.awaitingHurray -> {
                                // Пользователь нажал "HOORAY!" — показываем GreetingView.
                                // Чистим и displayedAttempts, чтобы лог с попытками
                                // предыдущего квеста не оставался видимым при переходе.
                                flushTick()   // сессия закрывается — накопить activeTimeMs
                                _stopwatchTime.value = timePlaceholder()
                                setState {
                                    copy(
                                        awaitingHurray      = false,
                                        displayGreetingView = true,
                                        attempts            = listOf(),
                                        displayedAttempts   = if (viewState.value.periodFilter ==
                                                                  PeriodFilter.SESSION)
                                                                    listOf()
                                                                else viewState.value.displayedAttempts,
                                        stopWatchState      = StopWatchCycle.ON_START,
                                        buttonResultType    = ButtonResultType.NONE,
                                        currentSession      = null
                                    )
                                }
                                stopWatch.setState(StopWatchCycle.ON_START)
                            }

                            else -> {
                                _stopwatchTime.value = timePlaceholder()
                                setState {
                                    copy(
                                        stopWatchState   = StopWatchCycle.ON_START,
                                        startTime        = -1L,
                                        endTime          = -1L,
                                        buttonResultType = ButtonResultType.NONE
                                    )
                                }
                                stopWatch.setState(viewState.value.stopWatchState)
                            }
                        }
                    }
                }
            }

            is GreetingView.Close -> {
                val pending = viewState.value.pendingCheerUpTrigger
                val cheerEnabled = localStorage.getCheerUpsEnabled()
                // Применяем следующий квест только сейчас — чтобы пользователь увидел новое задание
                // после GreetingView, а не до него.
                val nextAccuracy = localStorage.getAccuracy()
                val stats = viewState.value.questCompletionStats

                if (pending != CheerUpTrigger.NONE && cheerEnabled) {
                    // Для AFTER_LEVEL / AFTER_GAME числовая статистика подставляется
                    // асинхронно (см. viewModelScope.launch ниже); тут — базовая
                    // версия с описанием фазы и меткой следующего квеста.
                    val bubbleText = composeBubbleText(pending, completedStats = stats)
                    setState {
                        copy(
                            displayGreetingView   = false,
                            questCompletionStats  = null,
                            pendingCheerUpTrigger = CheerUpTrigger.NONE,
                            taskTitle             = taskDescription(),
                            questAimStr           = aimTimeFormatted(),
                            questToleranceStr     = toleranceFormatted(),
                            questIsSubquest       = isInSubquest(),
                            questWindowLimit      = currentWindowLimit(),
                            currentQuest          = quest(),
                            accuracy              = nextAccuracy,
                            cheerUpTrigger        = pending,
                            cheerUpSpecificText   = bubbleText,
                            cheerUpGeneralText    = "",
                            cheerUpAttemptsLeft   = 1
                        )
                    }
                    // Обогащаем текст статистикой игры (async). Числа уровня в
                    // AFTER_LEVEL больше не подставляются — они переехали в золотой
                    // блок GreetingView (дедупликация, Alex 2026-07-12); бабл несёт
                    // только «что дальше».
                    if (pending == CheerUpTrigger.AFTER_GAME) {
                        viewModelScope.launch {
                            val allAttempts = timeDatabase.tryDao().getAllAttempts()
                            val attemptsCount = allAttempts.size
                            val trueThreesCount = allAttempts.count { (it.endTime - it.startTime - currentAimTime) == 0L }
                            // Play Time = сумма activeTimeMs по всем сессиям, не сумма
                            // длительностей попыток (последнее давало 3-4 сек × N — не
                            // «время в игре», а «время под секундомером»).
                            val totalPureMs = timeDatabase.sessionDao().getAllSessions()
                                .sumOf { it.activeTimeMs }
                            val totalTimeStr = formatPureTime((totalPureMs / 60_000L).toInt())
                            val enriched = composeBubbleText(
                                CheerUpTrigger.AFTER_GAME,
                                completedStats     = stats,
                                enrichedAttempts   = attemptsCount,
                                enrichedTrueThrees = trueThreesCount,
                                enrichedTotalTime  = totalTimeStr
                            )
                            setState { copy(cheerUpSpecificText = enriched) }
                        }
                    }

                    // Async-хвост «You beat your average …» удалён (Alex 2026-07-12):
                    // то же сравнение теперь показывает таблица GreetingView
                    // («this quest vs your overall average»), словесный дубль в бабле
                    // не нужен.
                } else {
                    setState {
                        copy(
                            displayGreetingView   = false,
                            questCompletionStats  = null,
                            pendingCheerUpTrigger = CheerUpTrigger.NONE,
                            taskTitle             = taskDescription(),
                            questAimStr           = aimTimeFormatted(),
                            questToleranceStr     = toleranceFormatted(),
                            questIsSubquest       = isInSubquest(),
                            questWindowLimit      = currentWindowLimit(),
                            currentQuest          = quest(),
                            accuracy              = nextAccuracy
                        )
                    }
                }
            }

            is Event.FailCard.Close -> {
                // Пользователь закрыл fail-карточку (тап на GOT IT / REST — I'LL BE BACK).
                // Теперь активируем бабл с recap-opener'ом и новой фазой.
                val pending = viewState.value.pendingCheerUpTrigger
                val cheerEnabled = localStorage.getCheerUpsEnabled()
                val bubbleText = composeBubbleText(pending)
                setState {
                    copy(
                        displayFailCard       = false,
                        failCardData          = null,
                        pendingCheerUpTrigger = CheerUpTrigger.NONE,
                        cheerUpTrigger        = if (cheerEnabled) pending else CheerUpTrigger.NONE,
                        cheerUpSpecificText   = if (cheerEnabled) bubbleText else "",
                        cheerUpAttemptsLeft   = if (cheerEnabled) 1 else 0
                    )
                }
            }

            is Event.SelectHand -> {
                localStorage.putSelectedHand(event.hand)
                setState { copy(selectedHand = event.hand) }
            }

            is Event.SelectPeriod -> {
                // Сохраняем выбор в общем LocalStorage — StatsViewModel при следующем
                // открытии аналитического экрана увидит тот же период.
                localStorage.putPeriodFilter(event.filter)
                setState { copy(periodFilter = event.filter) }
                loadMetrics(event.filter)
            }

            is Event.OnScreenActive -> {
                // Пришли на MainScreen (или app вернулся из background).
                // Если сессия открыта — запускаем тикер Play Time.
                mainScreenActive = true
                startTickIfEligible()
            }

            is Event.OnScreenInactive -> {
                // Ушли на другой экран или app свернули. Flush тикер, чтобы
                // накопленное реально попало в session.activeTimeMs.
                flushTick()
                mainScreenActive = false
            }

            is Event.DismissCheerUp -> {
                // sorry.m4a уже отыграл в момент появления AFTER_FAIL-чирапа
                // (см. awaitingFailResult ветку выше) — здесь больше не дёргаем.
                setState {
                    copy(
                        cheerUpTrigger         = CheerUpTrigger.NONE,
                        cheerUpSpecificText    = "",
                        cheerUpGeneralText     = "",
                        cheerUpAttemptsLeft    = 0,
                        sessionComparisonStats = null
                    )
                }
            }

            is Event.DismissWelcome -> {
                localStorage.markWelcomeShown()
                setState { copy(showWelcome = false) }
            }
        }
    }

    private fun loadMetrics(
        filter: PeriodFilter = viewState.value.periodFilter,
        updateLog: Boolean = true          // false при старте — лог остаётся пустым
    ) {
        viewModelScope.launch {
            runCatching {
                val sessionId = viewState.value.currentSession?.id
                val metrics = mainMetricsUseCase(filter, sessionId)
                val (total, todayDelta) = serenityBalanceUseCase()

                // Лог должен СОВПАДАТЬ с источником метрик:
                //   • SESSION — берём текущую сессию из state.attempts (newest-first,
                //     уже с twin-метками и SC). РАНЬШЕ брали displayedAttempts,
                //     но при возврате с фильтра TODAY/MONTH туда оставались чужие
                //     данные — log показывал TODAY, а метрики SESSION, рассинхрон.
                //   • TODAY / MONTH / ALL_TIME — попытки за период из БД.
                val logAttempts = when {
                    !updateLog                     -> viewState.value.displayedAttempts
                    filter == PeriodFilter.SESSION -> viewState.value.attempts
                    else                           -> getLogAttempts(filter, sessionId)
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
    }

    // ── Адаптивные параметры квеста ──────────────────────────────────────────

    /**
     * Возвращает текущее N для квеста. Если LocalStorage ещё не инициализирован (-1),
     * берём дефолт из Quest. Гарантия: N >= baseline и (N − baseline) кратно step.
     * Старые значения (например N=3 из прежней версии L3.4) автоматически нормализуются.
     *
     * Для квестов без лимита (maxAttemptsDefault == null) ВСЕГДА возвращаем -1, даже
     * если в хранилище остался устаревший N=33/66/99 от предыдущего адаптивного квеста.
     * Тогда sessionLimit = sessionAutoCloseAttempts (99): счётчик no-limit квеста
     * идёт до 99, сессия закрывается и нумерация начинается заново.
     */
    private fun getEffectiveN(): Int {
        val quest    = viewState.value.currentQuest
        val baseline = quest.maxAttemptsDefault ?: return -1
        val step     = quest.adaptiveNStep
        val stored   = localStorage.getQuestCurrentN()

        if (stored == -1) {
            localStorage.putQuestCurrentN(baseline)
            return baseline
        }
        // Sanitize: floor at baseline, snap to next multiple of step, cap at baseline + 2·step.
        val nMax = baseline + 2 * step
        val snapped = when {
            stored <= baseline -> baseline
            else -> baseline + ((stored - baseline + step - 1) / step) * step
        }
        val sanitized = minOf(snapped, nMax)
        if (sanitized != stored) {
            localStorage.putQuestCurrentN(sanitized)
        }
        return sanitized
    }

    /**
     * Текущий допуск t в мс, нормализованный под точность текущего квеста.
     * Старые значения (например 1ms, оставшиеся с центисекундной индульгенции
     * под прежним багом ±1ms) автоматически подтягиваются к ближайшему
     * валидному шагу — иначе игрок остаётся в режиме «выиграть невозможно».
     */
    private fun getEffectiveToleranceMs(): Int {
        val quest    = viewState.value.currentQuest
        val baseT    = quest.toleranceMsDefault
        val stepMs   = toleranceStepMs(quest)
        val tMax     = baseT + 3 * stepMs
        val stored   = localStorage.getQuestCurrentToleranceMs()
        if (!quest.adaptiveT) return stored
        val snapped = when {
            stored <= baseT -> baseT
            else -> baseT + ((stored - baseT + stepMs - 1) / stepMs) * stepMs
        }
        val sanitized = minOf(snapped, tMax)
        if (sanitized != stored) {
            localStorage.putQuestCurrentToleranceMs(sanitized)
        }
        return sanitized
    }

    /** Триггеры, для которых бабл должен показывать "Next quest L{L}.{Q}" — advance path. */
    private val advanceTriggers = setOf(
        CheerUpTrigger.AFTER_QUEST,
        CheerUpTrigger.AFTER_LEVEL,
        CheerUpTrigger.AFTER_SUBQUEST_TO_BASELINE,
        CheerUpTrigger.AFTER_SIMPLIFIED_QUEST_DONE
    )

    /** Триггеры, где описание фазы не нужно (нет следующего задания). */
    private val noPhaseTriggers = setOf(
        CheerUpTrigger.AFTER_GAME,
        CheerUpTrigger.NONE,
        CheerUpTrigger.AFTER_SESSION
    )

    /**
     * Полный текст бабла с подстановкой placeholder'ов по актуальному состоянию.
     * Читает `quest()` / `currentWindowLimit()` / `getEffectiveToleranceMs()` из
     * LocalStorage, поэтому вызывать ПОСЛЕ того, как N/t уже обновились (успех /
     * провал / advance). Один вызов покрывает все 6 сайтов, где раньше был прямой
     * `CheerUpMessages.specificText(...)` с разными наборами параметров.
     */
    private fun composeBubbleText(
        trigger: CheerUpTrigger,
        completedStats: QuestCompletionStats? = null,
        enrichedAttempts: Int = 0,
        enrichedTrueThrees: Int = 0,
        enrichedTotalTime: String = ""
    ): String {
        val nextLabel = if (trigger in advanceTriggers) {
            val q = quest()
            "L${q.levelId}.${q.taskId}"
        } else ""
        val phase = if (trigger !in noPhaseTriggers)
            describePhase(quest(), currentWindowLimit(), getEffectiveToleranceMs())
        else ""
        val completedLevel = completedStats?.levelId ?: 0
        // Игрок на момент вызова уже перешёл на следующий уровень:
        //   completed = 1 → new = 2 → hundredths
        //   completed = 2 → new = 3 → thousandths
        val nextPrecision = when {
            completedLevel >= 2 -> "thousandths"
            completedLevel >= 1 -> "hundredths"
            else                -> ""
        }
        // Arrival-квест (тот, НА который игрок прибыл) — по нему AFTER_QUEST
        // выбирает разнообразную вводную фразу. quest() к этому моменту уже
        // указывает на следующий квест.
        val arrival = quest()
        // Ротация recap-opener'ов: каждый триггер имеет свой счётчик в
        // LocalStorage, инкрементируется на каждый вызов. Одна и та же
        // ситуация (напр. AFTER_QUEST дважды подряд) даёт разные фразы.
        val phraseIdx = localStorage.nextCheerUpPhraseIndex(trigger.name)
        return CheerUpMessages.specificText(
            trigger,
            nextQuestLabel   = nextLabel,
            phaseDescription = phase,
            completedLevelId = completedLevel,
            nextPrecision    = nextPrecision,
            attempts         = enrichedAttempts,
            trueThrees       = enrichedTrueThrees,
            totalTimeStr     = enrichedTotalTime,
            arrivalLevelId   = arrival.levelId,
            arrivalTaskId    = arrival.taskId,
            phraseIndex      = phraseIdx
        )
    }

    /**
     * Один шаг изменения допуска t для квеста — одна «клетка» точности дисплея.
     * Без масштабирования при centiseconds квест ждал бы попадания в ±1ms, что
     * невозможно увидеть глазом и невозможно засчитать (попытка 2.99 даёт ошибку
     * ровно 10ms, а допуск всего 1ms → проигрыш всегда).
     */
    private fun toleranceStepMs(quest: Quest): Int = when (quest.accuracy) {
        Accuracy.SECONDS      -> 1000
        Accuracy.DECISECONDS  -> 100
        Accuracy.CENTISECONDS -> 10
        Accuracy.MILLISECONDS -> 1
    }

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
        localStorage.putQuestWasIndulged(false)
    }

    /**
     * Провал N-окна: адаптируем N/t вверх и сбрасываем счётчики.
     */
    private fun onQuestWindowFailure(quest: Quest) {
        // Для fail-карточки: описание фазы в человеческом формате (тот же
        // describePhase, что использует бабл). Считаем ДО бампа — иначе
        // прочитаем уже новые N/t.
        val prevPhaseHuman = describePhase(
            quest,
            currentWindowLimit(),
            getEffectiveToleranceMs()
        )
        val phaseAttemptsBefore = viewState.value.attempts.size
        val phaseWinsBefore     = viewState.value.attempts.count { it.isWin }
        // Фиксируем ДО бампа: была ли это попытка пройти baseline (исходный квест)
        // или уже индульгентная фаза. Также фиксируем, был ли параметр уже на cap.
        val baseN = quest.maxAttemptsDefault ?: Int.MAX_VALUE
        val baseT = quest.toleranceMsDefault
        val wasAtBaseline = (!quest.adaptiveN || getEffectiveN() <= baseN) &&
                            (!quest.adaptiveT || getEffectiveToleranceMs() <= baseT)
        val stepMs = if (quest.adaptiveT) toleranceStepMs(quest) else 0
        val nMax   = if (quest.adaptiveN) (quest.maxAttemptsDefault ?: quest.adaptiveNStep) +
                                          2 * quest.adaptiveNStep else Int.MAX_VALUE
        val tMax   = if (quest.adaptiveT) quest.toleranceMsDefault + 3 * stepMs else 0
        val wasAtCap = (quest.adaptiveN && getEffectiveN() >= nMax) ||
                       (quest.adaptiveT && getEffectiveToleranceMs() >= tMax)
        // Индульгенция работает не более трёх ступеней:
        //   N: baseline → +step → +2·step  (для baseline=33, step=33 → max N = 99)
        //   t: 0 → 1 → 2 → 3 мс            (для baseline=0           → max t = 3)
        // Дойдя до cap, параметр больше не растёт; пользователь остаётся на cap-уровне
        // до победы на этом уровне (которая сокращает обратно к baseline шаг за шагом).
        if (quest.adaptiveN) {
            val currentN = getEffectiveN()
            val step     = quest.adaptiveNStep
            val nextN    = if (currentN == -1) step else currentN + step
            localStorage.putQuestCurrentN(minOf(nMax, nextN))
        }
        if (quest.adaptiveT) {
            localStorage.putQuestCurrentToleranceMs(minOf(tMax, getEffectiveToleranceMs() + stepMs))
        }
        // Отмечаем, что квест ушёл в субквест — если игрок потом победит baseline,
        // это будет AFTER_SIMPLIFIED_QUEST_DONE, а не AFTER_QUEST. Флаг сбросится
        // только при advance на следующий квест (resetQuestAdaptiveParams).
        localStorage.putQuestWasIndulged(true)
        // Пост-бамп проверка: параметр только что уперся в cap?
        val nowAtCap = (quest.adaptiveN && localStorage.getQuestCurrentN() >= nMax) ||
                       (quest.adaptiveT && localStorage.getQuestCurrentToleranceMs() >= tMax)
        localStorage.putQuestWindowAttempts(0)
        localStorage.putQuestWindowWins(0)
        localStorage.putQuestWindowMaxErrorMs(0)
        localStorage.putQuestWindowLastWinIndex(0)
        val cheerEnabled = localStorage.getCheerUpsEnabled()
        // Определяем подходящий субквестный триггер:
        //   wasAtCap                        → AFTER_SUBQUEST_STALLED (уже на дне, окно пусто)
        //   !wasAtCap && nowAtCap           → AFTER_SUBQUEST_CAPPED  (только что упёрлись в дно)
        //   wasAtBaseline                   → AFTER_SUBQUEST_ENTER   (первый уход в субквест)
        //   иначе                            → AFTER_SUBQUEST_DEEPER  (углубление в уже начатом субквесте)
        val trigger = when {
            wasAtCap        -> CheerUpTrigger.AFTER_SUBQUEST_STALLED
            nowAtCap        -> CheerUpTrigger.AFTER_SUBQUEST_CAPPED
            wasAtBaseline   -> CheerUpTrigger.AFTER_SUBQUEST_ENTER
            else            -> CheerUpTrigger.AFTER_SUBQUEST_DEEPER
        }
        val newPhaseHuman = describePhase(
            quest,
            currentWindowLimit(),
            getEffectiveToleranceMs()
        )
        // Стэш для fail-карточки: числа окна + описания фаз.
        val failData = FailCardData(
            trigger              = trigger,
            attempts             = phaseAttemptsBefore,
            wins                 = phaseWinsBefore,
            prevPhaseDescription = prevPhaseHuman,
            newPhaseDescription  = newPhaseHuman,
            levelId              = quest.levelId
        )
        // Не показываем чирап сразу — кнопка держит результат ("OK"), чирап появится по тапу.
        // Расчёт текста бабла отложен до FailCard.Close — recap-opener получит свежую
        // ротацию именно в момент показа бабла, не сейчас.
        setState {
            copy(
                taskTitle              = taskDescription(),
                questAimStr            = aimTimeFormatted(),
                questToleranceStr      = toleranceFormatted(),
                questIsSubquest        = isInSubquest(),
                questWindowLimit       = currentWindowLimit(),
                awaitingFailResult     = true,
                pendingCheerUpTrigger  = if (cheerEnabled) trigger else CheerUpTrigger.NONE,
                failCardData           = failData,
                cheerUpTrigger         = CheerUpTrigger.NONE,
                cheerUpSpecificText    = "",
                cheerUpGeneralText     = "",
                cheerUpAttemptsLeft    = 0
            )
        }
    }

    /**
     * Проигрывает озвучку "HOORAY!" из res/raw/hooray (любое расширение).
     * Если ресурс не подложен — тихо игнорируется (плейсхолдер до получения файла).
     */
    private fun playHoorayIfAvailable() = playRawIfAvailable("hooray")

    /** Озвучка после закрытия cheer-up'а о провале квеста (AFTER_FAIL). */
    private fun playSorryIfAvailable() = playRawIfAvailable("sorry")

    private fun playRawIfAvailable(name: String) {
        val ctx = context.context
        val resId = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        if (resId == 0) return
        runCatching {
            val mp = MediaPlayer.create(ctx, resId)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        }
    }

    /** True if two epoch-millis timestamps fall on the same calendar day in the device's local time. */
    private fun isSameCalendarDay(a: Long, b: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = a
        val yA = cal.get(java.util.Calendar.YEAR)
        val dA = cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.timeInMillis = b
        return yA == cal.get(java.util.Calendar.YEAR) &&
            dA == cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun formatPureTime(minutes: Int): String = when {
        minutes < 1  -> "less than a minute"
        minutes < 60 -> "$minutes min"
        else         -> "${minutes / 60} h ${minutes % 60} min"
    }

    /**
     * Успех квеста.
     *
     * По дизайну quests_oder.pdf (flowchart):
     *   Success? ──► N=base? & t=base? ──yes──► advance (exit cycle)
     *                                  ──no───► N:=N-step, t:=t-1ms; continue cycling
     *
     * Правила (Alex 2026-06-28):
     *   1. Победа УЖЕ на baseline → advance (бесспорный случай).
     *   2. Победа в субквесте, но РЕЗУЛЬТАТ ДОТЯГИВАЕТ ДО BASELINE → advance
     *      сразу, без промежуточных шагов. Условия baseline-quality:
     *        • ROW (адаптивный t): worst-win ≤ baseT (все победные попытки
     *          уложились в исходный допуск — фактически выиграл оригинальный квест).
     *        • IN_N (адаптивный N): все нужные победы случились в первых
     *          baseN попытках окна (lastWinIndex ≤ baseN).
     *   3. Иначе — один шаг назад к родительскому субквесту (одна ступень
     *      индульгенции снимается); чтобы advance'нуть, нужно выиграть ещё
     *      одно окно с лучшим результатом.
     *
     * Возвращает true когда квест считается пройденным (advance к следующему).
     */
    private fun onQuestWindowSuccess(quest: Quest): Boolean {
        val currentN = getEffectiveN()
        val currentT = getEffectiveToleranceMs()
        val baseN    = quest.maxAttemptsDefault ?: Int.MAX_VALUE
        val baseT    = quest.toleranceMsDefault
        val step     = quest.adaptiveNStep

        val atBaselineN = !quest.adaptiveN || currentN <= baseN
        val atBaselineT = !quest.adaptiveT || currentT <= baseT

        // Снимаем worst-win метрики ДО сброса (нужны для baseline-quality проверки).
        val worstWinErrorMs = localStorage.getQuestWindowMaxErrorMs()
        val lastWinIndex    = localStorage.getQuestWindowLastWinIndex()

        // Сбрасываем окно в любом случае — следующее окно будет новым.
        localStorage.putQuestWindowAttempts(0)
        localStorage.putQuestWindowWins(0)
        localStorage.putQuestWindowMaxErrorMs(0)
        localStorage.putQuestWindowLastWinIndex(0)

        // Правило 1: уже на baseline до этой победы → advance.
        if (atBaselineN && atBaselineT) {
            return true
        }

        // Правило 2: победа в субквесте с baseline-quality → прыжок к baseline + advance.
        // Для ROW: worst-win 0мс означает, что все wins были на 3.000 точно.
        // Для IN_N: lastWinIndex ≤ baseN значит, что nth-победа пришлась внутри
        //   baseline-окна. Если бы N не был расширен, игра бы засчитала победу на
        //   обычном baseline-окне — значит, фактически выиграл оригинальный квест.
        val baselineQualityT = !quest.adaptiveT || worstWinErrorMs <= baseT
        val baselineQualityN = !quest.adaptiveN || (lastWinIndex in 1..baseN)
        if (baselineQualityT && baselineQualityN) {
            // Сбрасываем параметры в baseline и advance.
            if (quest.adaptiveN) localStorage.putQuestCurrentN(baseN)
            if (quest.adaptiveT) localStorage.putQuestCurrentToleranceMs(baseT)
            return true
        }

        // Правило 3: победа в субквесте, но результат не дотянул до baseline —
        // один шаг назад к родителю.
        if (quest.adaptiveN && currentN > baseN) {
            localStorage.putQuestCurrentN(maxOf(baseN, currentN - step))
        }
        if (quest.adaptiveT && currentT > baseT) {
            val stepMs = toleranceStepMs(quest)
            localStorage.putQuestCurrentToleranceMs(maxOf(baseT, currentT - stepMs))
        }

        return false
    }

    // ── Основная логика попытки ───────────────────────────────────────────────

    private fun addAttemptToList(
        time: Long,
        deflection: Int?,
        yourAnr: String,
        startTimestamp: Long = 0L,
        endTimestamp: Long = 0L,
        prevAttemptScm: Double? = null   // SCM for N-1, retroactively stored on logBuffer[0]
    ) {
        // Retroactive SC применяем ТАКЖЕ в attempts: фильтр Session показывает
        // именно этот список, и без апдейта колонка SC оставалась пустой.
        val baseAttempts = if (prevAttemptScm != null &&
                               viewState.value.attempts.isNotEmpty() &&
                               !viewState.value.attempts[0].isSessionHeader) {
            listOf(viewState.value.attempts[0].copy(scm = prevAttemptScm)) +
                viewState.value.attempts.drop(1)
        } else viewState.value.attempts
        val attempts = baseAttempts.reversed().toMutableList()
        val currentLogBuffer = viewState.value.logBuffer
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

        // Номер ВНУТРИ текущей сессии — непрерывный 1, 2, 3, …, N (без цикла 1–33).
        // Сессия может содержать до N попыток в зависимости от текущего квеста (33/66/99).
        // Новая сессия всегда начинается с #1.
        // Считаем по in-memory списку attempts (он сбрасывается при закрытии сессии),
        // а не по logBuffer — в нём сверху лежит session-separator после моей последней
        // правки расположения, и takeWhile занулял счёт каждой попытки.
        val isFirstOfNewSession = viewState.value.attempts.isEmpty() && currentLogBuffer.isNotEmpty()
        val attemptsInCurrentSession = viewState.value.attempts.size
        val displayNumber = (attemptsInCurrentSession + 1).toLong()

        // Retroactively set SC on the previous item (logBuffer[0] = attempt N-1).
        // SaveScmAfterAttemptUseCase computes SC for N-1 when N is saved, so the
        // delta belongs to N-1, not to the new attempt N being added here.
        val updatedLogBuffer = if (prevAttemptScm != null &&
                                   currentLogBuffer.isNotEmpty() &&
                                   !currentLogBuffer[0].isSessionHeader) {
            listOf(currentLogBuffer[0].copy(scm = prevAttemptScm)) + currentLogBuffer.drop(1)
        } else {
            currentLogBuffer
        }

        // Twin shot: совпадение |error| с предыдущей (не-сессионной) попыткой.
        // По модулю: обе −0.05 → twin; обе +0.05 → twin; −0.05 и +0.05 → ТОЖЕ twin
        // (одинаковая «магнитуда» промаха, неважно с какой стороны). Допуск 0.001 c.
        val prevAttempt = currentLogBuffer.firstOrNull { !it.isSessionHeader }
        val twinShot = prevAttempt != null && !isWin &&
            kotlin.math.abs(kotlin.math.abs(prevAttempt.error) - kotlin.math.abs(tmpError)) < 0.0005

        // Если twin shot — помечаем и предыдущую попытку через copy(), чтобы LazyColumn
        // увидел изменение и перерисовал её с общим контуром. Предыдущая попытка может
        // лежать не на index 0 (если сверху session-заголовок) — ищем первый не-header.
        val twinTaggedBuffer = if (twinShot) {
            val prevIdx = updatedLogBuffer.indexOfFirst { !it.isSessionHeader }
            if (prevIdx >= 0) {
                updatedLogBuffer.toMutableList().also {
                    it[prevIdx] = it[prevIdx].copy(twinShot = true)
                }
            } else updatedLogBuffer
        } else updatedLogBuffer

        val newItem = SessionStatisticItemState(
            number         = displayNumber,
            time           = formatTime(time, localStorage.getAccuracy()),
            error          = tmpError,
            deflection     = tmpDeflection,
            isMaximum      = null,
            isMinimum      = null,
            hand           = localStorage.getSelectedHand().value,
            isWin          = isWin,
            startTimestamp = startTimestamp,
            endTimestamp   = endTimestamp,
            scm            = null,  // SC for this attempt is unknown until attempt N+1 arrives
            twinShot       = twinShot
        )

        // Разделитель сессии: вставляется НАД первой попыткой новой сессии
        // (визуально сверху, так как logBuffer хранится newest-first).
        // Номер берём из id сессии — autoincrement даёт глобальный порядковый номер,
        // совпадающий с тем, что показывает GetLogAttemptsUseCase в исторических периодах.
        val sessionSeparator: SessionStatisticItemState? = if (isFirstOfNewSession) {
            val sessionNum = viewState.value.currentSession?.id
                ?: (currentLogBuffer.count { it.isSessionHeader } + 1)
            // Сессия создаётся сейчас → текущие дата и время и есть её начало.
            // Формат совпадает с заголовками GetLogAttemptsUseCase:
            // дата · Session #N · квест · HH:mm.
            val dayStr  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val dateStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val q = viewState.value.currentQuest
            val total = getTasksCountForLevel(q.levelId)
            val questLabel = if (isInSubquest())
                "Level ${q.levelId}. SubQuest of ${q.taskId} of $total"
            else
                "Level ${q.levelId}. Quest ${q.taskId} of $total"
            SessionStatisticItemState(
                number          = 0,
                time            = "",
                error           = 0.0,
                deflection      = null,
                isMaximum       = null,
                isMinimum       = null,
                isSessionHeader = true,
                sessionLabel    = "Session #$sessionNum  ·  $questLabel  ·  $dateStr",
                // В SESSION-фильтре виден только текущий квест — этот заголовок
                // всегда самый верхний, дата рисуется слева.
                sessionDateLabel = dayStr
            )
        } else null

        // Twin shot: пересоздаём предыдущую попытку через copy(), чтобы Compose увидел изменение.
        if (twinShot && attempts.isNotEmpty()) {
            val lastIdx = attempts.size - 1
            attempts[lastIdx] = attempts[lastIdx].copy(twinShot = true)
        }
        attempts.add(newItem)

        if (attempts.size > 1) {
            attempts.forEach { it.isMinimum = null }
            attempts.forEach { it.isMaximum = null }
            attempts.minBy { it.error }.apply { isMinimum = true }
            attempts.maxBy { it.error }.apply { isMaximum = true }
        }

        val reversedAttempts = attempts.reversed().toList()
        // Персистентный лог: новая попытка в начало (newest first).
        // Разделитель сессий ставим МЕЖДУ блоками: при первой попытке новой
        // сессии вставляем sep СРАЗУ ПОД новой попыткой (= над предыдущей
        // сессией). Все последующие попытки текущей сессии просто пушатся
        // в начало — sep остаётся внизу блока текущей сессии и сверху блока
        // предыдущей, т.е. физически ровно между записями двух сессий.
        val newLogBuffer = if (sessionSeparator != null)
            listOf(newItem, sessionSeparator) + twinTaggedBuffer
        else
            listOf(newItem) + twinTaggedBuffer

        // Detect Error-Pattern chip — feeds errors + pauses to the (now unified) detector.
        // The 6 in-session "cheer-up" triggers from cheer_ups.docx are surfaced through
        // this chip, not through the cheer-up bubble (which is reserved for post-event only).
        val errorsChronological = attempts.map { it.error }
        val pausesMs: List<Long> = (1 until attempts.size).map { i ->
            val prev = attempts[i - 1]
            val curr = attempts[i]
            if (prev.endTimestamp > 0L && curr.startTimestamp > 0L)
                curr.startTimestamp - prev.endTimestamp
            else 0L
        }
        val detectedPattern = detectErrorPatterns(errorsChronological, pausesMs)

        // No in-session cheer-up bubble pathway anymore — only post-event triggers
        // (AFTER_QUEST / AFTER_FAIL / AFTER_LEVEL / AFTER_GAME / AFTER_SESSION) use the bubble.
        val cheerTrigger: CheerUpTrigger? = null

        // Auto-decrement cheer-up bubble counter on each attempt
        val currentAttemptsLeft = viewState.value.cheerUpAttemptsLeft
        val newAttemptsLeft = if (currentAttemptsLeft > 0) currentAttemptsLeft - 1 else 0
        val autoDismiss = currentAttemptsLeft == 1  // was 1, now becomes 0

        if (cheerTrigger != null) {
            shownCheerUps.add(cheerTrigger)
            val randomGeneral = if (CheerUpMessages.hasGeneralPart(cheerTrigger))
                CheerUpMessages.generalTexts.random()
            else ""
            setState {
                copy(
                    attempts          = reversedAttempts,
                    logBuffer         = newLogBuffer,
                    displayedAttempts = if (periodFilter == PeriodFilter.SESSION) reversedAttempts
                                        else displayedAttempts,
                    yourAnr           = yourAnr,
                    buttonResultType  = resultType,
                    errorPattern      = detectedPattern,
                    cheerUpTrigger      = cheerTrigger,
                    cheerUpSpecificText = CheerUpMessages.specificText(cheerTrigger),
                    cheerUpGeneralText  = randomGeneral,
                    cheerUpAttemptsLeft = 1
                )
            }
        } else if (autoDismiss) {
            setState {
                copy(
                    attempts          = reversedAttempts,
                    logBuffer         = newLogBuffer,
                    displayedAttempts = if (periodFilter == PeriodFilter.SESSION) reversedAttempts
                                        else displayedAttempts,
                    yourAnr           = yourAnr,
                    buttonResultType  = resultType,
                    errorPattern      = detectedPattern,
                    cheerUpTrigger      = CheerUpTrigger.NONE,
                    cheerUpSpecificText = "",
                    cheerUpGeneralText  = "",
                    cheerUpAttemptsLeft = 0
                )
            }
        } else {
            setState {
                copy(
                    attempts          = reversedAttempts,
                    logBuffer         = newLogBuffer,
                    displayedAttempts = if (periodFilter == PeriodFilter.SESSION) reversedAttempts
                                        else displayedAttempts,
                    yourAnr           = yourAnr,
                    buttonResultType  = resultType,
                    errorPattern      = detectedPattern,
                    cheerUpAttemptsLeft = newAttemptsLeft
                )
            }
        }

        // Twin / "it was close" — живут до следующей попытки, как остальные паттерны.
        // "Close" срабатывает когда промах равен РОВНО одной «клетке» дисплея
        // за пределом допуска квеста:
        //   baseline (t=0): 2.9 / 3.1 (deci), 2.99 / 3.01 (centi), 2.999 / 3.001 (milli)
        //   subquest ± 0.001: 2.998 / 3.002 (одна клетка за пределом ± допуска)
        //   subquest ± 0.002: 2.997 / 3.003 и т.д.
        // Раньше порог был фиксированный (одна клетка от 0), из-за чего в
        // субквесте с индульгенцией "close" срабатывал ВНУТРИ допуска и
        // конкурировал с TRUE_THREE.
        val stepSec = when (localStorage.getAccuracy()) {
            Accuracy.DECISECONDS  -> 0.1
            Accuracy.CENTISECONDS -> 0.01
            Accuracy.MILLISECONDS -> 0.001
            Accuracy.SECONDS      -> Double.MAX_VALUE   // на секундном уровне «close» бессмыслен
        }
        val tolSec = getEffectiveToleranceMs() / 1000.0
        val closeDistance = tolSec + stepSec
        val itWasClose = !isWin &&
            kotlin.math.abs(kotlin.math.abs(totalError) - closeDistance) < 0.0005
        setState {
            copy(
                twinShotVisible   = twinShot,
                itWasCloseVisible = !twinShot && itWasClose
            )
        }

        // Обновляем счётчики N-окна и проверяем победу / провал.
        // Передаём errorMs (величина ошибки в мс) — нужно для "worst-win"-логики
        // сжатия индульгенции (см. onQuestWindowSuccess).
        val errorMsAbs = kotlin.math.abs(totalError * 1000.0).toInt()
        checkVictory(isWin, errorMsAbs)
    }

    private fun checkVictory(isWin: Boolean, errorMs: Int) {
        val currentQuest = viewState.value.currentQuest

        // Обновляем счётчик попыток окна (если квест имеет N-лимит)
        if (currentQuest.maxAttemptsDefault != null) {
            val windowAttempts = localStorage.getQuestWindowAttempts() + 1
            localStorage.putQuestWindowAttempts(windowAttempts)
            if (isWin) {
                localStorage.putQuestWindowWins(localStorage.getQuestWindowWins() + 1)
                // Worst-win tracking для сжатия индульгенции:
                //   ROW квесты → max |error| (мс) среди победных попыток ТЕКУЩЕГО streak'а
                //   IN_N квесты → позиция последней победной попытки в окне
                if (currentQuest.adaptiveT) {
                    val prevMax = localStorage.getQuestWindowMaxErrorMs()
                    if (errorMs > prevMax) localStorage.putQuestWindowMaxErrorMs(errorMs)
                }
                if (currentQuest.adaptiveN) {
                    localStorage.putQuestWindowLastWinIndex(windowAttempts)
                }
            } else if (currentQuest.adaptiveT) {
                // Промах разрывает ROW-streak → сбрасываем worst-win, чтобы baseline-
                // quality проверка (Rule 2 в onQuestWindowSuccess) считала max только
                // по попыткам НОВОГО streak'а. Без этого ранняя in-tolerance победа
                // (напр. 3.01 в субквесте ±0.01) навсегда портила проверку и
                // следующие 3 чистых 3.00 подряд шли через Rule 3 (шаг назад),
                // хотя фактически игрок выполнил условия оригинального квеста.
                localStorage.putQuestWindowMaxErrorMs(0)
            }
        }

        // Считаем кол-во побед согласно условию. Для квестов с N-окном смотрим
        // только на ПОСЛЕДНИЕ windowAttempts попыток — это и есть текущее окно,
        // которое сжатие (onQuestWindowSuccess) сбрасывает до нуля. Без этой
        // нарезки старые победы из уже завершённого индульгентного окна
        // продолжали бы засчитываться → бесконечный цикл «снова победил → снова сжать».
        val allAttempts = viewState.value.attempts.sortedBy { it.number }
        val windowAttemptsCount = if (currentQuest.maxAttemptsDefault != null)
            localStorage.getQuestWindowAttempts() else allAttempts.size
        val windowItems = allAttempts.takeLast(windowAttemptsCount)
        val winAttempts = windowItems.filter { it.isWin }

        val winsCount = when (currentQuest.victory.victoryCondition) {

            VictoryCondition.SESSION -> winAttempts.size

            VictoryCondition.ROW -> {
                // Максимальная серия подряд идущих побед (по номеру попытки)
                // ВНУТРИ текущего окна.
                var maxRow = 0
                var curRow = 0
                var lastNum = -100L
                windowItems.forEach { item ->
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
            // Захватываем ДО сжатия — для разделения cheer-up триггеров
            // (был ли игрок в субквесте до победы).
            val wasAtBaselineBeforeWin  = !isInSubquest()
            val wasIndulgedBeforeWin    = localStorage.getQuestWasIndulged()
            // Адаптируем N/t вниз при успехе.
            val atBaseline = onQuestWindowSuccess(currentQuest)

            if (!atBaseline) {
                // Rule 3: победа в субквесте, но не baseline-quality — один шаг
                // назад к родителю. Показываем AFTER_SUBQUEST_STEPBACK по тапу.
                val cheerEnabled = localStorage.getCheerUpsEnabled()
                setState {
                    copy(
                        taskTitle                 = taskDescription(),
                        questAimStr               = aimTimeFormatted(),
                        questToleranceStr         = toleranceFormatted(),
                        questIsSubquest           = isInSubquest(),
                        questWindowLimit          = currentWindowLimit(),
                        awaitingContractionResult = true,
                        pendingCheerUpTrigger     = if (cheerEnabled) CheerUpTrigger.AFTER_SUBQUEST_STEPBACK
                                                     else CheerUpTrigger.NONE
                    )
                }
                return
            }

            // Параметры на исходных значениях — квест пройден по-настоящему.

            // Помечаем сессию как «квест пройден» (для истории / короны в дереве)
            viewState.value.currentSession?.id?.let { sid ->
                viewModelScope.launch { markSessionQuestComplete(sid) }
            }

            val isLastLevel = currentQuest.levelId >= 3
            val isLastTask  = currentQuest.taskId >= getTasksCountForLevel(currentQuest.levelId)
            val isAllDone   = isLastLevel && isLastTask

            // Собираем статистику сессии до сброса списка попыток
            val sessionAttempts = viewState.value.attempts.size
            val sessionWins = viewState.value.attempts.count { it.isWin }
            val completionStats = QuestCompletionStats(
                levelId              = currentQuest.levelId,
                taskId               = currentQuest.taskId,
                sessionAttempts      = sessionAttempts,
                sessionWins          = sessionWins,
                congratulationText   = currentQuest.congratulationDescription ?: "",
                arrivalLevelId       = when {
                    isAllDone  -> 0
                    isLastTask -> currentQuest.levelId + 1
                    else       -> currentQuest.levelId
                },
                arrivalTaskId        = when {
                    isAllDone  -> 0
                    isLastTask -> 1
                    else       -> currentQuest.taskId + 1
                },
                isLevelComplete      = isLastTask,
                isGameComplete       = isAllDone,
                nextPrecisionLabel   = when (currentQuest.levelId) {
                    1    -> "hundredths of a second: 3.00"
                    2    -> "thousandths of a second: 3.000"
                    else -> ""
                }
            )

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

            // AFTER_GAME / AFTER_LEVEL перекрывают всё остальное — если игра/уровень
            // закончены, показываем именно это. Иначе среди «квест взят» триггеров:
            //   Rule 2 (был в субквесте, выиграл baseline-quality → advance) → AFTER_SUBQUEST_TO_BASELINE
            //   Rule 1 + флаг wasIndulged                                    → AFTER_SIMPLIFIED_QUEST_DONE
            //   Rule 1 без флага (baseline с первого раза)                    → AFTER_QUEST
            val pendingTrigger = when {
                isAllDone  -> CheerUpTrigger.AFTER_GAME
                isLastTask -> CheerUpTrigger.AFTER_LEVEL
                !wasAtBaselineBeforeWin -> CheerUpTrigger.AFTER_SUBQUEST_TO_BASELINE
                wasIndulgedBeforeWin    -> CheerUpTrigger.AFTER_SIMPLIFIED_QUEST_DONE
                else                    -> CheerUpTrigger.AFTER_QUEST
            }
            // Не показываем GreetingView сразу — кнопка меняет текст на "HOORAY!"
            // чтобы пользователь успел увидеть свой результат перед переходом.
            setState {
                copy(
                    awaitingHurray        = true,
                    questCompletionStats  = completionStats,
                    greetingViewText      = completionStats.congratulationText,
                    pendingCheerUpTrigger = pendingTrigger
                )
            }
            // Озвучка "hooray" — проигрываем сразу как только показалась зелёная HOORAY!-кнопка
            // (на шаг раньше, чем тап пользователя).
            playHoorayIfAvailable()

            // Обогащаем статистику квеста данными из БД: время, SCM, сравнение
            // «этот квест vs накопленное среднее» (геом. средние). При переходе
            // уровня — ещё и итоги уровня (attempts / clean threes) для золотого
            // блока GreetingView.
            val enrichLevelId = currentQuest.levelId
            val enrichTaskId  = currentQuest.taskId
            val enrichIsLastTask = isLastTask
            // id победной сессии — для интерпретации «сессия vs история».
            // Захватываем ДО async: state может сброситься после HOORAY.
            val finalSessionId = viewState.value.currentSession?.id
            viewModelScope.launch {
                val summary = getQuestSummary(enrichLevelId, enrichTaskId, finalSessionId)
                var levelAttempts   = 0
                var levelTrueThrees = 0
                if (enrichIsLastTask) {
                    val sessions = timeDatabase.sessionDao().getSessionsByLevel(enrichLevelId)
                    val attempts = sessions.flatMap { timeDatabase.tryDao().getAttemptsBySessionId(it.id) }
                    levelAttempts   = attempts.size
                    levelTrueThrees = attempts.count { (it.endTime - it.startTime - currentAimTime) == 0L }
                }
                val current = viewState.value.questCompletionStats ?: return@launch
                setState {
                    copy(
                        questCompletionStats = current.copy(
                            questDays            = summary.questDays,
                            questPureTimeMinutes = summary.pureTimeMinutes,
                            scmEarned            = summary.scmEarned,
                            questEarlyMs         = summary.questEarlyMs,
                            overallEarlyMs       = summary.overallEarlyMs,
                            questLateMs          = summary.questLateMs,
                            overallLateMs        = summary.overallLateMs,
                            hitRateQuestPct      = summary.hitRateQuestPct,
                            hitRateOverallPct    = summary.hitRateOverallPct,
                            levelAttempts        = levelAttempts,
                            levelTrueThrees      = levelTrueThrees,
                            sessPrecisionMs      = summary.sessPrecisionMs,
                            histPrecisionMs      = summary.histPrecisionMs,
                            sessLucky            = summary.sessLucky,
                            histLucky            = summary.histLucky,
                            sessFocusPct         = summary.sessFocusPct,
                            histFocusPct         = summary.histFocusPct,
                            sessConsistPct       = summary.sessConsistPct,
                            histConsistPct       = summary.histConsistPct,
                            sessPauseMin         = summary.sessPauseMin,
                            histPauseMin         = summary.histPauseMin,
                            sessExcitationMs     = summary.sessExcitationMs,
                            histExcitationMs     = summary.histExcitationMs,
                            sessInhibitionMs     = summary.sessInhibitionMs,
                            histInhibitionMs     = summary.histInhibitionMs,
                        )
                    )
                }
            }
            return
        }

        // Проверка провала N-окна (только для квестов с лимитом).
        // Используем state.attempts.size как источник истины — это ровно то число
        // попыток, которое игрок видит в логе. Counter в LocalStorage может уйти
        // вперёд (DB-write не успел, нав туда-обратно, и т.п.) — тогда failure
        // срабатывал на 32-й попытке вместо 33-й. Берём минимум, чтобы не
        // сорвало раньше времени.
        if (currentQuest.maxAttemptsDefault != null) {
            val attemptsCount  = viewState.value.attempts.size
            val storedCount    = localStorage.getQuestWindowAttempts()
            val effectiveCount = kotlin.math.min(attemptsCount, storedCount)
            val currentN       = getEffectiveN()
            if (currentN > 0 && effectiveCount >= currentN) {
                onQuestWindowFailure(currentQuest)
            }
        }
    }
}
