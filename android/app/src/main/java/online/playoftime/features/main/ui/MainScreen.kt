package online.playoftime.features.main.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.KoinApplication
import online.playoftime.R
import online.playoftime.core.appModule
import online.playoftime.features.main.ui.MainContract.Event
import online.playoftime.features.main.ui.MainContract.Event.StopWatchButton
import online.playoftime.core.utils.CheerUpTrigger
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.ui_lib.GreetingView
import online.playoftime.ui_lib.Screen
import online.playoftime.ui_lib.SessionSummaryOverlay
import online.playoftime.ui_lib.stop_watch.StopWatchButton
import online.playoftime.ui_theme.Dimens
import online.playoftime.core.utils.ErrorPattern
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.helpAccentColor
import online.playoftime.ui_theme.baseBackgroundColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.baseTextStyle
import online.playoftime.ui_theme.resultBlueColor
import online.playoftime.ui_theme.resultGreenColor
import online.playoftime.ui_theme.resultRedColor
import online.playoftime.ui_theme.resultYellowColor

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
        online.playoftime.ui_lib.WelcomeOverlay(
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
        online.playoftime.ui_lib.FailCard(
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

    // Настройки представления кнопки — перечитываются при каждом возврате на Play.
    val displayPrefs = org.koin.compose.koinInject<online.playoftime.core.data.LocalStorageService>()
    val showProgressBar = remember(currentPage) { displayPrefs.getShowProgressBar() }
    val showNumericRun  = remember(currentPage) { displayPrefs.getShowNumericRun() }
    // Масштаб прогресс-бара (полуокно в мс) — настраивается в Настройках,
    // читаем при возврате на Play (перечитывается по смене страницы).
    // Масштаб бара (Alex 2026-07-30): в «авто» — динамическое полуокно из
    // state.autoBarHalfMs (ViewModel подстраивает под результаты), к которому
    // MainScreen ПЛАВНО анимирует за ~1 c (цифры по краям «доезжают» к новым).
    // В ручном режиме — фиксированный пресет из Settings.
    val targetBarHalfMs = if (displayPrefs.getBarScaleAuto()) state.autoBarHalfMs
                          else displayPrefs.getBarHalfWindowMs()
    val animBarHalf = remember { androidx.compose.animation.core.Animatable(targetBarHalfMs.toFloat()) }
    LaunchedEffect(targetBarHalfMs) {
        animBarHalf.animateTo(
            targetBarHalfMs.toFloat(),
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 1000,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }
    val barHalfWindowMs = animBarHalf.value.roundToInt().coerceAtLeast(5)

    // Геометрия прогресс-бара (root-координаты) — обновляется каждый тик,
    // НЕ Compose-state (иначе рекомпозиция MainScreen каждые 16 мс).
    val barGeom = remember { FloatArray(3) }          // tick3X, headX, trackY
    var barGeomValid = remember { booleanArrayOf(false) }
    // Снимок точки старта капли и начала сегмента ошибки — на момент запуска.
    var activeDropStart    by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var activeDropSegStart by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.attempts.size) {
        val grew = prevAttemptCount in 0 until state.attempts.size
        prevAttemptCount = state.attempts.size
        val newest = state.attempts.firstOrNull { !it.isSessionHeader }
        if (!grew || newest == null) return@LaunchedEffect
        activeDropErrorMs = newest.error * 1000.0
        activeDropIsWin   = newest.isWin
        // Источник капли (Alex 2026-07-23): засечка «3» прогресс-бара. Сначала
        // от «3» до головки анимированно вырастает сегмент ошибки, затем капля
        // капает с головки вниз на гистограмму. Если бар выключен — как раньше,
        // с конца эррор-бара свежей строки лога.
        if (showProgressBar && barGeomValid[0]) {
            activeDropStart = androidx.compose.ui.geometry.Offset(barGeom[1], barGeom[2])
        } else {
            activeDropStart = null
        }
        // Сегмент-«линия от 3 к головке» УБРАН (Alex 2026-07-25): он вырастал
        // горизонтально от центральной «3» к точке останова со скоростью бара
        // и читался как «прогресс-бар заезжает за точку останова». Чем дальше
        // от 3 (красная зона), тем длиннее и медленнее линия → «огромный забег».
        // Теперь капля сразу падает от головки вниз, без горизонтального роста.
        activeDropSegStart = null
        dropProgress.snapTo(0.3f)
        // Полёт капли; «гравитация» (ускорение) применяется в отрисовке через
        // t² — капля стартует медленно и разгоняется.
        dropProgress.animateTo(
            1f,
            androidx.compose.animation.core.tween(
                durationMillis = 600,
                easing = androidx.compose.animation.core.LinearEasing
            )
        )
        activeDropErrorMs = null
        activeDropStart   = null
        activeDropSegStart = null
    }

    // Цель капли — ТОЧКА на вершине столбца ошибки (Alex 2026-07-23): капля
    // пролетает ровно через верхнюю точку финального столбца (с учётом этой
    // попытки), а x — центр бакета, как рисует DistribChart. maxCount берём
    // как во время полёта (target подавлен на 1) — тогда точка совпадает и
    // до, и после приземления.
    data class DropTarget(val heightFrac: Float, val centerMs: Double)
    val dropTarget: DropTarget? = run {
        val e = activeDropErrorMs ?: return@run null
        val buckets = state.distribBuckets
        if (buckets.isEmpty()) return@run DropTarget(0f, e)
        val target = buckets.firstOrNull { e >= it.startMs && e < it.endMs }
            ?: buckets.minByOrNull { abs(it.centerMs - e) }
            ?: return@run DropTarget(0f, e)
        // maxCount — по полным counts, как в DistribChart (шкала стабильна).
        val maxCount = maxOf(buckets.maxOf { it.count }, 5)
        // Вершина ПОЛНОГО столбца (со свежей попыткой) — точка, через которую
        // пролетает капля; DistribChart растит столбец до неё синхронно.
        DropTarget(target.count.toFloat() / maxCount.toFloat(), target.centerMs)
    }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayBounds = it.boundsInRoot() }
                // Хит-тестинг режима справки живёт ЗДЕСЬ — на том же Box, что
                // оборачивает лог/гистограмму (см. helpTapGestures doc-comment
                // в HelpTapOverlay.kt: это ДОЛЖЕН быть настоящий предок
                // скролл-контента, а не сосед, иначе скролл под оверлеем
                // ломается — Alex 2026-09-10, второй заход после первой
                // попытки фикса, которая не сработала на Analytics).
                .then(
                    if (helpMode) Modifier.helpTapGestures(
                        zones        = helpZones,
                        questionRect = helpQuestionRect,
                        origin       = { overlayBounds?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero },
                        onSelect     = { helpTopic = it },
                        onExit       = { helpMode = false; helpTopic = null }
                    ) else Modifier
                )
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
                // Верхняя строка метрик убрана (Alex 2026-07-26): Balance переехал
                // в пиктограмму Analytics нижнего меню, Hit rate и Serenity coins —
                // на экран Analytics, тумблер руки — в Settings.
                Spacer(Modifier.height(10.dp))

                // ── Блок квеста: session summary OR текст задания + подсветка ──
                // Жёлтый бабл-чирап (Alex 01.09: «удаляется полностью») заменён
                // на простую подсветку области квеста амбером — привлекает
                // внимание к новому квесту/субквесту/уровню БЕЗ текста. Гаснет
                // после старта ПЕРВОЙ попытки сессии (RUN на пустом
                // state.attempts), тем же плавным 1.5с затуханием, которым
                // раньше «сдувался» бабл. CheerUpBubble.kt / CheerUpMessages.kt
                // НЕ удалены — просто больше не вызываются отсюда; текст,
                // который они бы сгенерировали, остаётся в state
                // (cheerUpSpecificText/cheerUpGeneralText), но никем не читается.
                val highlightActive = state.cheerUpTrigger !=
                    online.playoftime.core.utils.CheerUpTrigger.NONE

                // Alex 2026-09-10 (второй заход): подсветку ДЕРЖАТЬ на видном уровне,
                // пока игрок не начал играть — никакого авто-затухания, пока ждём его
                // действия (иначе к моменту, когда он наконец посмотрит на экран,
                // подсветка уже "успокоилась" до почти незаметной — та же жалоба
                // "слишком слабо", просто отложенная во времени). Плавно до НУЛЯ
                // подсветка уходит ТОЛЬКО в момент, когда запускается первая попытка
                // сессии (RUN на пустом state.attempts) — единственный триггер полного
                // затухания. Любой ДРУГОЙ сброс триггера (новый квест ещё до RUN,
                // мгновенный сброс при провале и т.п.) убирает подсветку быстро, без
                // долгого затухания — оно зарезервировано именно под "начало игры".
                val highlightPeakAlpha = 0.42f
                val highlightAlpha = remember { Animatable(0f) }
                var highlightVisible by remember { mutableStateOf(false) }
                LaunchedEffect(highlightActive) {
                    if (highlightActive) {
                        highlightVisible = true
                        highlightAlpha.animateTo(highlightPeakAlpha, tween(220))
                        // Держим здесь — дальше ничего не трогает alpha, пока не начнётся
                        // игра (см. LaunchedEffect(state.stopWatchState) ниже) или пока
                        // триггер не сбросится по другой причине.
                    } else if (highlightVisible) {
                        highlightVisible = false
                        highlightAlpha.animateTo(0f, tween(160))
                    }
                }
                LaunchedEffect(state.stopWatchState) {
                    if (state.stopWatchState == online.playoftime.core.utils.StopWatchCycle.IS_RUNNING &&
                        highlightActive && state.attempts.isEmpty()
                    ) {
                        highlightVisible = false
                        setEvent(Event.DismissCheerUp)
                        highlightAlpha.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
                    }
                }
                if (sessionStats != null && !state.awaitingSessionResult) {
                    SessionSummaryOverlay(
                        stats     = sessionStats,
                        onDismiss = { setEvent(Event.DismissCheerUp) },
                        modifier  = Modifier.padding(horizontal = Dimens.horizontalPadding)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.horizontalPadding)
                            .helpZone(helpZones, HelpTopic.QUEST_PANEL)
                    ) {
                        // Подсветка-«внимание» — амбер фон ПОД содержимым (в
                        // отличие от прежнего бабла, который лежал ПОВЕРХ и
                        // размывал текст под собой — блюр больше не нужен, сам
                        // квест-блок остаётся полностью читаемым). Видимость и
                        // alpha управляются напрямую через highlightAlpha —
                        // держим Box, пока идёт активность ИЛИ ещё не доиграло
                        // затухание, чтобы плавный уход до нуля не обрезался.
                        if (highlightVisible || highlightAlpha.value > 0f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        color = Color(0xFFF5C02E).copy(alpha = highlightAlpha.value),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            )
                        }

                        // "?" теперь передаётся слотом ВНУТРЬ QuestProgress
                        // (helpIcon), а не сосед в Row(weight) — Alex 01.09:
                        // центр мишеней/цели/характеристики должен сидеть на
                        // той же оси, что «0» прогресс-бара/диаграммы/
                        // гистограммы (истинный центр экрана). Раньше "?"
                        // занимал реальную ширину справа (26dp + отступ),
                        // сдвигая центр Column(weight(1f)) влево от истинного
                        // центра — та же асимметрия, что чинили для нижнего
                        // меню и гистограммы 23.08. QuestProgress теперь
                        // получает ПОЛНУЮ ширину панели (ту же, что у
                        // гистограммы/кнопки) и центрирует комплекс внутри
                        // себя — координата совпадает "бесплатно". "?"
                        // рисуется В ТОМ ЖЕ Box, что и центрируемый комплекс
                        // (не над рядом пипсов) — передан как слот, потому
                        // что helpMode/helpQuestionRect живут в MainScreen.
                        run {
                            val q = state.currentQuest
                            val isRow = q.victory.victoryCondition ==
                                online.playoftime.core.utils.VictoryCondition.ROW
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
                            // Пипсы окна попыток (Alex 25.08, замена пучка стрел):
                            // цвет результата по каждой сыгранной попытке, СТАРЕЙШАЯ
                            // ПЕРВОЙ — state.attempts хранится newest-first, поэтому
                            // reversed(). Заголовки сессий исключены — это не попытки.
                            val pipsColors = state.attempts.asReversed()
                                .filter { !it.isSessionHeader }
                                .map { a ->
                                    when {
                                        a.isWin     -> resultGreenColor
                                        a.error < 0.0 -> resultRedColor
                                        else        -> resultBlueColor
                                    }
                                }
                            QuestProgress(
                                levelId      = q.levelId,
                                taskId       = q.taskId,
                                subStep      = state.questSubStep,
                                aimTimeStr   = state.questAimStr,
                                toleranceStr = state.questToleranceStr,
                                victoryCount = q.victory.victoryCount,
                                isRow        = isRow,
                                progress     = landed,
                                windowLimit  = state.questWindowLimit,
                                pipsColors   = pipsColors,
                                completed    = state.awaitingHurray,
                                helpIcon     = {
                                    // Всегда интерактивна (раньше гасла под активным
                                    // баблом, чтобы тап по его крестику не проваливался
                                    // сюда — бабл удалён 01.09, условие больше не нужно).
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
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
                                        // "?" → "×" при входе в режим подсказок (Alex 2026-08-22) —
                                        // тот же круг служит и выходом из режима.
                                        // В режиме подсказок крестик жёлтый, тем же цветом, что
                                        // бабл подсказки (Alex 2026-09-27): оранжевым он читался
                                        // как обычная кнопка, а не как выход из справки.
                                        BasicText(
                                            text  = if (helpMode) "×" else "?",
                                            style = TextStyle(
                                                fontSize   = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = (if (helpMode) MaterialTheme.colorScheme.helpAccentColor
                                                         else MaterialTheme.colorScheme.accentColor).copy(alpha = 0.95f)
                                            )
                                        )
                                    }
                                }
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

                // Ширина прогресс-бара = ширина plot-области гистограммы частотного
                // распределения (Alex 2026-08-25). chartBounds — root-bounds Box'а,
                // которому DistribChart отдаётся БЕЗ доп. паддинга (см. ниже), поэтому
                // его ширина = ширина, которую DistribChart получает ДО собственных
                // внутренних отступов: .padding(horizontal=12.dp) снаружи + Spacer
                // CHART_LABEL_WIDTH слева + Column.padding(end=CHART_SC_PAD) справа —
                // именно столько же вычитаем здесь, чтобы получить фактическую ширину
                // столбиков гистограммы (DistribChart.kt:131-139).
                val density = LocalDensity.current
                val histogramPlotWidth: Dp? = chartBounds?.let { cb ->
                    with(density) {
                        (cb.width.toDp() - 24.dp - CHART_LABEL_WIDTH - CHART_SC_PAD)
                            .coerceAtLeast(0.dp)
                    }
                }
                // Гистограмма/лог растянуты и сдвинуты вправо (Alex 2026-09-09,
                // CHART_SC_PAD < CHART_LABEL_WIDTH — см. комментарий в DistribChart.kt),
                // прогресс-бар над кнопкой подгоняется тем же сдвигом, чтобы не
                // отвязаться от них визуально. Круглая кнопка секундомера сама НЕ
                // двигается — сдвигается только полоса над ней. Не зависит от
                // фактической ширины экрана (W-слагаемое сокращается алгебраически),
                // поэтому это фиксированная dp-константа, а не производная от chartBounds.
                val progressBarOffsetX = (CHART_LABEL_WIDTH - CHART_SC_PAD) / 2

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
                        // В режиме справки RUN/STOP не должен срабатывать, НО
                        // тап всё равно должен показать бабл справки (Alex
                        // 2026-09-10, второй заход: первая версия — простое
                        // enabled=false — вообще убрала справку, не только
                        // RUN: кнопка ловит касание на СВОЁМ собственном
                        // pointerInput(PointerEventPass.Initial), раньше ЛЮБОГО
                        // Main-pass обработчика выше по дереву, так что снаружи
                        // это никак не перехватить. Обрабатывается ПРЯМО ВНУТРИ
                        // StopWatchButton — см. currentHelpMode-ветку там).
                        helpMode          = helpMode,
                        onHelpTap         = { helpTopic = HelpTopic.STOPWATCH },
                        resultType        = state.buttonResultType,
                        showHurray        = state.awaitingHurray,
                        showProgressBar   = showProgressBar,
                        showNumericRun    = showNumericRun,
                        barHalfWindowMs   = barHalfWindowMs,
                        // Зернистость подписей бара = точность уровня: SECONDS→0,
                        // DECISECONDS→1, CENTISECONDS→2, MILLISECONDS→3 (= ordinal).
                        barDecimals       = state.accuracy.ordinal,
                        barToleranceMs    = state.questToleranceMs,
                        onBarGeometry     = { t3, head, y ->
                            barGeom[0] = t3; barGeom[1] = head; barGeom[2] = y
                            barGeomValid[0] = true
                        },
                        progressBarWidth  = histogramPlotWidth,
                        progressBarOffsetX = progressBarOffsetX
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
                        // Показываемая эмоция отстаёт от вычисленной: реакция
                        // смайлика на игровую ситуацию идёт с задержкой 0.5 с
                        // (Alex 2026-07-23) — глиф (улыбка/грусть), позиция,
                        // подмигивание и появление после первой попытки.
                        val shownKind  = remember { mutableStateOf(faceKind) }
                        val faceAppeared = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(500)
                            faceAppeared.value = true
                        }

                        LaunchedEffect(attemptsCount, faceKind) {
                            if (attemptsCount > prevCount.value) {
                                kotlinx.coroutines.delay(500)
                                shownKind.value = faceKind
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
                                shownKind.value = faceKind
                                prevKind.value  = faceKind
                                prevCount.value = attemptsCount
                            }
                        }

                        if (faceAppeared.value) FaceIcon(
                            kind         = shownKind.value,
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
                    state.twinShotVisible     -> "✦ wow, twins!"     to resultGreenColor
                    state.neighborShotVisible -> "✦ wow, neighbors!" to resultYellowColor
                    state.itWasCloseVisible   -> "✦ it was close!"   to resultYellowColor
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

                // Лог попыток — занимает оставшееся место и скроллится.
                // Обёрнут в Box (Alex 2026-08-14): справа накладывается тонкий
                // скроллер (AttemptLogScrollbar) — виден только пока список
                // реально прокручивается, плавно гаснет в покое.
                if (state.displayedAttempts.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state    = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = Dimens.smallPadding)
                            .helpZone(helpZones, HelpTopic.LOG)
                    ) {
                        val listSize = state.displayedAttempts.size
                        val firstAttemptIdx = state.displayedAttempts.indexOfFirst { !it.isSessionHeader }
                        // Дата стоит, номер уезжает (Alex 2026-07-30): дата и номер —
                        // на ОДНОЙ строке заголовка, но дату несёт ТОЛЬКО самый верхний
                        // (новейший) заголовок сессии. При старте новой сессии дата
                        // оказывается на её верхнем заголовке, а номер прежней сессии
                        // сползает вниз уже БЕЗ даты — визуально дата остаётся на месте,
                        // меняется только номер.
                        var topHeaderShown = false
                        state.displayedAttempts.forEachIndexed { idx, item ->
                            if (item.isSessionHeader) {
                                val carriesDate = !topHeaderShown
                                topHeaderShown = true
                                stickyHeader(key = "hdr$idx") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.baseBackgroundColor)
                                    ) {
                                        SessionStatisticItem(
                                            state          = if (carriesDate) item
                                                             else item.copy(sessionDateLabel = ""),
                                            scaleMaxMs     = dynamicScaleMaxMs,
                                            earlyMeanMs    = state.geomMeanEarlyMs,
                                            lateMeanMs     = state.geomMeanLateMs,
                                            absoluteMeanMs = state.geomMeanAbsoluteOffsetMs,
                                            accuracy       = state.accuracy,
                                            showScm        = false,
                                            isScmHeader    = false
                                        )
                                    }
                                }
                                return@forEachIndexed
                            }
                            item(key = "row$idx") {
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
                            // Neighbor-группа (Alex 2026-07-30): овал охватывает ТОЛЬКО
                            // непрерывный ряд, где КАЖДАЯ соседняя пара — реальные соседи
                            // (Δ модулей ошибок = ровно одна клетка точности). Прежняя
                            // версия тянула овал по всем подряд с флагом neighborShot, из-за
                            // чего 2.98/3.03/2.99/2.98 склеивались в один (3.03 и 2.99 —
                            // НЕ соседи, Δ = 2 клетки). Теперь там разрыв → два овала.
                            val neighborStepSec = when (state.accuracy) {
                                online.playoftime.core.utils.Accuracy.DECISECONDS  -> 0.1
                                online.playoftime.core.utils.Accuracy.CENTISECONDS -> 0.01
                                online.playoftime.core.utils.Accuracy.MILLISECONDS -> 0.001
                                else -> Double.MAX_VALUE
                            }
                            val isNbPair = { a: online.playoftime.features.main.ui.SessionStatisticItemState?,
                                             b: online.playoftime.features.main.ui.SessionStatisticItemState? ->
                                a != null && b != null && !a.isSessionHeader && !b.isSessionHeader &&
                                    a.neighborShot && b.neighborShot &&
                                    kotlin.math.abs(
                                        kotlin.math.abs(kotlin.math.abs(a.error) - kotlin.math.abs(b.error)) -
                                            neighborStepSec
                                    ) < 0.0005
                            }
                            val next = state.displayedAttempts.getOrNull(idx + 1)
                            val isNbTopmost = isNbPair(item, next) && !isNbPair(prev, item)
                            val neighborGroupHeight = if (isNbTopmost) {
                                var n = 1
                                var j = idx
                                while (j + 1 <= state.displayedAttempts.lastIndex &&
                                    isNbPair(state.displayedAttempts[j], state.displayedAttempts[j + 1])) {
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
                                    twinGroupHeight = twinGroupHeight,
                                    neighborGroupHeight = neighborGroupHeight
                                )
                            }
                            }
                        }
                    }
                    AttemptLogScrollbar(
                        listState = listState,
                        modifier  = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = Dimens.smallPadding)
                    )
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
                                color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.38f)
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
                                // столбик растёт синхронно с падением (фаза 0.3→1),
                                // так что вершина поднимается навстречу капле.
                                suppressErrorMs = activeDropErrorMs,
                                suppressReleaseFrac =
                                    ((dropProgress.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
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

            // Sticky нижняя навигация вынесена в MainActivity (Alex 2026-08-08):
            // единственный экземпляр над HorizontalPager — не пересоздаётся и не
            // едет вместе со страницей при свайпе/переключении.
        }

        // ── «Капля» ошибки: сегмент растёт от «3» прогресс-бара до головки, ──
        // затем капля летит вниз в свой столбик гистограммы. Если бар выключен —
        // капля стартует с эррор-бара свежей строки лога (как раньше).
        val dropE = activeDropErrorMs
        if (dropE != null && dropTarget != null &&
            chartBounds != null && overlayBounds != null &&
            (activeDropStart != null || newestRowBounds != null)
        ) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val origin = overlayBounds!!.topLeft
                // Плоскость графика: те же поля, что у DistribChart (outer padding
                // 12dp, слева лейбл CHART_LABEL_WIDTH, справа CHART_SC_PAD — оба
                // читаются из констант, а не захардкожены, чтобы не рассинхрониться).
                val plotLeft  = chartBounds!!.left - origin.x +
                    12.dp.toPx() + CHART_LABEL_WIDTH.toPx()
                val plotRight = chartBounds!!.right - origin.x -
                    12.dp.toPx() - CHART_SC_PAD.toPx()
                val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
                val scaleMax = dynamicScaleMaxMs.coerceAtLeast(1.0)
                // Целевой x — ЦЕНТР бакета (как рисует столбик DistribChart),
                // а не сырая ошибка: тогда капля попадает ровно в его вершину.
                val xFrac = ((dropTarget.centerMs / scaleMax + 1.0) / 2.0)
                    .toFloat().coerceIn(0f, 1f)
                val targetX = plotLeft + plotW * xFrac

                // Геометрия канваса гистограммы (см. DistribChart): высота 48dp,
                // ось на h−2dp, верхний отступ 6dp → barArea 40dp.
                val canvasTop = chartBounds!!.top - origin.y
                val axisY     = canvasTop + 48.dp.toPx() - 2.dp.toPx()
                val barAreaH  = 48.dp.toPx() - 2.dp.toPx() - 6.dp.toPx()
                // Вершина ФИНАЛЬНОГО столбца — капля проходит точно через неё.
                val endY = axisY - dropTarget.heightFrac * barAreaH

                val color = when {
                    activeDropIsWin -> resultGreenColor
                    dropE < 0       -> resultRedColor
                    else            -> resultBlueColor
                }

                val t = dropProgress.value
                val fromBar = activeDropStart != null
                val startX  = if (fromBar) activeDropStart!!.x - origin.x
                              else targetX
                val startY  = if (fromBar) activeDropStart!!.y - origin.y
                              else newestRowBounds!!.center.y - origin.y

                // Фаза 1 (сегмент от «3» до головки) убрана — см. LaunchedEffect.
                // Блок оставлен под условием, чтобы не рисоваться никогда.
                if (fromBar && activeDropSegStart != null) {
                    val segT   = (t / 0.3f).coerceIn(0f, 1f)
                    val seg0   = activeDropSegStart!! - origin.x
                    val segEnd = seg0 + (startX - seg0) * segT
                    drawLine(
                        color       = color,
                        start       = Offset(seg0, startY),
                        end         = Offset(segEnd, startY),
                        strokeWidth = 5.dp.toPx(),
                        cap         = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                // Фаза 2: полёт капли (x скользит от головки к своему столбику).
                val fallT = ((t - 0.3f) / 0.7f).coerceIn(0f, 1f)
                if (t > 0.3f && endY > startY) {
                    val tt = fallT * fallT   // гравитация: разгон к приземлению
                    val x  = startX + (targetX - startX) * fallT
                    val y  = startY + (endY - startY) * tt
                    // Хвост из бледнеющих капелек над основной
                    drawCircle(color.copy(alpha = 0.20f), 1.8.dp.toPx(),
                        Offset(x, y - 9.dp.toPx() * fallT))
                    drawCircle(color.copy(alpha = 0.45f), 2.4.dp.toPx(),
                        Offset(x, y - 4.5.dp.toPx() * fallT))
                    // Основная капля — слегка вытягивается по вертикали в полёте
                    scale(scaleX = 1f, scaleY = 1f + 0.5f * fallT, pivot = Offset(x, y)) {
                        drawCircle(color, 3.dp.toPx(), Offset(x, y))
                    }
                }
            }
        }

        // ── Оверлей интерактивной справки (тап по элементу → бабл) ──────
        // Хит-тестинг теперь на внешнем Box'е (см. .helpTapGestures выше) —
        // это чисто визуальный слой (рамка + бабл).
        if (helpMode) {
            HelpTapOverlayVisual(
                zones        = helpZones,
                questionRect = helpQuestionRect,
                selected     = helpTopic,
                onSelect     = { helpTopic = it },
                origin       = overlayBounds?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero,
                overlaySize  = overlayBounds?.size ?: androidx.compose.ui.geometry.Size.Zero,
                modifier     = Modifier.matchParentSize()
            )
        }

        // Кнопка обновления (Alex 2026-09-22) — левый верхний угол экрана,
        // видна только когда MainViewModel обнаружил на сервере версию новее
        // установленной (state.updateApkUrl != null, см. UpdateApi/init{}).
        // ПОСЛЕДНИЙ ребёнок этого Box НАРОЧНО (баг Alex 2026-09-22, найден и
        // исправлен): раньше кнопка стояла ПЕРВЫМ ребёнком, а основной контент
        // (Column ниже) рисуется поверх неё непрозрачным фоном — кнопка была
        // в дереве (потому тост про детект версии был честный), но физически
        // погребена под фоном экрана, невидима. В Box поздние дети рисуются
        // поверх ранних — тот же приём уже применён к help-оверлею выше.
        state.updateApkUrl?.let { apkUrl ->
            online.playoftime.ui_lib.AppUpdateButton(
                apkUrl        = apkUrl,
                crashReporter = org.koin.compose.koinInject<online.playoftime.core.telemetry.CrashReporter>(),
                modifier      = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 6.dp)
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
    // 11sp — тот же размер, что у лейбла "Geom. Mean" под гистограммой (Alex
    // 2026-08-22), раньше было мельче (9sp).
    val labelStyle = TextStyle(
        fontSize  = 11.sp,
        color     = textColor.copy(alpha = 0.63f),
        textAlign = TextAlign.Center
    )
    // Same font size as the "#" attempt-number values in the error diagram log rows
    // (see SessionStatisticItem: fontSize = 12.sp).
    val valueStyle = TextStyle(
        fontSize   = 12.sp,
        fontWeight = FontWeight.Bold,
        color      = textColor.copy(alpha = 0.90f),
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
        // Total tries — centred over total count, sits under the # column area.
        // Was "Σtries" (Alex 2026-08-22: clearer spelled out).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = "Total tries", style = labelStyle)
            BasicText(text = totalCount.toString(), style = valueStyle)
        }
        // Σ Serenity coins — вместо подписи «ΣSC» пиктограмма-монетка (Alex 2026-07-26).
        // CoinIcon вынесена в ui_lib (Alex 2026-09-10) — переиспользуется на Ranks.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            online.playoftime.ui_lib.CoinIcon(modifier = Modifier.size(9.dp))
            BasicText(text = scmText, style = valueStyle)
        }
    }
}

/**
 * Тонкий скроллер лога попыток (Alex 2026-08-14): вертикальная полоска у правого
 * края, размер и позиция — пропорция видимой/полной длины списка (оценка по
 * среднему размеру видимых элементов — LazyColumn не даёт точную высоту всего
 * контента без замера всех строк). Видна только пока список реально
 * прокручивается (`isScrollInProgress`), плавно гаснет через паузу простоя —
 * не отвлекает, когда список статичен.
 */
@Composable
private fun AttemptLogScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val scrolling = listState.isScrollInProgress
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            visible = true
        } else {
            delay(600)
            visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue  = if (visible) 0.55f else 0f,
        animationSpec = tween(if (visible) 80 else 400),
        label = "attemptLogScrollbarAlpha"
    )
    if (alpha <= 0f) return

    val thumbColor = MaterialTheme.colorScheme.accentColor
    Canvas(modifier = modifier.width(4.dp)) {
        val layoutInfo   = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val totalItems   = layoutInfo.totalItemsCount
        if (visibleItems.isEmpty() || totalItems == 0) return@Canvas

        // Непрерывная доля прокрутки ВНУТРИ первого видимого элемента (по его
        // собственному реальному размеру), а не по среднему размеру видимых
        // элементов. Прежняя версия усредняла size по всем visibleItemsInfo —
        // строки лога и высокие sticky-заголовки сессий сильно отличаются по
        // высоте, из-за чего среднее скакало кадр от кадра и бегунок «дёргался»
        // рывками на границах заголовков. Индекс+прогресс внутри элемента даёт
        // плавное движение независимо от того, что сейчас видно.
        val first = visibleItems.first()
        val itemProgress = if (first.size > 0) (-first.offset).toFloat() / first.size else 0f
        val scrollFraction = ((first.index + itemProgress) / totalItems.toFloat()).coerceIn(0f, 1f)

        val thumbFraction = (visibleItems.size.toFloat() / totalItems).coerceIn(0.08f, 1f)
        val thumbHeight = (size.height * thumbFraction).coerceAtLeast(24.dp.toPx())
        val maxOffset = (size.height - thumbHeight).coerceAtLeast(0f)
        val thumbOffset = (scrollFraction * maxOffset).coerceIn(0f, maxOffset)

        // Едва заметный трек — бегунок не «висит в воздухе» на пустом фоне.
        drawRoundRect(
            color        = thumbColor.copy(alpha = alpha * 0.18f),
            topLeft      = androidx.compose.ui.geometry.Offset(0f, 0f),
            size         = androidx.compose.ui.geometry.Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f)
        )
        drawRoundRect(
            color       = thumbColor.copy(alpha = alpha),
            topLeft     = androidx.compose.ui.geometry.Offset(0f, thumbOffset),
            size        = androidx.compose.ui.geometry.Size(size.width, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f)
        )
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
