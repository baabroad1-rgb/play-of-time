package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.KoinApplication
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.appModule
import ru.iflex.timemvp.features.main.ui.MainContract.Event
import ru.iflex.timemvp.features.main.ui.MainContract.Event.StopWatchButton
import ru.iflex.timemvp.core.utils.CheerUpTrigger
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.ui_lib.CheerUpBubble
import ru.iflex.timemvp.ui_lib.blurBackdrop
import ru.iflex.timemvp.ui_lib.GreetingView
import ru.iflex.timemvp.ui_lib.Screen
import ru.iflex.timemvp.ui_lib.SessionSummaryOverlay
import ru.iflex.timemvp.ui_lib.stop_watch.StopWatchButton
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.core.utils.ErrorPattern
import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.baseBackgroundColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.baseTextStyle
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor
import ru.iflex.timemvp.ui_theme.resultYellowColor

@Composable
fun MainScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit
) {
    val viewModel = koinViewModel<MainViewModel>()
    val state = viewModel.viewState.value
    val setEvent: (Event) -> Unit = { viewModel.setEvent(event = it) }

    // Интерактивная справка: тап по "?" включает режим, тап по элементу —
    // бабл с описанием. Зоны регистрируют свои Rect'ы через Modifier.helpZone.
    var helpMode by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }
    val helpZones = remember { androidx.compose.runtime.mutableStateMapOf<HelpTopic, androidx.compose.ui.geometry.Rect>() }
    var helpQuestionRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // Play-time трекер: следим за NavBackStackEntry lifecycle этого экрана.
    // ON_RESUME → RESUMED (виден и активен), ON_PAUSE → уход навигацией или в background.
    // На каждое событие шлём ViewModel'у, чтобы тот стартовал/флашил тикер activeTimeMs
    // текущей сессии. Так Play Time считается только пока MainScreen реально на экране.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> setEvent(Event.OnScreenActive)
                Lifecycle.Event.ON_PAUSE  -> setEvent(Event.OnScreenInactive)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // Страховка: если composable убран, а ON_PAUSE ещё не долетел —
            // flush явно. flushTick() в ViewModel идемпотентен.
            setEvent(Event.OnScreenInactive)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Welcome-окно первого запуска — перекрывает весь экран, показывается один раз
    if (state.showWelcome) {
        ru.iflex.timemvp.ui_lib.WelcomeOverlay(
            onDismiss = { setEvent(Event.DismissWelcome) }
        )
        return
    }

    // Оверлей завершения квеста — перекрывает весь экран
    if (state.displayGreetingView) {
        GreetingView(
            greetingText = state.greetingViewText,
            questStats   = state.questCompletionStats,
            onClick      = { setEvent(Event.GreetingView.Close) }
        )
        return
    }

    // Fail-карточка (Alex 2026-07-13) — симметричный аналог GreetingView
    // для fail-пути. Показывается ПОСЛЕ провала окна, ДО активации бабла.
    if (state.displayFailCard && state.failCardData != null) {
        ru.iflex.timemvp.ui_lib.FailCard(
            data      = state.failCardData,
            onDismiss = { setEvent(Event.FailCard.Close) }
        )
        return
    }

    val sessionStats = state.sessionComparisonStats

    // Lifted out of LazyColumn so DistribChart / GeomMeanRow can share the same dynamic scale.
    val listState = rememberLazyListState()
    val dynamicScaleMaxMs by remember(state.displayedAttempts) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val max = visible
                .mapNotNull { state.displayedAttempts.getOrNull(it.index) }
                .filter { !it.isSessionHeader }
                .maxOfOrNull { abs(it.error) * 1000.0 }
            (max ?: state.scaleMaxMs).coerceAtLeast(10.0)
        }
    }

    // ── «Капля» ошибки (Alex 2026-07-15) ────────────────────────────────
    // После новой попытки маленькая капля цвета результата летит с конца
    // эррор-бара верхней (свежей) строки лога ВНИЗ на гистограмму — и в момент
    // приземления соответствующий столбик подрастает (его вклад скрыт, пока
    // капля в полёте, — suppressErrorMs в DistribChart). Ощущение: гистограмма
    // растёт из падающих капель.
    var activeDropErrorMs by remember { mutableStateOf<Double?>(null) }
    var activeDropIsWin   by remember { mutableStateOf(false) }
    val dropProgress      = remember { androidx.compose.animation.core.Animatable(0f) }
    var newestRowBounds by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var chartBounds     by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var overlayBounds   by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var prevAttemptCount by remember { mutableStateOf(-1) }

    LaunchedEffect(state.attempts.size) {
        val grew = prevAttemptCount in 0 until state.attempts.size
        prevAttemptCount = state.attempts.size
        val newest = state.attempts.firstOrNull { !it.isSessionHeader }
        if (!grew || newest == null) return@LaunchedEffect
        activeDropErrorMs = newest.error * 1000.0
        activeDropIsWin   = newest.isWin
        dropProgress.snapTo(0f)
        // Прогресс линейный; «гравитация» (ускорение) применяется в отрисовке
        // через t² — капля стартует медленно и разгоняется к приземлению.
        dropProgress.animateTo(
            1f,
            androidx.compose.animation.core.tween(
                durationMillis = 600,
                easing = androidx.compose.animation.core.LinearEasing
            )
        )
        activeDropErrorMs = null
    }

    // Высота столбика-цели ДО приземления капли (доля от barAreaH гистограммы).
    // Формулы зеркалят DistribChart: count подавленного бакета −1, maxCount с polом 5.
    val dropTargetHeightFrac: Float? = run {
        val e = activeDropErrorMs ?: return@run null
        val buckets = state.distribBuckets
        if (buckets.isEmpty()) return@run 0f
        val target = buckets.firstOrNull { e >= it.startMs && e < it.endMs }
            ?: buckets.minByOrNull { abs(it.centerMs - e) }
        val suppressed = ((target?.count ?: 1) - 1).coerceAtLeast(0)
        val maxCount = maxOf(
            buckets.maxOf { if (it === target) suppressed else it.count }, 5
        )
        suppressed.toFloat() / maxCount.toFloat()
    }

    Screen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayBounds = it.boundsInRoot() }
        ) {
        Column {
            // ── Основной контент ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.baseBackgroundColor),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Метрики: Balance | Serenity | True Three — показываем всегда.
                // При старте без попыток данные загружены за всё время (ALL_TIME),
                // в первый запуск вообще — показатели будут нулевыми/прочерком.
                val totalAttempts = state.redCount + state.blueCount + state.trueThreeCount
                TopMetricsBar(
                    redCount             = state.redCount,
                    blueCount            = state.blueCount,
                    balanceSide          = state.balanceSide,
                    totalSerenityBalance = state.totalSerenityBalance,
                    todaySerenityDelta   = state.todaySerenityDelta,
                    trueThreeCount       = state.trueThreeCount,
                    totalAttempts        = totalAttempts,
                    selectedHand         = state.selectedHand,
                    onHandSelected       = { setEvent(Event.SelectHand(it)) },
                    helpZone             = { topic -> Modifier.helpZone(helpZones, topic) }
                )

                Spacer(Modifier.height(10.dp))

                // ── Блок квеста: session summary OR текст задания + чирап-бабл ──
                // Пост-эвентные чирапы (после квеста / уровня / субквеста / игры)
                // всплывают полупрозрачным жёлтым баблом ПОВЕРХ текста квеста —
                // с крестиком закрытия и опциональной ссылкой на Analytics.
                val bubbleActive = state.cheerUpTrigger !=
                    ru.iflex.timemvp.core.utils.CheerUpTrigger.NONE
                if (sessionStats != null && !state.awaitingSessionResult) {
                    SessionSummaryOverlay(
                        stats     = sessionStats,
                        onDismiss = { setEvent(Event.DismissCheerUp) },
                        modifier  = Modifier.padding(horizontal = Dimens.horizontalPadding)
                    )
                } else {
                    // Обёртка Box, чтобы бабл (оверлей ниже) мог перекрыть ВСЮ ширину
                    // включая иконку "?". Раньше бабл сидел внутри Column.weight(1f)
                    // и не доставал до "?" справа.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.horizontalPadding)
                            .helpZone(helpZones, HelpTopic.QUEST_PANEL)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (bubbleActive) Modifier.blurBackdrop(radiusPx = 40f)
                                    else Modifier
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val q = state.currentQuest
                                val isRow = q.victory.victoryCondition ==
                                    ru.iflex.timemvp.core.utils.VictoryCondition.ROW
                                val landed = if (isRow) {
                                    // ТЕКУЩИЙ streak — от новейшей попытки назад до первого
                                    // промаха. Alex попросил "сбрасывать мишени когда пошли
                                    // ошибки": как только идёт miss, targets возвращаются
                                    // к 0. state.attempts newest-first → takeWhile от
                                    // индекса 0 = текущий streak.
                                    // Внутренний checkVictory всё равно считает max streak
                                    // в окне, так что квест засчитывается когда N подряд
                                    // случились где угодно в окне.
                                    state.attempts.takeWhile { it.isWin }.size
                                        .coerceAtMost(q.victory.victoryCount)
                                } else {
                                    state.attempts.count { it.isWin }
                                        .coerceAtMost(q.victory.victoryCount)
                                }
                                QuestProgress(
                                    levelId      = q.levelId,
                                    taskId       = q.taskId,
                                    totalTasks   = 5,
                                    isSubquest   = state.questIsSubquest,
                                    aimTimeStr   = state.questAimStr,
                                    toleranceStr = state.questToleranceStr,
                                    victoryCount = q.victory.victoryCount,
                                    isRow        = isRow,
                                    progress     = landed,
                                    windowLimit  = state.questWindowLimit,
                                    windowUsed   = state.attempts.size
                                )
                            }

                            // Иконка "?". Тоже блюрится через parent Row modifier.
                            // Тап включает/выключает режим интерактивной справки.
                            // Отцентрована по высоте области квеста (align в Row),
                            // символ пожирнее/поярче (Alex 2026-07-11).
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 8.dp)
                                    .size(26.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.accentColor.copy(
                                            alpha = if (helpMode) 1f else 0.75f
                                        ),
                                        shape = CircleShape
                                    )
                                    .onGloballyPositioned { helpQuestionRect = it.boundsInRoot() }
                                    .clickable {
                                        helpTopic = null
                                        helpMode  = !helpMode
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text  = "?",
                                    style = TextStyle(
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.accentColor.copy(alpha = 0.95f)
                                    )
                                )
                            }
                        }

                        // Бабл как оверлей поверх Row — теперь заполняет всю ширину
                        // (включая "?"), а не сидит внутри Column.
                        if (bubbleActive) {
                            // CTA «See your progress →» скрыта на L1 и на L2.1 —
                            // впервые появляется, когда игрок только что взял L2.1
                            // и arrival-квест уже L2.2. Логика по Alex 2026-07-05:
                            // на первом уровне статистика не нужна (мало данных,
                            // разброс огромный), а с L2.2 идёт первый адаптивный
                            // квест где имеет смысл смотреть распределение.
                            val q = state.currentQuest
                            val levelUnlocksAnalytics =
                                q.levelId >= 3 || (q.levelId == 2 && q.taskId >= 2)
                            val offersLink = levelUnlocksAnalytics &&
                                ru.iflex.timemvp.core.utils.CheerUpMessages
                                    .offersAnalyticsLink(state.cheerUpTrigger)
                            CheerUpBubble(
                                specificText = state.cheerUpSpecificText,
                                generalText  = state.cheerUpGeneralText,
                                onDismiss    = { setEvent(Event.DismissCheerUp) },
                                onAnalytics  = if (offersLink) {
                                    {
                                        setEvent(Event.DismissCheerUp)
                                        onSelectPage(1)   // 1 = Analytics в pager'e
                                    }
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Эмоция лица: NICE — |error| последней попытки ≤ предыдущей (или ровно одна попытка);
                //              SAD  — иначе.
                // Смайлик появляется после ПЕРВОЙ попытки сессии и остаётся на кнопке всегда.
                // При смене эмоции — плавно скользит между верхней и нижней позицией;
                // при повторении эмоции — «подмигивает» (NICE) или «изгибает рот вниз» (SAD).
                val faceKind = run {
                    val a = state.attempts.sortedBy { it.number }
                    when {
                        a.isEmpty() -> FaceKind.NICE
                        a.size == 1 -> FaceKind.NICE
                        kotlin.math.abs(a.last().error) <=
                            kotlin.math.abs(a[a.size - 2].error) -> FaceKind.NICE
                        else -> FaceKind.SAD
                    }
                }

                Box(modifier = Modifier.helpZone(helpZones, HelpTopic.STOPWATCH)) {
                    StopWatchButton(
                        modifier          = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = Dimens.horizontalPadding),
                        state             = state.stopWatchState,
                        // Передаём State<String> объектом (не значение) —
                        // .value читается ВНУТРИ StopWatchButton, MainScreen
                        // не инвалидируется каждые 16 мс.
                        timeState         = viewModel.stopwatchTime,
                        // Прямой вызов метода ViewModel'а вместо setEvent — без
                        // прохода через MutableSharedFlow (буфер/collect). Один
                        // меньше round-trip Main-диспетчера между касанием и endTime.
                        onPress           = { wall, uptime ->
                            viewModel.onStopwatchPress(wall, uptime)
                        },
                        enabled           = true,
                        resultType        = state.buttonResultType,
                        showHurray        = state.awaitingHurray
                    )
                    val attemptsCount = state.attempts.size
                    if (attemptsCount >= 1 && !state.awaitingHurray) {
                        // TOP_Y — при NICE (сверху), BOTTOM_Y — при SAD (снизу). Значения = отступ
                        // от верхней грани кнопки. Кнопка ≥ 200 dp, иконка 28 dp,
                        // сохраняем прежний 10 dp зазор.
                        val topY    = 10f
                        val bottomY = (Dimens.mainButtonHeight.value) - 28f - 10f
                        val targetY = if (faceKind == FaceKind.NICE) topY else bottomY

                        val posY       = remember { androidx.compose.animation.core.Animatable(targetY) }
                        val winkAnim   = remember { androidx.compose.animation.core.Animatable(0f) }
                        val stretchAnim= remember { androidx.compose.animation.core.Animatable(0f) }
                        val prevKind   = remember { mutableStateOf(faceKind) }
                        val prevCount  = remember { mutableStateOf(attemptsCount) }

                        LaunchedEffect(attemptsCount, faceKind) {
                            if (attemptsCount > prevCount.value) {
                                if (faceKind != prevKind.value) {
                                    // Смена эмоции — скользим до новой позиции
                                    posY.animateTo(
                                        targetValue   = targetY,
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = 650,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    )
                                } else {
                                    // Эмоция повторилась — маленький «росчерк»
                                    if (faceKind == FaceKind.NICE) {
                                        winkAnim.animateTo(1f,
                                            androidx.compose.animation.core.tween(120))
                                        winkAnim.animateTo(0f,
                                            androidx.compose.animation.core.tween(220))
                                    } else {
                                        stretchAnim.animateTo(1f,
                                            androidx.compose.animation.core.tween(220))
                                        stretchAnim.animateTo(0f,
                                            androidx.compose.animation.core.tween(420))
                                    }
                                }
                                prevKind.value  = faceKind
                                prevCount.value = attemptsCount
                            } else if (attemptsCount < prevCount.value) {
                                // Сессия сброшена — снимок текущего состояния (без анимации).
                                posY.snapTo(targetY)
                                winkAnim.snapTo(0f)
                                stretchAnim.snapTo(0f)
                                prevKind.value  = faceKind
                                prevCount.value = attemptsCount
                            }
                        }

                        FaceIcon(
                            kind         = faceKind,
                            size         = 28.dp,
                            winkFactor   = winkAnim.value,
                            mouthStretch = stretchAnim.value,
                            modifier     = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    start = Dimens.horizontalPadding + 12.dp,
                                    top   = posY.value.dp
                                )
                        )
                    }
                }

                // Error Pattern chip — приоритет:
                //   1) "wow, twins!"     — мгновенное (1 сек), две подряд одинаковые ошибки
                //   2) "it was close!"   — мгновенное (1 сек), 2.999 / 3.001 на мс-уровне
                //   3) sustained-паттерны errorPattern
                val chipPair: Pair<String, androidx.compose.ui.graphics.Color>? = when {
                    state.twinShotVisible   -> "✦ wow, twins!"   to resultGreenColor
                    state.itWasCloseVisible -> "✦ it was close!" to resultYellowColor
                    state.errorPattern == ErrorPattern.SYSTEMATIC_EARLY -> "← too tense — try to relax" to resultRedColor
                    state.errorPattern == ErrorPattern.SYSTEMATIC_LATE  -> "→ too relaxed — try to focus" to resultBlueColor
                    state.errorPattern == ErrorPattern.INCREASING_DRIFT -> "↗ errors growing — breathe and refocus" to resultYellowColor
                    state.errorPattern == ErrorPattern.TRIPLE_IDENTICAL -> "✦ three identical — now improve accuracy" to resultGreenColor
                    state.errorPattern == ErrorPattern.PAUSE_SHORT      -> "← too tense — lengthen the pause" to resultRedColor
                    state.errorPattern == ErrorPattern.PAUSE_LONG       -> "→ too relaxed — shorten the pause" to resultBlueColor
                    state.errorPattern == ErrorPattern.JAGGED_RHYTHM    -> "↯ approaching to the balance" to resultYellowColor
                    state.errorPattern == ErrorPattern.RED_GROWING      -> "← easy, easy…" to resultRedColor
                    state.errorPattern == ErrorPattern.BLUE_GROWING     -> "→ wake up!!!" to resultBlueColor
                    else -> null
                }
                if (chipPair != null) {
                    val (patternText, patternColor) = chipPair
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.horizontalPadding, vertical = 2.dp)
                            .background(
                                color = patternColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = patternText,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = patternColor
                            )
                        )
                    }
                }

                // Лог попыток — занимает оставшееся место и скроллится
                if (state.displayedAttempts.isNotEmpty()) {
                    LazyColumn(
                        state    = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = Dimens.smallPadding)
                            .helpZone(helpZones, HelpTopic.LOG)
                    ) {
                        val listSize = state.displayedAttempts.size
                        val firstAttemptIdx = state.displayedAttempts.indexOfFirst { !it.isSessionHeader }
                        items(listSize) { idx ->
                            val item = state.displayedAttempts[idx]
                            // Twin group: только ВЕРХНЯЯ строка группы рисует общий маркерный овал,
                            // высотой = число строк в группе (включая себя).
                            // Группа = последовательные попытки с ОДИНАКОВЫМ |error|.
                            // Раньше группа расширялась пока подряд идут любые isTwin=true,
                            // из-за чего две разные пары близнецов (2.95/2.95 → 3.05/3.05)
                            // получали ОДИН большой овал вокруг четырёх строк вместо двух
                            // отдельных кружочков.
                            val itemAbsErr = kotlin.math.abs(item.error)
                            val prev = state.displayedAttempts.getOrNull(idx - 1)
                            val prevMatchesErr = prev != null && !prev.isSessionHeader &&
                                prev.twinShot &&
                                kotlin.math.abs(kotlin.math.abs(prev.error) - itemAbsErr) < 0.0005
                            val isTwinTopmost = item.twinShot && !prevMatchesErr
                            val twinGroupHeight = if (isTwinTopmost) {
                                var n = 1
                                var j = idx + 1
                                while (j <= state.displayedAttempts.lastIndex) {
                                    val nxt = state.displayedAttempts[j]
                                    if (nxt.isSessionHeader || !nxt.twinShot) break
                                    // Разрыв группы при смене |error| — начинается
                                    // новая пара близнецов, будет свой овал.
                                    if (kotlin.math.abs(kotlin.math.abs(nxt.error) - itemAbsErr)
                                        >= 0.0005) break
                                    n++; j++
                                }
                                n
                            } else 0
                            // Верхняя (свежая) строка данных — источник «капли»:
                            // запоминаем её bounds, чтобы капля стартовала с конца
                            // её эррор-бара.
                            val rowModifier = if (idx == firstAttemptIdx)
                                Modifier.onGloballyPositioned {
                                    newestRowBounds = it.boundsInRoot()
                                }
                            else Modifier
                            Box(modifier = rowModifier) {
                                SessionStatisticItem(
                                    state           = item,
                                    scaleMaxMs      = dynamicScaleMaxMs,
                                    earlyMeanMs     = state.geomMeanEarlyMs,
                                    lateMeanMs      = state.geomMeanLateMs,
                                    absoluteMeanMs  = state.geomMeanAbsoluteOffsetMs,
                                    accuracy        = state.accuracy,
                                    showScm         = if (state.periodFilter == PeriodFilter.SESSION)
                                                          !item.isSessionHeader && idx < listSize - 1
                                                      else !item.isSessionHeader,
                                    isScmHeader     = idx == firstAttemptIdx,
                                    twinGroupHeight = twinGroupHeight
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .helpZone(helpZones, HelpTopic.LOG),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text  = "No data for this period",
                            style = baseTextStyle.copy(
                                textAlign = TextAlign.Center,
                                color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // ── Аналитика — зафиксирована над нижней панелью ──────────
            if (state.distribBuckets.isNotEmpty() && state.displayedAttempts.isNotEmpty()) {
                val attemptsOnly = state.displayedAttempts.filter { !it.isSessionHeader }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .helpZone(helpZones, HelpTopic.DISTRIBUTION)
                                .onGloballyPositioned { chartBounds = it.boundsInRoot() }
                        ) {
                            DistribChart(
                                buckets    = state.distribBuckets,
                                scaleMaxMs = dynamicScaleMaxMs,
                                earlyMs    = state.geomMeanEarlyMs,
                                lateMs     = state.geomMeanLateMs,
                                absoluteMs = state.geomMeanAbsoluteOffsetMs,
                                accuracy   = state.accuracy,
                                // Пока капля летит — вклад свежей попытки в её
                                // столбик скрыт; на приземлении столбик подрастает.
                                suppressErrorMs = activeDropErrorMs
                            )
                        }
                        Box(modifier = Modifier.helpZone(helpZones, HelpTopic.GEOM_MEAN)) {
                            GeomMeanRow(
                                earlyMs    = state.geomMeanEarlyMs,
                                absoluteMs = state.geomMeanAbsoluteOffsetMs,
                                lateMs     = state.geomMeanLateMs,
                                scaleMaxMs = dynamicScaleMaxMs,
                                accuracy   = state.accuracy
                            )
                        }
                    }
                    // Итоги Σtries / ΣSC — оверлей в боковых полях, вертикально по центру
                    // гистограммы (~14 dp от её верхнего края при высоте бара 50 dp).
                    DiagramTotalsRow(
                        totalCount = attemptsOnly.size,
                        totalScm   = attemptsOnly.sumOf { it.scm ?: 0.0 },
                        modifier   = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)   // подтянуто к меньшей 48dp-высоте гистограммы
                    )
                }
            }

            // Period toggle убран (Alex 2026-07-11): главный экран всегда
            // показывает статистику за TODAY. Освободившееся место уходит логу
            // попыток (weight(1f)), гистограмма распределения не растёт — просто
            // опускается ниже.

            // ── Sticky нижняя навигация (Play · Analytics · Settings · About · Export) ──
            val exportAction = ru.iflex.timemvp.ui_lib.rememberExcelShareAction()
            ru.iflex.timemvp.ui_lib.BottomNavBar(
                currentPage  = currentPage,
                onSelectPage = onSelectPage,
                onExport     = exportAction
            )
        }

        // ── «Капля» ошибки: летит с эррор-бара свежей попытки в гистограмму ──
        val dropE = activeDropErrorMs
        if (dropE != null && dropTargetHeightFrac != null &&
            chartBounds != null && newestRowBounds != null && overlayBounds != null
        ) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val origin = overlayBounds!!.topLeft
                // Плоскость графика: те же поля, что у DistribChart
                // (outer padding 12dp, слева лейбл 64dp, справа SC-колонка 32dp).
                val plotLeft  = chartBounds!!.left - origin.x +
                    12.dp.toPx() + CHART_LABEL_WIDTH.toPx()
                val plotRight = chartBounds!!.right - origin.x -
                    12.dp.toPx() - CHART_SC_PAD.toPx()
                val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
                val scaleMax = dynamicScaleMaxMs.coerceAtLeast(1.0)
                // Единая формула x для эррор-бара строки и столбика гистограммы —
                // капля летит строго вертикально.
                val xFrac = ((dropE / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
                val x = plotLeft + plotW * xFrac

                val startY = newestRowBounds!!.center.y - origin.y
                // Геометрия канваса гистограммы (см. DistribChart): высота 48dp,
                // ось на h−2dp, верхний отступ 6dp → barArea 40dp.
                val canvasTop = chartBounds!!.top - origin.y
                val axisY     = canvasTop + 48.dp.toPx() - 2.dp.toPx()
                val barAreaH  = 48.dp.toPx() - 2.dp.toPx() - 6.dp.toPx()
                val endY = axisY - dropTargetHeightFrac * barAreaH - 1.dp.toPx()
                if (endY > startY) {
                    val t  = dropProgress.value
                    val tt = t * t   // гравитация: разгон к приземлению
                    val y  = startY + (endY - startY) * tt
                    val color = when {
                        activeDropIsWin -> resultGreenColor
                        dropE < 0       -> resultRedColor
                        else            -> resultBlueColor
                    }
                    // Хвост из бледнеющих капелек над основной
                    drawCircle(color.copy(alpha = 0.20f), 1.8.dp.toPx(),
                        Offset(x, y - 9.dp.toPx() * t))
                    drawCircle(color.copy(alpha = 0.45f), 2.4.dp.toPx(),
                        Offset(x, y - 4.5.dp.toPx() * t))
                    // Основная капля — слегка вытягивается по вертикали в полёте
                    scale(scaleX = 1f, scaleY = 1f + 0.5f * t, pivot = Offset(x, y)) {
                        drawCircle(color, 3.dp.toPx(), Offset(x, y))
                    }
                }
            }
        }

        // ── Оверлей интерактивной справки (тап по элементу → бабл) ──────
        if (helpMode) {
            HelpTapOverlay(
                zones        = helpZones,
                questionRect = helpQuestionRect,
                selected     = helpTopic,
                onSelect     = { helpTopic = it },
                onExit       = { helpMode = false; helpTopic = null },
                modifier     = Modifier.matchParentSize()
            )
        }
        }

        // Сообщение об успехе (оверлей)
        if (state.displayGreetingView) {
            GreetingView(
                greetingText = state.greetingViewText,
                onClick      = { setEvent(Event.GreetingView.Close) }
            )
        }
    }
}

/**
 * Итоговая строка под диаграммой ошибок:
 *   • под столбцом "#" — суммарное количество показанных попыток
 *   • под столбцом "SC" — суммарные Serenity Coins за эти попытки
 * Колоночные ширины 20 / 44 / weight / 32 dp совпадают с SessionStatisticItem.
 */
@Composable
private fun DiagramTotalsRow(
    totalCount: Int,
    totalScm: Double,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val labelStyle = TextStyle(
        fontSize  = 9.sp,
        color     = textColor.copy(alpha = 0.55f),
        textAlign = TextAlign.Center
    )
    // Same font size as the "#" attempt-number values in the error diagram log rows
    // (see SessionStatisticItem: fontSize = 12.sp).
    val valueStyle = TextStyle(
        fontSize   = 12.sp,
        fontWeight = FontWeight.Bold,
        color      = textColor.copy(alpha = 0.85f),
        textAlign  = TextAlign.Center
    )
    val scmText = if (totalScm >= 100) "%.0f".format(totalScm) else "%.2f".format(totalScm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        // Σtries — centred over total count, sits under the # column area.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = "Σtries", style = labelStyle)
            BasicText(text = totalCount.toString(), style = valueStyle)
        }
        // ΣSC — right-aligned into the blank SC gutter reserved by DistribChart
        // (CHART_SC_PAD = 32dp before the diagram's own right padding), with a
        // reduced end-padding here (4dp vs 12dp) so a wide sum never crosses
        // into the plotted histogram bars area.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = "ΣSC", style = labelStyle)
            BasicText(text = scmText, style = valueStyle)
        }
    }
}

@Preview
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    KoinApplication(application = {
        androidContext(context)
        modules(appModule)
    }) {
        MainScreen(
            currentPage  = 0,
            onSelectPage = {}
        )
    }
}
