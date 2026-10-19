package online.playoftime.features.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import online.playoftime.R
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.features.stats.domain.entity.AnalyticsData
import online.playoftime.features.stats.domain.entity.DeviationProfile
import online.playoftime.features.stats.domain.entity.SessionTreeData
import online.playoftime.features.main.ui.HelpTopic
import online.playoftime.features.main.ui.helpTapGestures
import online.playoftime.features.main.ui.helpZoneDisposable
import online.playoftime.features.main.ui.helpZonePartDisposable
import online.playoftime.features.stats.ui.StatsContract.Event
import online.playoftime.ui_lib.AppSpinner
import online.playoftime.ui_lib.InfoDialog
import online.playoftime.ui_lib.Screen
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.helpAccentColor
import online.playoftime.ui_theme.baseBackgroundColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.resultBlueColor
import online.playoftime.ui_theme.resultGreenColor
import online.playoftime.ui_theme.resultRedColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
//  Типографика: Inter с letter-spacing как Helvetica Now Display
// ──────────────────────────────────────────────────────────────────────────────

private val googleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

private val InterFamily = FontFamily(
    Font(GoogleFont("Inter"), googleFontsProvider, FontWeight.Normal),
    Font(GoogleFont("Inter"), googleFontsProvider, FontWeight.Medium),
    Font(GoogleFont("Inter"), googleFontsProvider, FontWeight.SemiBold),
    Font(GoogleFont("Inter"), googleFontsProvider, FontWeight.Bold),
)

// Roboto грузится явно, а не берётся системным по умолчанию: на Samsung и
// других оболочках системный шрифт свой, и спецификация дизайнера бы не совпала.
private val RobotoFamily = FontFamily(
    Font(GoogleFont("Roboto"), googleFontsProvider, FontWeight.Medium),
    Font(GoogleFont("Roboto"), googleFontsProvider, FontWeight.SemiBold),
)

// Спецификация дизайнера для карточек Analytics (Alex 2026-10-05).
private val metricLabelGray = Color(0xFF7F7F7F)
private val metricValueWhite = Color(0xFFFFFFFF)

/** Название показателя (Play time, Hit rate, …): Roboto Medium 11, #7F7F7F. */
private fun metricLabelStyle(
    size: androidx.compose.ui.unit.TextUnit = 11.sp,
    color: Color = metricLabelGray
) = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = size, color = color)

/** Значение показателя: Roboto Medium 12, #FFFFFF. */
private fun metricValueStyle(color: Color = metricValueWhite) =
    TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = color)

/** Заголовок блока (Chance of Win, …) над карточкой: Roboto SemiBold 12, #7F7F7F. */
private val blockTitleStyle =
    TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = metricLabelGray)

/** Большие цифры-метрики: tight letter-spacing (-0.02em) */
private fun metricStyle(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified
) = TextStyle(
    fontFamily    = InterFamily,
    fontSize      = size,
    fontWeight    = weight,
    letterSpacing = (-0.02).em,
    color         = color
)

/** Подписи и лейблы: wide letter-spacing (+0.08em) */
private fun labelStyle(
    size: androidx.compose.ui.unit.TextUnit = 11.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
) = TextStyle(
    fontFamily    = InterFamily,
    fontSize      = size,
    fontWeight    = weight,
    letterSpacing = 0.08.em,
    color         = color
)

// ──────────────────────────────────────────────────────────────────────────────
//  Цветовые константы зон (Activation Spectrum)
// ──────────────────────────────────────────────────────────────────────────────

internal val cardBg    = Color(0xFF1A1A1A)
private val dividerClr = Color(0xFF2A2A2A)
private val subtleText = 0.53f   // alpha для неактивного текста

// Tree column widths — заголовки и строки значений разделяют эти константы,
// чтобы значения выстраивались вертикально.
// Столбцы (слева направо): [lucky index] [geom. mean early/late] [att4win].
private val TREE_COL_LUCKY   = 40.dp
private val TREE_EL_LEFT_W   = 28.dp   // "early" (red) value cell, right-aligned
private val TREE_EL_SLASH_W  = 10.dp   // "/" cell, centered
private val TREE_EL_RIGHT_W  = 28.dp   // "late"  (blue) value cell, left-aligned
private val TREE_COL_ATT4WIN = 44.dp

@Composable
private fun SlashCell(
    leftText  : String,
    rightText : String,
    leftStyle : TextStyle,
    slashStyle: TextStyle,
    rightStyle: TextStyle,
    leftW     : Dp,
    slashW    : Dp,
    rightW    : Dp
) {
    // Ширина столбцов фиксирована, чтобы значения выстраивались вертикально,
    // поэтому четырёхзначные миллисекунды с разделителем разрядов в них уже не
    // влезают. FitValueText уменьшает шрифт ровно настолько, насколько нужно
    // (Alex 2026-09-27), вместо того чтобы обрезать цифру.
    Row(verticalAlignment = Alignment.CenterVertically) {
        FitValueText(
            modifier = Modifier.width(leftW),
            text  = leftText,
            style = leftStyle.copy(textAlign = TextAlign.End)
        )
        BasicText(
            modifier = Modifier.width(slashW),
            text  = "/",
            style = slashStyle.copy(textAlign = TextAlign.Center)
        )
        FitValueText(
            modifier = Modifier.width(rightW),
            text  = rightText,
            style = rightStyle.copy(textAlign = TextAlign.Start)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  StatScreen — точка входа
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun StatScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    onEarnMore: () -> Unit = {}
) {
    val viewModel  = koinViewModel<StatsViewModel>()
    val state      = viewModel.viewState.value
    val setEvent: (Event) -> Unit = { viewModel.setEvent(it) }

    // Интерактивная справка (как на главном): тап по "?" в period pill включает
    // режим, тап по карточке — бабл с описанием, повторный тап по "?" — выход.
    var helpMode by remember { mutableStateOf(false) }
    var helpTopic by remember {
        mutableStateOf<online.playoftime.features.main.ui.HelpTopic?>(null)
    }
    val helpZones = remember {
        androidx.compose.runtime.mutableStateMapOf<
            online.playoftime.features.main.ui.HelpTopic,
            androidx.compose.ui.geometry.Rect>()
    }
    var helpQuestionRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }

    // HorizontalPager держит экран живым — init StatsViewModel отработал один
    // раз при старте приложения, дальше данные протухали. Перезагружаем при
    // КАЖДОМ выходе экрана на передний план (свайп / таб / бабл) —
    // Alex 2026-07-15.
    androidx.compose.runtime.LaunchedEffect(currentPage) {
        if (currentPage == 2) setEvent(Event.Refresh)
    }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.baseBackgroundColor)
        ) {
            var analyticsBoxOrigin by remember {
                mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
            }
            var analyticsBoxSize by remember {
                mutableStateOf(androidx.compose.ui.geometry.Size.Zero)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        analyticsBoxOrigin = it.boundsInRoot().topLeft
                        analyticsBoxSize   = it.boundsInRoot().size
                    }
                    // Хит-тестинг режима справки живёт ЗДЕСЬ — на том же Box, что
                    // оборачивает AnalyticsContent (LazyColumn ниже). ДОЛЖЕН быть
                    // настоящий предок скролл-контента, не сосед — иначе скролл
                    // под оверлеем ломается (Alex 2026-09-10: "в режиме справки
                    // экран аналитикс не скролируется" — первая попытка фикса,
                    // отдельный оверлей-Box как СОСЕД LazyColumn, не сработала;
                    // см. helpTapGestures doc-comment в HelpTapOverlay.kt).
                    .then(
                        if (helpMode) Modifier.helpTapGestures(
                            zones        = helpZones,
                            questionRect = helpQuestionRect,
                            origin       = { analyticsBoxOrigin },
                            onSelect     = { helpTopic = it },
                            onExit       = { helpMode = false; helpTopic = null }
                        ) else Modifier
                    )
            ) {
                when {
                    state.analyticsLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.accentColor
                    )

                    state.analytics != null -> AnalyticsContent(
                        data             = state.analytics,
                        selectedPeriod   = state.selectedPeriod,
                        expandedYears    = state.expandedYears,
                        expandedMonths   = state.expandedMonths,
                        expandedWeeks    = state.expandedWeeks,
                        expandedDays     = state.expandedDays,
                        expandedSessions = state.expandedSessions,
                        onSelectPeriod   = { setEvent(Event.SelectPeriod(it)) },
                        onShiftPeriod    = { setEvent(Event.ShiftPeriod(it)) },
                        onToggleYear     = { setEvent(Event.ToggleYear(it)) },
                        onToggleMonth    = { setEvent(Event.ToggleMonth(it)) },
                        onToggleWeek     = { setEvent(Event.ToggleWeek(it)) },
                        onToggleDay      = { setEvent(Event.ToggleDay(it)) },
                        onToggleSession  = { setEvent(Event.ToggleSession(it)) },
                        onEarnMore       = onEarnMore,
                        helpZones        = helpZones,
                        helpMode         = helpMode,
                        onHelpToggle     = { helpTopic = null; helpMode = !helpMode },
                        onQuestionRect   = { helpQuestionRect = it }
                    )

                    else -> BasicText(
                        text     = "No data yet",
                        modifier = Modifier.align(Alignment.Center),
                        style    = labelStyle(color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = subtleText))
                    )
                }

                // Оверлей интерактивной справки — поверх карточек, под диалогами.
                // Хит-тестинг теперь на внешнем Box'е (см. .helpTapGestures выше) —
                // это чисто визуальный слой (рамка + бабл).
                if (helpMode) {
                    online.playoftime.features.main.ui.HelpTapOverlayVisual(
                        zones        = helpZones,
                        questionRect = helpQuestionRect,
                        selected     = helpTopic,
                        onSelect     = { helpTopic = it },
                        origin       = analyticsBoxOrigin,
                        overlaySize  = analyticsBoxSize,
                        modifier     = Modifier.matchParentSize()
                    )
                }

                if (state.displayExportToExcelResultDialog) {
                    InfoDialog(
                        message      = state.exportToExcelResultDialogText,
                        onCloseClick = { setEvent(Event.Dialog.Close) }
                    )
                }
                if (state.displayAppSpinner) AppSpinner()
            }
            // Sticky нижняя навигация вынесена в MainActivity (Alex 2026-08-08).
        }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Основное содержимое
// ──────────────────────────────────────────────────────────────────────────────

/** Клювик-стрелка листания периода назад/вперёд под фильтром на аналитике
 *  (Alex 2026-09-09). Тап неактивной (enabled=false) стрелки ничего не делает —
 *  дальше листать некуда (край истории или уже "сейчас"). */
@Composable
private fun PeriodShiftChevron(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.accentColor
    Box(
        modifier = Modifier
            .size(28.dp)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text  = symbol,
            style = labelStyle(
                16.sp,
                FontWeight.Bold,
                accent.copy(alpha = if (enabled) 0.9f else 0.25f)
            )
        )
    }
}

private fun PeriodFilter.toSlashLabel() = when (this) {
    PeriodFilter.SESSION  -> "/ session"
    PeriodFilter.TODAY    -> "/ day"
    PeriodFilter.WEEK     -> "/ week"
    PeriodFilter.MONTH    -> "/ month"
    PeriodFilter.ALL_TIME -> "/ all time"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnalyticsContent(
    data             : AnalyticsData,
    selectedPeriod   : PeriodFilter,
    expandedYears    : Set<Int>,
    expandedMonths   : Set<String>,
    expandedWeeks    : Set<String>,
    expandedDays     : Set<String>,
    expandedSessions : Set<Int>,
    onSelectPeriod   : (PeriodFilter) -> Unit,
    onShiftPeriod    : (Int) -> Unit = {},
    onToggleYear     : (Int) -> Unit,
    onToggleMonth    : (String) -> Unit,
    onToggleWeek     : (String) -> Unit,
    onToggleDay      : (String) -> Unit,
    onToggleSession  : (Int) -> Unit,
    onEarnMore       : () -> Unit = {},
    helpZones        : MutableMap<online.playoftime.features.main.ui.HelpTopic,
                           androidx.compose.ui.geometry.Rect> = mutableMapOf(),
    helpMode         : Boolean = false,
    onHelpToggle     : () -> Unit = {},
    onQuestionRect   : (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val tree    = remember(data.sessions) { buildTree(data.sessions) }
    val accent  = MaterialTheme.colorScheme.accentColor
    val pl      = selectedPeriod.toSlashLabel()
    // Дерево сессий — куски зоны STAT_SESSIONS (Alex 2026-09-10, см.
    // helpZonePartDisposable doc-comment): каждая строка дерева — свой
    // LazyColumn item, регистрирует себя сюда под собственным ключом.
    val sessionsTreeParts = remember {
        androidx.compose.runtime.mutableStateMapOf<Any, androidx.compose.ui.geometry.Rect>()
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Period pill остаётся на своём месте (сверху), но приклеивается к верху
        // при прокрутке — так фильтр всегда доступен без скролла обратно вверх.
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.baseBackgroundColor)
                    .padding(vertical = 4.dp)
                    .helpZoneDisposable(helpZones, HelpTopic.STAT_PERIOD)
            ) {
                PeriodPill(
                    selected       = selectedPeriod,
                    onSelect       = onSelectPeriod,
                    helpActive     = helpMode,
                    onHelpClick    = onHelpToggle,
                    onQuestionRect = onQuestionRect
                )
                // Контекст выбранного периода (Alex 2026-07-15): что именно
                // сейчас на экране — номер сессии / дата / месяц. All time —
                // без метки (контекст очевиден). Живёт в sticky-заголовке,
                // виден при любом скролле.
                // Alex 2026-09-09: подпись чуть крупнее, по центру, с клювиками
                // влево/вправо — листают день/неделю/месяц/сессию туда-сюда, и
                // весь экран пересчитывается под выбранный период (data.periodOffset,
                // считается в AnalyticsUseCase, а не тут — подпись только читает
                // готовый результат, чтобы не разойтись с тем, что реально загружено).
                val periodContext: String? = when (selectedPeriod) {
                    PeriodFilter.SESSION  -> data.selectedSessionNumber?.let { "Session #$it" }
                    PeriodFilter.TODAY    -> {
                        val (start, _) = selectedPeriod.rangeMsForOffset(data.periodOffset)
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(start))
                    }
                    PeriodFilter.WEEK     -> {
                        val fmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
                        val (start, endExclusive) = selectedPeriod.rangeMsForOffset(data.periodOffset)
                        val end = Date(endExclusive - 86_400_000L) // конец диапазона включительно
                        "${fmt.format(Date(start))} – ${fmt.format(end)}"
                    }
                    PeriodFilter.MONTH    -> {
                        val (start, _) = selectedPeriod.rangeMsForOffset(data.periodOffset)
                        SimpleDateFormat("LLLL yyyy", Locale.ENGLISH).format(Date(start))
                    }
                    PeriodFilter.ALL_TIME -> null
                }
                if (periodContext != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        PeriodShiftChevron(
                            symbol  = "‹",
                            enabled = data.hasOlderPeriod,
                            onClick = { onShiftPeriod(1) }
                        )
                        BasicText(
                            text     = periodContext,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            style    = labelStyle(
                                12.5.sp,
                                FontWeight.Medium,
                                MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.78f)
                            )
                        )
                        PeriodShiftChevron(
                            symbol  = "›",
                            enabled = data.hasNewerPeriod,
                            onClick = { onShiftPeriod(-1) }
                        )
                    }
                }
            }
        }
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_SUMMARY)) {
                SummaryHeader(data, helpMode = helpMode)
            }
        }

        // ── CHANCE OF WIN ────────────────────────────────────────────────
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_CHANCE)) {
            StatCard("Chance of Win", pl) {
                CardMetricRow(
                    label = "Hit rate",
                    value = "%.1f%%".format(data.trueThreeHitRatePct)
                )
                CardMetricRow(
                    label = "Attempts per win",
                    value = if (data.attemptsPerWin > 0) "%.1f".format(data.attemptsPerWin) else "—"
                )
                // Строка есть всегда; без чистых троек — прочерк (Alex 2026-10-07).
                CardMetricRow(
                    label = "Lucky Index",
                    value = if (data.luckyIndex > 0) "%.2f".format(data.luckyIndex) else "—",
                    note  = "(empirical / theoretical probability — higher is better)"
                )
            }
            }
        }

        // ── GEOMETRIC MEAN + VARIABILITY ─────────────────────────────────
        // (moved above RANGE OF DEVIATIONS per design)
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_GEOM)) {
            StatCard("Geometric Mean ÷ Spread", pl) {
                // Early/Late остаются в своих цветах — цвет здесь несёт смысл
                // (красный = рано, синий = поздно), как и на макете.
                DeviationRow("Early",    data.deviationEarly, resultRedColor,  negated = true)
                DeviationRow("Absolute", data.deviationAll,   metricLabelGray)
                DeviationRow("Late",     data.deviationLate,  resultBlueColor)
            }
            }
        }

        // ── RANGE OF DEVIATIONS (со встроенными MODES OF DEVIATION) ──────
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_RANGE)) {
            StatCard("Range of deviations", pl) {
                RangeBar(
                    earlyMs    = data.rangeEarlyMs,
                    lateMs     = data.rangeLateMs,
                    earlyCount = data.earlyCount,
                    greenCount = data.periodWins,
                    lateCount  = data.lateCount,
                    topEarly   = data.topEarly,
                    topLate    = data.topLate
                )
                Spacer(Modifier.height(4.dp))
                val textColor = MaterialTheme.colorScheme.baseTextColor
                BasicText(
                    text  = "The range of your results, 97% on both sides: the extreme 3% is cut off at each end.",
                    style = labelStyle(11.sp, color = textColor.copy(alpha = 0.48f))
                )
            }
            }
        }

        // ── PACE OF LEARNING ─────────────────────────────────────────────
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_PACE)) {
            // Что рисует график — выбор в выпадающем списке, который стоит на
            // месте заголовка блока (Alex 2026-10-07).
            // По умолчанию — Mean error (Alex 2026-10-19).
            var metric by rememberSaveable { mutableStateOf(PaceMetric.MEAN) }
            StatCard("", pl, header = { PaceMetricDropdown(metric) { metric = it } }) {
                val series = when (metric) {
                    PaceMetric.PACE  -> data.paceChartValues
                    PaceMetric.HIT   -> data.paceChartHitRate
                    PaceMetric.LUCKY -> data.paceChartLucky
                    PaceMetric.MEAN  -> data.paceChartEarlyMs
                    PaceMetric.FACES -> data.paceChartHappy
                }
                val isSession = selectedPeriod == PeriodFilter.SESSION
                if (metric == PaceMetric.FACES && isSession && series.isNotEmpty()) {
                    // Faces в сессии — ряд маленьких рожиц, по одной на попытку
                    // (Alex 2026-10-19).
                    SessionFacesStrip(data.paceChartHappy, data.paceChartSad)
                    Spacer(Modifier.height(6.dp))
                } else if (series.size >= 2) {
                    // В сессии столбик — одна попытка, без усреднения (Alex
                    // 2026-10-19). У Hit rate и Lucky там два состояния,
                    // и подписи — словами, а не числами.
                    val formatY: (Float) -> String = when {
                        !isSession                -> metric.formatY
                        metric == PaceMetric.HIT   -> { v -> if (v > 0f) "hit" else "miss" }
                        metric == PaceMetric.LUCKY -> { v -> if (v > 0f) "3.000" else "—" }
                        metric == PaceMetric.FACES -> { _ -> "" }
                        else                       -> metric.formatY
                    }
                    if (metric == PaceMetric.MEAN) {
                        // Ранние красным, поздние синим (Alex 2026-10-18): за день
                        // и больше — пара столбиков в точке, в сессии — один
                        // столбик попытки своего цвета.
                        PaceSparklineChart(
                            values    = data.paceChartEarlyMs,
                            lineColor = resultRedColor,
                            xLabels   = data.paceChartLabels,
                            formatY   = formatY,
                            legend    = "early",
                            values2   = data.paceChartLateMs,
                            color2    = resultBlueColor,
                            legend2   = "late",
                            single    = isSession
                        )
                    } else if (metric == PaceMetric.FACES) {
                        // Весёлые и грустные рожицы — цветами самих лиц (FaceIcon).
                        PaceSparklineChart(
                            values    = data.paceChartHappy,
                            lineColor = FACE_HAPPY_COLOR,
                            xLabels   = data.paceChartLabels,
                            formatY   = formatY,
                            legend    = "happy",
                            values2   = data.paceChartSad,
                            color2    = FACE_SAD_COLOR,
                            legend2   = "sad",
                            single    = isSession
                        )
                    } else {
                        PaceSparklineChart(
                            values     = series,
                            lineColor  = accent.copy(alpha = 0.65f),
                            xLabels    = data.paceChartLabels,
                            formatY    = formatY,
                            legend     = metric.legend
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                when (metric) {
                    PaceMetric.PACE -> CardMetricRow(
                        label = "SC/att",
                        value = "%+.1f".format(data.paceOfLearning),
                        note  = "Average Serenity per attempt"
                    )
                    PaceMetric.HIT -> CardMetricRow(
                        label = "Hit rate",
                        value = "%.1f%%".format(data.trueThreeHitRatePct)
                    )
                    PaceMetric.LUCKY -> CardMetricRow(
                        label = "Lucky Index",
                        value = if (data.luckyIndex > 0) "%.2f".format(data.luckyIndex) else "—"
                    )
                    PaceMetric.MEAN -> CardMetricRow(
                        label = "Early / Late",
                        value = (data.missMeanEarlyMs?.let { "−" + formatMeanMs(it) } ?: "—") +
                                " / " + (data.missMeanLateMs?.let { "+" + formatMeanMs(it) } ?: "—"),
                        note  = "Geometric mean, ms"
                    )
                    PaceMetric.FACES -> CardMetricRow(
                        label = "Happy / Sad",
                        value = "${data.facesHappy} / ${data.facesSad}"
                    )
                }
            }
            }
        }

        // ── Sessions Tree ────────────────────────────────────────────────
        item {
            val baseColor = MaterialTheme.colorScheme.baseTextColor
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .helpZonePartDisposable(helpZones, HelpTopic.STAT_SESSIONS, sessionsTreeParts, "header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    modifier = Modifier.weight(1f),
                    text  = "Sessions log",
                    style = blockTitleStyle
                )
                // Column header — lucky index (per-row) — centered in its column
                BasicText(
                    modifier = Modifier.width(TREE_COL_LUCKY),
                    text  = "lucky",
                    style = labelStyle(9.sp, FontWeight.SemiBold, baseColor.copy(alpha = 0.5f))
                        .copy(textAlign = TextAlign.Center)
                )
                // Column header — mean (single label, centered over both red/late cells)
                BasicText(
                    modifier = Modifier.width(TREE_EL_LEFT_W + TREE_EL_SLASH_W + TREE_EL_RIGHT_W),
                    text  = "mean",
                    style = labelStyle(9.sp, FontWeight.SemiBold, baseColor.copy(alpha = 0.5f))
                        .copy(textAlign = TextAlign.Center)
                )
                // Column header — att4win — centered in its column
                BasicText(
                    modifier = Modifier.width(TREE_COL_ATT4WIN),
                    text  = "att4win",
                    style = labelStyle(9.sp, FontWeight.SemiBold, baseColor.copy(alpha = 0.5f))
                        .copy(textAlign = TextAlign.Center)
                )
            }
        }

        tree.forEach { yearNode ->
            item {
                Box(
                    Modifier.helpZonePartDisposable(
                        helpZones, HelpTopic.STAT_SESSIONS, sessionsTreeParts, "year-${yearNode.year}"
                    )
                ) {
                TreeYearRow(
                    year          = yearNode.year,
                    attempts      = yearNode.totalAttempts,
                    geomEarlyMs   = yearNode.geomEarlyMs,
                    geomLateMs    = yearNode.geomLateMs,
                    wins          = yearNode.totalWins,
                    luckyIndex    = yearNode.luckyIndex,
                    expanded      = yearNode.year in expandedYears,
                    summary       = yearNode.summary,
                    onClick       = { onToggleYear(yearNode.year) },
                    helpMode      = helpMode
                )
                }
            }
            if (yearNode.year in expandedYears) {
                yearNode.months.forEach { monthNode ->
                    val monthKey = "${yearNode.year}-${monthNode.month}"
                    item {
                        Box(
                            Modifier.helpZonePartDisposable(
                                helpZones, HelpTopic.STAT_SESSIONS, sessionsTreeParts, "month-$monthKey"
                            )
                        ) {
                        TreeMonthRow(
                            label         = monthNode.label,
                            attempts      = monthNode.totalAttempts,
                            geomEarlyMs   = monthNode.geomEarlyMs,
                            geomLateMs    = monthNode.geomLateMs,
                            wins          = monthNode.totalWins,
                            luckyIndex    = monthNode.luckyIndex,
                            expanded      = monthKey in expandedMonths,
                            summary       = monthNode.summary,
                            onClick       = { onToggleMonth(monthKey) },
                            helpMode      = helpMode
                        )
                        }
                    }
                    if (monthKey in expandedMonths) {
                        monthNode.weeks.forEach { weekNode ->
                            val weekKey = "${yearNode.year}-${monthNode.month}-${weekNode.weekOfYear}"
                            item {
                                Box(
                                    Modifier.helpZonePartDisposable(
                                        helpZones, HelpTopic.STAT_SESSIONS, sessionsTreeParts, "week-$weekKey"
                                    )
                                ) {
                                TreeWeekRow(
                                    label         = weekNode.label,
                                    attempts      = weekNode.totalAttempts,
                                    geomEarlyMs   = weekNode.geomEarlyMs,
                                    geomLateMs    = weekNode.geomLateMs,
                                    wins          = weekNode.totalWins,
                                    luckyIndex    = weekNode.luckyIndex,
                                    expanded      = weekKey in expandedWeeks,
                                    summary       = weekNode.summary,
                                    onClick       = { onToggleWeek(weekKey) },
                                    helpMode      = helpMode
                                )
                                }
                            }
                            if (weekKey in expandedWeeks) {
                                weekNode.days.forEach { dayNode ->
                                    val dayKey = "${yearNode.year}-${monthNode.month}-${dayNode.day}"
                                    item {
                                        Box(
                                            Modifier.helpZonePartDisposable(
                                                helpZones, HelpTopic.STAT_SESSIONS, sessionsTreeParts, "day-$dayKey"
                                            )
                                        ) {
                                        TreeDayRow(
                                            label         = dayNode.label,
                                            attempts      = dayNode.totalAttempts,
                                            geomEarlyMs   = dayNode.geomEarlyMs,
                                            geomLateMs    = dayNode.geomLateMs,
                                            wins          = dayNode.totalWins,
                                            luckyIndex    = dayNode.luckyIndex,
                                            expanded      = dayKey in expandedDays,
                                            summary       = dayNode.summary,
                                            onClick       = { onToggleDay(dayKey) },
                                            helpMode      = helpMode
                                        )
                                        }
                                    }
                                    if (dayKey in expandedDays) {
                                        items(dayNode.sessions) { session ->
                                            Box(
                                                Modifier.helpZonePartDisposable(
                                                    helpZones, HelpTopic.STAT_SESSIONS, sessionsTreeParts,
                                                    "session-${session.sessionId}"
                                                )
                                            ) {
                                            TreeSessionRow(
                                                session      = session,
                                                expanded     = session.sessionId in expandedSessions,
                                                toleranceMs  = data.toleranceMs,
                                                onToggle     = { onToggleSession(session.sessionId) },
                                                helpMode     = helpMode
                                            )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // ── "Earn More Serenity" button — returns to main screen ─────────
        item {
            val accent = MaterialTheme.colorScheme.accentColor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(accent)
                    .clickable { onEarnMore() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text  = "EARN MORE SERENITY",
                    style = metricStyle(14.sp, FontWeight.Bold, Color.Black)
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Summary Header — верхняя карточка: цифры периода + мордочка отчёта.
//  Вопреки прежнему комментарию «всегда ALL_TIME», все значения здесь считаются
//  по ВЫБРАННОМУ периоду (AnalyticsUseCase складывает их из periodAttempts).
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryHeader(data: AnalyticsData, helpMode: Boolean = false) {
    // Две карточки в ряд: слева цифры (2/3 ширины), справа — кнопка отчёта
    // с мордочкой (1/3). Равной высоты (IntrinsicSize.Max).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // СЕТКА, А НЕ ДВЕ КОЛОНКИ (Alex 2026-09-27: «ни один заголовок не
            // идёт вровень с соседом справа»).
            //
            // Раньше это были два независимых Column. Высота строки в каждом
            // своя: у SERENITY COINS значение с иконкой монеты выше обычной
            // строки текста, длинная подпись могла перенестись на вторую
            // строку — и всё, что ниже, съезжало относительно соседней
            // колонки. Чем дальше вниз, тем сильнее расхождение.
            //
            // Теперь каждая ПАРА — это один Row из двух ячеек равной ширины.
            // Высота строки общая для обеих ячеек, обе начинаются от её
            // верха, поэтому заголовки стоят вровень по построению, а не
            // потому, что содержимое случайно совпало по высоте.
            val pair: @Composable (
                String, @Composable () -> Unit,
                String, @Composable () -> Unit
            ) -> Unit = { labelL, valueL, labelR, valueR ->
                // Общий кегль подписей этой строки. Любая из двух ячеек, не
                // поместившись, опускает его для ОБЕИХ — так пара всегда
                // одного размера и на одной линии. Шаг 0.5sp, ниже 7sp не
                // опускаемся; уменьшение только вниз, поэтому процесс
                // сходится за пару кадров и не зацикливается.
                var labelSp by remember { mutableStateOf(11.sp) }
                val shrink = {
                    if (labelSp.value > 7f) labelSp = (labelSp.value - 0.5f).sp
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SummaryStat(labelL, Modifier.weight(1f), labelSp, shrink, valueL)
                    SummaryStat(labelR, Modifier.weight(1f), labelSp, shrink, valueR)
                }
            }

            // Сколько сыграно и сколько попыток — наверху, вместе (Alex
            // 2026-09-27: «поставим количество попыток рядом со временем»):
            // оба отвечают на один вопрос — сколько было игры.
            pair(
                "Play time", { SummaryValue(formatDuration(data.periodPlayTimeMs)) },
                "Attempts",  { SummaryValue(formatNumber(data.periodAttempts.toString())) }
            )
            pair(
                "Sessions", { SummaryValue(formatNumber(data.periodSessionsCount.toString())) },
                "Serenity coins", {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        online.playoftime.ui_lib.CoinIcon(Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        // weight(1f) — значению достаётся ровно остаток строки
                        // после иконки, по нему оно и подгоняет шрифт.
                        SummaryValue(
                            groupDigits("%.2f".format(data.periodScm)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            )
            pair(
                "Wins/precise", {
                    SummaryValue(
                        "${formatNumber(data.periodWins.toString())}/${formatNumber(data.periodPrecise.toString())}",
                        color = resultGreenColor
                    )
                },
                "Pace of play", {
                    SummaryValue(
                        if (data.paceOfPlayPerMin > 0)
                            groupDigits("%.1f att/min".format(data.paceOfPlayPerMin))
                        else "—"
                    )
                }
            )
            // Practice — одна в строке: «Playing since» убрана (Alex 2026-10-05).
            // Пустая правая половина держит ту же ширину колонки, что у пар выше.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStat("Practice", Modifier.weight(1f)) {
                    SummaryValue(
                        if (data.practiceDensityMinPerDay > 0)
                            groupDigits("%.1f min/day".format(data.practiceDensityMinPerDay))
                        else "—"
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }

        ReportFaceCard(
            data     = data,
            helpMode = helpMode,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun SummaryStat(
    label: String,
    modifier: Modifier = Modifier,
    // Кегль подписи ОБЩИЙ на всю строку карточки, а не свой у каждой ячейки.
    // Раньше подпись ужималась сама по себе: длинная «SERENITY COINS»
    // садилась до 7.5sp, соседняя «SESSIONS» оставалась 9sp — разный размер,
    // разная базовая линия, и пара смотрелась вразнобой (Alex 2026-09-27:
    // «надписи в карточке выравнять забыл»). Теперь ячейка только СООБЩАЕТ,
    // что не помещается, а решение принимает строка — и уменьшает обе сразу.
    labelSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    onLabelOverflow: () -> Unit = {},
    value: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text     = label,
            style    = metricLabelStyle(labelSize),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.didOverflowWidth) onLabelOverflow() }
        )
        value()
    }
}

@Composable
private fun SummaryValue(text: String, modifier: Modifier = Modifier, color: Color = metricValueWhite) {
    // Тот же стиль, что у значений во всех остальных карточках — верхняя
    // карточка не выбивается из ряда (Alex 2026-09-27, спецификация 2026-10-05).
    FitValueText(
        text     = text,
        style    = metricValueStyle(color),
        modifier = modifier
    )
}

// ── Разделители разрядов и защита от разъезда ──────────────────────────────
//
// Alex 2026-09-27: «сделать разделители разрядов для всех значений» и
// «убедиться что ничего не разъедется при росте значений» — у Serenity Coins
// знак после запятой уезжал на следующую строку.
//
// Разделитель — НЕразрывный пробел. Выглядит как обычный, но внутри числа
// строка по нему не переносится, поэтому «23 945» не разорвётся на две
// строки даже там, где перенос разрешён.
private const val GROUP_SEP = '\u00A0'

private val INT_DIGIT_RUN = Regex("""(?<![\d.])\d{4,}""")

/**
 * Ставит разделители разрядов в целой части каждого числа внутри строки.
 *
 * Работает по готовому тексту, а не по числу, поэтому годится и для
 * «1234.56», и для «+1234ms», и для «1234 att/min» — везде, где значение
 * уже отформатировано под свою единицу измерения.
 *
 * Дробная часть не группируется: цифры после точки к разрядам не относятся,
 * за это отвечает отрицательный lookbehind на точку. Числа короче четырёх
 * цифр не трогаются — «999» остаётся «999».
 */
private fun groupDigits(text: String): String = INT_DIGIT_RUN.replace(text) { m ->
    m.value.reversed().chunked(3).joinToString(GROUP_SEP.toString()).reversed()
}

/** Целое число с разделителями разрядов. */
private fun formatNumber(value: String): String = groupDigits(value)

/**
 * Значение, которое не разъезжается. Пишется всегда одной строкой; если не
 * влезает в отведённую ширину — шрифт уменьшается шагами по 0.5sp до
 * [minSize], и только потом текст обрезается.
 *
 * Нужно потому, что значения растут вместе с историей игрока: Serenity Coins
 * за всё время, четырёхзначные миллисекунды в дереве, att4win на длинной
 * серии без побед. При фиксированном размере шрифта такое значение либо
 * переносилось на вторую строку (и ломало высоту карточки), либо обрезалось
 * без предупреждения.
 */
@Composable
private fun FitValueText(
    text     : String,
    style    : TextStyle,
    modifier : Modifier = Modifier,
    minSize  : androidx.compose.ui.unit.TextUnit = 8.sp
) {
    val startSize = style.fontSize
    var size by remember(text, startSize) { mutableStateOf(startSize) }
    BasicText(
        text     = text,
        modifier = modifier,
        style    = style.copy(fontSize = size),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth && size.value > minSize.value) {
                size = (size.value - 0.5f).sp
            }
        }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
//  Period Pill
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodPill(
    selected: PeriodFilter,
    onSelect: (PeriodFilter) -> Unit,
    helpActive: Boolean = false,
    onHelpClick: () -> Unit = {},
    onQuestionRect: (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent    = MaterialTheme.colorScheme.accentColor
    // Тот же индикатор баланса (red/blue), что и на вкладке Analytics нижнего
    // меню (Alex 2026-08-08) — заменяет прежнюю статичную bar-chart иконку.
    val storage      = org.koin.compose.koinInject<online.playoftime.core.data.LocalStorageService>()
    val balanceRed   = storage.getBalanceRed()
    val balanceBlue  = storage.getBalanceBlue()

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Название экрана заменено на индикатор баланса — идентифицирует экран
        // одним взглядом без съедания ширины у 4 фильтров (Session/Today/Month/All time).
        online.playoftime.ui_lib.BalanceGauge(
            redCount  = balanceRed,
            blueCount = balanceBlue,
            modifier  = Modifier
                .padding(end = 8.dp)
                .size(width = 24.dp, height = 12.dp)
        )
        // Порядок отображения НЕ равен ordinal-порядку enum'а (тот зафиксирован ради
        // персистентности — см. комментарий в PeriodFilter.kt): визуально Week логично
        // между Today и Month, а не в хвосте списка.
        listOf(PeriodFilter.SESSION, PeriodFilter.TODAY, PeriodFilter.WEEK, PeriodFilter.MONTH, PeriodFilter.ALL_TIME)
            .forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(period) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text  = period.label,
                    style = labelStyle(
                        size   = 11.sp,
                        weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color  = if (isSelected) accent else textColor.copy(alpha = subtleText)
                    )
                )
            }
        }

        // "?" — интерактивная справка (как на главном): тап включает режим,
        // затем тап по любой карточке показывает её описание.
        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 4.dp)
                .size(26.dp)
                .border(
                    width = 1.5.dp,
                    color = (if (helpActive) MaterialTheme.colorScheme.helpAccentColor else accent)
                        .copy(alpha = if (helpActive) 1f else 0.75f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .onGloballyPositioned { onQuestionRect(it.boundsInRoot()) }
                .clickable { onHelpClick() },
            contentAlignment = Alignment.Center
        ) {
            // В режиме подсказок круг и крестик жёлтые — тем же цветом, что бабл
            // подсказки (Alex 2026-09-27). Оранжевым крестик читался как обычная
            // кнопка приложения, а не как выход из справки.
            // "?" → "×" при входе в режим подсказок (тот же паттерн, что на главном
            // экране, Alex 2026-08-22) — тот же круг служит и выходом из режима.
            // На экране аналитики этого переключения не было (Alex 2026-09-09).
            BasicText(
                text  = if (helpActive) "×" else "?",
                style = TextStyle(
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = (if (helpActive) MaterialTheme.colorScheme.helpAccentColor else accent)
                        .copy(alpha = 0.95f)
                )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Range Bar (visual P3–P97 indicator)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun RangeBar(
    earlyMs: Double,
    lateMs: Double,
    earlyCount: Long = 0L,
    greenCount: Long = 0L,
    lateCount: Long  = 0L,
    topEarly: List<online.playoftime.features.stats.domain.entity.TopDeviation> = emptyList(),
    topLate:  List<online.playoftime.features.stats.domain.entity.TopDeviation> = emptyList()
) {
    val textColor    = MaterialTheme.colorScheme.baseTextColor
    val textMeasurer = rememberTextMeasurer()

    // Доля «0»/baseline-точки относительно ширины стрипа: |early| / (|early| + |late|).
    // Левый край стрипа = -|earlyMs|, правый край = +|lateMs|. Зелёный центр сдвигается.
    val totalDev   = abs(earlyMs) + abs(lateMs)
    val centerFrac = if (totalDev > 0.0)
        (abs(earlyMs) / totalDev).toFloat().coerceIn(0.02f, 0.98f)
    else 0.5f

    val totalCount = (earlyCount + greenCount + lateCount).coerceAtLeast(1L)
    val redFrac    = earlyCount.toFloat() / totalCount
    val grnFrac    = greenCount.toFloat() / totalCount
    val bluFrac    = lateCount.toFloat() / totalCount

    val countStyle = TextStyle(
        fontFamily    = InterFamily,
        fontSize      = 9.sp,
        fontWeight    = FontWeight.Bold,
        color         = Color.White.copy(alpha = 0.90f),
        letterSpacing = 0.em
    )
    // Текст счётчика в ЗЕЛЁНОМ сегменте — чёрный, лучше читается на зелёном фоне.
    val greenCountStyle = countStyle.copy(color = Color.Black)
    val captionStyle = TextStyle(
        fontFamily    = InterFamily,
        fontSize      = 9.sp,
        fontWeight    = FontWeight.Medium,
        color         = textColor.copy(alpha = 0.53f),
        letterSpacing = 0.05.em
    )
    val modeLabelStyle = TextStyle(
        fontFamily    = InterFamily,
        fontSize      = 9.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 0.em
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Upper stripe: attempts ratio (red / green / blue proportions) ──
        BasicText(text = "attempts ratio", style = captionStyle)
        Spacer(Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val cornerPx = 3.dp.toPx()
            val w = size.width
            val rW = w * redFrac
            val gW = w * grnFrac
            val bW = w * bluFrac
            val barPath = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        0f, 0f, w, size.height, CornerRadius(cornerPx)
                    )
                )
            }
            clipPath(barPath) {
                drawRect(resultRedColor,   topLeft = Offset(0f, 0f),         size = Size(rW, size.height))
                drawRect(resultGreenColor, topLeft = Offset(rW, 0f),         size = Size(gW, size.height))
                drawRect(resultBlueColor,  topLeft = Offset(rW + gW, 0f),    size = Size(bW, size.height))
            }
            // Inline counts (only if segment wide enough)
            val cy = size.height / 2f
            // Разделитель разрядов пробелом для трёхзначных+ значений.
            fun fmtCount(n: Long): String =
                groupDigits(n.toString())

            if (earlyCount > 0 && rW > 16f) {
                val ml = textMeasurer.measure(fmtCount(earlyCount), countStyle)
                drawText(ml, topLeft = Offset(rW / 2f - ml.size.width / 2f, cy - ml.size.height / 2f))
            }
            if (greenCount > 0 && gW > 16f) {
                val ml = textMeasurer.measure(fmtCount(greenCount), greenCountStyle)
                drawText(ml, topLeft = Offset(rW + gW / 2f - ml.size.width / 2f, cy - ml.size.height / 2f))
            }
            if (lateCount > 0 && bW > 16f) {
                val ml = textMeasurer.measure(fmtCount(lateCount), countStyle)
                drawText(ml, topLeft = Offset(rW + gW + bW / 2f - ml.size.width / 2f, cy - ml.size.height / 2f))
            }
        }

        // ── Serenity point — два треугольника + тонкая связующая линия ────
        // Вершина каждого треугольника упирается ровно в свою полоску:
        //  · верхняя вершина — в нижнюю грань attempts ratio;
        //  · нижняя вершина — в верхнюю грань range of deviation.
        // «Serenity point» — справа от связки, «range of deviation» — слева,
        // прижата к самой нижней полоске (внутри того же Canvas).
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(27.dp)      // расстояние между полосками увеличено (+1/3)
        ) {
            // Клювик — НЕПОДВИЖНАЯ цель, всегда в геометрическом центре обеих
            // полосок. Задача игрока — сдвинуть баланс так, чтобы граница цветов
            // на полосках (пропорциональная отклонениям) совпала с клювиком.
            val cx        = size.width / 2f
            val h         = size.height
            // Ярко-продолговатые «клювики»: очень узкое основание, высокая
            // вершина. Отношение 4:11 — визуально это клюв, а не стрелка.
            val halfBase  = 2.dp.toPx()
            val triH      = 11.dp.toPx()
            val gapPx     = 6.dp.toPx()

            // Верхний треугольник: вершина в (cx, 0), основание на y = triH
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - halfBase, triH)
                lineTo(cx + halfBase, triH)
                lineTo(cx, 0f)
                close()
            }, resultGreenColor)

            // Тонкая соединительная линия между основаниями
            drawLine(
                color = resultGreenColor,
                start = Offset(cx, triH),
                end   = Offset(cx, h - triH),
                strokeWidth = 1.5.dp.toPx()
            )

            // Нижний треугольник: основание на y = h - triH, вершина в (cx, h)
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - halfBase, h - triH)
                lineTo(cx + halfBase, h - triH)
                lineTo(cx, h)
                close()
            }, resultGreenColor)

            // Подпись «Serenity point» — справа от связки, центрирована по вертикали.
            val capRight = textMeasurer.measure("Serenity point", captionStyle)
            drawText(capRight,
                topLeft = Offset(
                    x = (cx + halfBase + gapPx),
                    y = (h - capRight.size.height) / 2f
                )
            )

            // Подпись «range of deviation» — СЛЕВА от связки, прижата к нижней
            // грани Canvas (т.е. вплотную над нижней полоской).
            val capLeft = textMeasurer.measure("range of deviation", captionStyle)
            drawText(capLeft,
                topLeft = Offset(
                    x = 0f,
                    y = h - capLeft.size.height
                )
            )
        }

        // ── Нижняя полоска: range of deviation ──────────────────────────
        // Градиент red → blue; значения −59ms / +56ms сидят ВНУТРИ полоски
        // белым цветом, слева/справа. На полоске также стоят 1-px вертикальные
        // отметки на позициях модусов на всю высоту полоски.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stripWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }

            // Все модусы (early + late) — со счётчиком повторений — раскладываем
            // в 3 «этажа» по частоте. Верхний этаж (0) — самые частые, нижний (2) —
            // самые редкие. Внутри этажа сортируем по errorMs (слева направо по
            // положению на полоске).
            val modeRows = remember(topEarly, topLate) {
                assignModesToFloors(topEarly, topLate)
            }

            val rangeStart = -abs(earlyMs)
            val span       = abs(earlyMs) + abs(lateMs)
            val valueStyleWhite = metricStyle(11.sp, FontWeight.SemiBold, Color.White)
            val stripHeight = 22.dp

            Column(Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                ) {
                    val cornerPx = 6.dp.toPx()

                    // Красная и синяя зоны ПРОПОРЦИОНАЛЬНЫ величинам отклонений:
                    // граница цветов = centerFrac = |early| / (|early| + |late|) —
                    // ровно там, где на полоске сидит нулевая точка. Переход резкий
                    // (два плоских прямоугольника), без градиента. Раньше стык был
                    // хардкодом в центре (0.45..0.55) и при −105/+81 врал на ~6%.
                    val splitX = size.width * centerFrac
                    val stripPath = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                0f, 0f, size.width, size.height, CornerRadius(cornerPx)
                            )
                        )
                    }
                    clipPath(stripPath) {
                        drawRect(resultRedColor,
                            topLeft = Offset(0f, 0f),
                            size    = Size(splitX, size.height))
                        drawRect(resultBlueColor,
                            topLeft = Offset(splitX, 0f),
                            size    = Size(size.width - splitX, size.height))
                    }

                    // Значения P3/P97 ВНУТРИ полоски — белым цветом.
                    val leftText  = formatMs(earlyMs)
                    val rightText = formatMs(lateMs)
                    val leftMl    = textMeasurer.measure(leftText,  valueStyleWhite)
                    val rightMl   = textMeasurer.measure(rightText, valueStyleWhite)
                    val innerPad  = 8.dp.toPx()
                    drawText(leftMl,
                        topLeft = Offset(innerPad,
                            (size.height - leftMl.size.height) / 2f))
                    drawText(rightMl,
                        topLeft = Offset(size.width - rightMl.size.width - innerPad,
                            (size.height - rightMl.size.height) / 2f))

                    // 1-px вертикальные отметки на позициях всех модусов
                    // на ВСЮ высоту полоски.
                    if (span > 0.0) {
                        val tickColor = Color.White.copy(alpha = 0.95f)
                        val tickPx    = 1.dp.toPx().coerceAtLeast(1f)
                        modeRows.forEach { (_, dev) ->
                            val frac = ((dev.errorMs - rangeStart) / span)
                                .toFloat().coerceIn(0f, 1f)
                            val x = size.width * frac
                            drawLine(
                                color       = tickColor,
                                start       = Offset(x, 0f),
                                end         = Offset(x, size.height),
                                strokeWidth = tickPx
                            )
                        }
                    }
                }

                // ── Подписи модусов под полоской ─────────────────────────────
                // Красный (early) и синий (late) модусы на ОДНОМ этаже. Значения
                // подписаны БЕЗ знака (сам знак — это цвет). Соединители сверху
                // строго вертикально-горизонтальные: если подписи «встают» на своих
                // штатных позициях без наложения — обе идут прямыми вертикальными
                // линиями; если пересекаются — раздвигаем ОБЕ по краям и рисуем
                // П-образные (вертикаль → горизонталь → вертикаль) выноски.
                // Весь этаж (Modes + значения) обведён тонкой линией.
                if (modeRows.isNotEmpty() && span > 0.0) {
                    val floorHeightDp     = 15.dp
                    val floorFormatting   = { dev: online.playoftime.features.stats.domain.entity.TopDeviation ->
                        // Разделитель разрядов пробелом для count > 999.
                        val countStr = groupDigits(dev.count.toString())
                        "%.3f x %s".format(abs(dev.errorMs) / 1000.0, countStr)
                    }
                    val sortedModes = modeRows.sortedBy { it.second.errorMs }

                    // Заранее размещаем метки, вычисляем нужно ли переходить в
                    // «разнесённый» режим и сохраняем результат для использования
                    // как соединителями сверху, так и подписями ниже.
                    data class LayoutItem(
                        val dev: online.playoftime.features.stats.domain.entity.TopDeviation,
                        val labelX: Float, val labelW: Float,
                        val color: Color, val text: String,
                        val stripFrac: Float,
                        val spread: Boolean
                    )

                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val innerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            maxWidth.toPx()
                        }
                        val padPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            6.dp.toPx()
                        }
                        // Метка переименована «Modes» → «Top-frequency error» (Alex
                        // 2026-09-10, везде по приложению) — заметно длиннее, поэтому
                        // резерв слева больше не фиксированные 42dp, а РЕАЛЬНАЯ ширина
                        // новой метки (измерена textMeasurer'ом) + её отступ + зазор.
                        val leftReservePx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            textMeasurer.measure("Top-frequency error", captionStyle).size.width.toFloat() +
                                6.dp.toPx() + 6.dp.toPx()
                        }
                        val comfortGapPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            30.dp.toPx()   // ~0.5 cm — комфортное расстояние для чтения
                        }
                        // «Разносим» подписи только когда они РЕАЛЬНО налезают друг
                        // на друга (0 — они соприкасаются). Небольшой отступ 2 px —
                        // чтобы не касались вплотную даже кернингом.
                        // Спред включается ТОЛЬКО когда подписи реально пересекаются.
                        val minLabelGapPx = 0f

                        val layout = remember(sortedModes, innerWidthPx) {
                            val prepared = sortedModes.map { (_, dev) ->
                                val early = dev.errorMs < 0
                                val text  = floorFormatting(dev)
                                val color = if (early) resultRedColor else resultBlueColor
                                val ml = textMeasurer.measure(text, modeLabelStyle.copy(color = color))
                                val frac  = ((dev.errorMs - rangeStart) / span)
                                    .toFloat().coerceIn(0f, 1f)
                                Triple(dev, ml, color to text).let {
                                    LayoutItem(dev, 0f, ml.size.width.toFloat(),
                                        color, text, frac, false)
                                }
                            }
                            // Идеальные (по x) позиции каждой подписи, центрированные
                            // на её frac. Не даём подписи налезать на зарезервированную
                            // левую полосу под «Top-frequency error».
                            val ideal = prepared.map { item ->
                                val centered = innerWidthPx * item.stripFrac - item.labelW / 2f
                                val minX = leftReservePx
                                val maxX = innerWidthPx - item.labelW - padPx
                                item.copy(labelX = centered.coerceIn(minX, maxX))
                            }
                            // Проверяем наложение между соседями. Если хоть одно —
                            // включаем «spread» режим: обе подписи уезжают к краям.
                            val overlapping = ideal.zipWithNext().any { (a, b) ->
                                a.labelX + a.labelW + minLabelGapPx > b.labelX
                            }
                            if (!overlapping) ideal
                            else {
                                // Спред НЕ по краям — по расстоянию, комфортному для
                                // чтения (~0.5 см = 30 dp зазор между подписями).
                                val n = ideal.size
                                if (n == 2) {
                                    val a = ideal[0]; val b = ideal[1]
                                    // Мидпоинт по идеальным центрам обеих подписей.
                                    val aCenter = a.labelX + a.labelW / 2f
                                    val bCenter = b.labelX + b.labelW / 2f
                                    val mid     = (aCenter + bCenter) / 2f
                                    // Целевые позиции: слева/справа от mid с зазором.
                                    var aX = mid - comfortGapPx / 2f - a.labelW
                                    var bX = mid + comfortGapPx / 2f
                                    // Не наезжать на «Top-frequency error» слева и правый край справа.
                                    if (aX < leftReservePx) {
                                        val delta = leftReservePx - aX
                                        aX += delta; bX += delta
                                    }
                                    val rightMax = innerWidthPx - b.labelW - padPx
                                    if (bX > rightMax) {
                                        val delta = bX - rightMax
                                        aX -= delta; bX -= delta
                                    }
                                    listOf(a.copy(labelX = aX, spread = true),
                                           b.copy(labelX = bX, spread = true))
                                } else {
                                    // ≥ 3 подписей: равномерно распределяем в оставшемся
                                    // окне с зазором ≥ comfortGapPx между соседями.
                                    ideal.mapIndexed { idx, item ->
                                        val slot = (innerWidthPx - leftReservePx - padPx) *
                                            (idx.toFloat() / (n - 1))
                                        val x = (leftReservePx + slot - item.labelW / 2f)
                                            .coerceIn(leftReservePx,
                                                innerWidthPx - item.labelW - padPx)
                                        item.copy(labelX = x, spread = true)
                                    }
                                }
                            }
                        }

                        val useSpread = layout.any { it.spread }
                        val connectorHeightDp = if (useSpread) 14.dp else 10.dp

                        Column(Modifier.fillMaxWidth()) {
                            // Соединители сверху. Прямая вертикаль, если подпись под
                            // своей отметкой. П-образная (вниз-вбок-вниз), если
                            // подпись разнесена.
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(connectorHeightDp)
                            ) {
                                val lineColor = Color.White.copy(alpha = 0.85f)
                                val lineW     = 1.dp.toPx().coerceAtLeast(1f)
                                val elbowY    = size.height * 0.5f
                                layout.forEach { item ->
                                    val stripX = size.width * item.stripFrac
                                    val labelCenterX = item.labelX + item.labelW / 2f
                                    if (!useSpread && kotlin.math.abs(labelCenterX - stripX) < 1f) {
                                        // Прямая вертикальная линия
                                        drawLine(lineColor,
                                            start = Offset(stripX, 0f),
                                            end   = Offset(stripX, size.height),
                                            strokeWidth = lineW)
                                    } else {
                                        // П-образный маршрут: вниз, вбок (гориз.), вниз.
                                        drawLine(lineColor,
                                            start = Offset(stripX, 0f),
                                            end   = Offset(stripX, elbowY),
                                            strokeWidth = lineW)
                                        drawLine(lineColor,
                                            start = Offset(stripX, elbowY),
                                            end   = Offset(labelCenterX, elbowY),
                                            strokeWidth = lineW)
                                        drawLine(lineColor,
                                            start = Offset(labelCenterX, elbowY),
                                            end   = Offset(labelCenterX, size.height),
                                            strokeWidth = lineW)
                                    }
                                }
                            }
                            // Этаж с подписями. Слева — заголовок «Top-frequency error»
                            // (переименовано из «Modes» — Alex 2026-09-10, везде по
                            // приложению). Весь блок обведён тонкой линией.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(floorHeightDp)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            ) {
                                BasicText(
                                    text     = "Top-frequency error",
                                    style    = captionStyle,
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 6.dp)
                                )
                                Canvas(Modifier.fillMaxSize()) {
                                    layout.forEach { item ->
                                        val ml = textMeasurer.measure(
                                            item.text,
                                            modeLabelStyle.copy(color = item.color)
                                        )
                                        val y = (size.height - ml.size.height) / 2f
                                        drawText(ml, topLeft = Offset(item.labelX, y))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Один «этаж» подписей модусов: вертикальные отметки уже нарисованы на самой
 * полоске, здесь просто расставляем текст по x-позиции каждого модуса.
 * Опциональная подпись слева (например «Top-frequency error» на среднем этаже).
 */
/**
 * Оставляем ровно по ОДНОЙ моде на цвет — самую частую среди early (красные) и
 * самую частую среди late (синие). Обе моды теперь на одном этаже, поэтому
 * значение floor всегда 0. Все остальные модусы (и их отметки на ленте) не рисуем.
 */
private fun assignModesToFloors(
    topEarly: List<online.playoftime.features.stats.domain.entity.TopDeviation>,
    topLate:  List<online.playoftime.features.stats.domain.entity.TopDeviation>
): List<Pair<Int, online.playoftime.features.stats.domain.entity.TopDeviation>> {
    val bestEarly = topEarly.maxByOrNull { it.count }
    val bestLate  = topLate.maxByOrNull  { it.count }
    return listOfNotNull(bestEarly, bestLate).map { 0 to it }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Top Deviations
// ──────────────────────────────────────────────────────────────────────────────

// ──────────────────────────────────────────────────────────────────────────────
//  Stat Card (универсальная карточка)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    title   : String,
    period  : String = "",
    header  : (@Composable () -> Unit)? = null,
    content : @Composable () -> Unit
) {
    // Заголовок блока — над карточкой, а не внутри неё (Alex 2026-10-05,
    // по макету дизайнера).
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (header != null) header() else BasicText(text = title, style = blockTitleStyle)
            if (period.isNotEmpty()) {
                BasicText(text = period, style = blockTitleStyle)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun CardMetricRow(
    label      : String,
    value      : String,
    valueColor : Color = Color.Unspecified,
    note       : String? = null
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // weight(1f) у подписи (Alex 2026-09-27): длинная подпись с пояснением
        // теперь переносится внутри своей доли строки, а не отжимает значение
        // к правому краю, пока то не начнёт переноситься само.
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text  = label,
                style = metricLabelStyle()
            )
            if (note != null) BasicText(
                text  = note,
                style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
            )
        }
        Spacer(Modifier.width(8.dp))
        FitValueText(
            text  = groupDigits(value),
            style = metricValueStyle(if (valueColor == Color.Unspecified) metricValueWhite else valueColor)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Дерево сессий
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Magnitude only — "34" / "21"; направление кодируется цветом (red = early,
 * blue = late), знаки +/− убраны (Alex 2026-07-12). "—" if no data.
 */
private fun formatGeomMs(ms: Double, early: Boolean): String =
    if (ms <= 0.0) "—" else groupDigits(ms.roundToInt().toString())

/** attempts ÷ wins (с допуском, как Attempts per win на карточке), "—" если побед нет. */
private fun formatAttemptsPerWin(attempts: Int, wins: Int): String {
    if (wins <= 0) return "—"
    val ratio = attempts.toDouble() / wins
    return groupDigits("%.1f".format(ratio))
}

/** Lucky index formatted as "0.87". "—" if lucky index is 0.0 (нет данных / нет побед). */
private fun formatLucky(l: Double): String =
    if (l <= 0.0) "—" else "%.2f".format(l)

@Composable
private fun TreeYearRow(year: Int, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, wins: Int, luckyIndex: Double, expanded: Boolean, summary: NodeSummary, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(0.dp, year.toString(), attempts, geomEarlyMs, geomLateMs, wins, luckyIndex, expanded,
        metricStyle(14.sp, FontWeight.Bold, MaterialTheme.colorScheme.baseTextColor), onClick,
        summary = summary, helpMode = helpMode)

@Composable
private fun TreeMonthRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, wins: Int, luckyIndex: Double, expanded: Boolean, summary: NodeSummary, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(12.dp, label, attempts, geomEarlyMs, geomLateMs, wins, luckyIndex, expanded,
        metricStyle(13.sp, FontWeight.Medium, MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.90f)), onClick,
        summary = summary, helpMode = helpMode)

@Composable
private fun TreeWeekRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, wins: Int, luckyIndex: Double, expanded: Boolean, summary: NodeSummary, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(18.dp, label, attempts, geomEarlyMs, geomLateMs, wins, luckyIndex, expanded,
        metricStyle(12.5.sp, FontWeight.Medium, MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.84f)), onClick,
        summary = summary, helpMode = helpMode)

@Composable
private fun TreeDayRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, wins: Int, luckyIndex: Double, expanded: Boolean, summary: NodeSummary, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(24.dp, label, attempts, geomEarlyMs, geomLateMs, wins, luckyIndex, expanded,
        labelStyle(12.sp, color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.78f)), onClick,
        summary = summary, helpMode = helpMode)

@Composable
private fun TreeRow(
    indent        : Dp,
    label         : String,
    attempts      : Int,
    geomEarlyMs   : Double,
    geomLateMs    : Double,
    wins          : Int,
    luckyIndex    : Double,
    expanded      : Boolean,
    labelStyle2   : TextStyle,
    onClick       : () -> Unit,
    // Палец, длительность и процент побед узла — см. NodeSummary.
    summary       : NodeSummary,
    // В режиме справки тап по строке дерева должен показывать бабл STAT_SESSIONS
    // (через helpTapGestures на внешнем Box), а не разворачивать/сворачивать
    // строку — иначе .clickable() ниже consume'ит тап раньше, чем очередь
    // дойдёт до обработчика справки (Alex 2026-09-10: "спрака должна
    // реагировать соответственно" при выборе в дереве лога/месяцев/недель/
    // дней). enabled=false здесь НЕ consume'ит тап — он проходит насквозь.
    helpMode      : Boolean = false
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val thumbUp = summary.thumbUp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !helpMode) { onClick() }
            .padding(start = indent, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = if (expanded) "▾ " else "▸ ", style = labelStyle2.copy(color = labelStyle2.color.copy(alpha = 0.6f)))
            BasicText(text = label, style = labelStyle2)
            if (thumbUp != null) {
                online.playoftime.ui_lib.Icon(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(11.dp)
                        .graphicsLayer { if (!thumbUp) rotationZ = 180f },
                    painter = androidx.compose.ui.res.painterResource(
                        online.playoftime.R.drawable.ic_thumb_up
                    ),
                    color = if (thumbUp) resultGreenColor else Color(0xFFB8B8B8)
                )
            }
        }
        // Lucky index — leftmost data column; центрирован по ширине столбца,
        // как его заголовок "lucky" (Alex 2026-07-12).
        val luckyColor = if (luckyIndex <= 0.0)
            textColor.copy(alpha = subtleText) else textColor
        FitValueText(
            modifier = Modifier.width(TREE_COL_LUCKY),
            text  = formatLucky(luckyIndex),
            style = metricStyle(11.sp, FontWeight.Normal, luckyColor)
                .copy(textAlign = TextAlign.Center)
        )
        // Geom. mean — early(red) / late(blue) error, ms
        SlashCell(
            leftText   = formatGeomMs(geomEarlyMs, early = true),
            rightText  = formatGeomMs(geomLateMs, early = false),
            leftStyle  = metricStyle(11.sp, FontWeight.Normal, resultRedColor.copy(alpha = 0.9f)),
            slashStyle = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.48f)),
            rightStyle = metricStyle(11.sp, FontWeight.Normal, resultBlueColor.copy(alpha = 0.9f)),
            leftW = TREE_EL_LEFT_W, slashW = TREE_EL_SLASH_W, rightW = TREE_EL_RIGHT_W
        )
        // att4win — attempts per one clean-three win; центрирован по столбцу
        FitValueText(
            modifier = Modifier.width(TREE_COL_ATT4WIN),
            text  = formatAttemptsPerWin(attempts, wins),
            style = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.58f))
                .copy(textAlign = TextAlign.Center)
        )
    }
    // Длительность и процент побед — та же подстрока, что у сессии.
    Row(
        modifier              = Modifier.fillMaxWidth().padding(start = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        BasicText(
            text  = formatDuration(summary.playMs),
            style = metricStyle(10.sp, FontWeight.Normal, textColor.copy(alpha = 0.48f))
        )
        BasicText(
            text  = "${summary.winPct.roundToInt()}% ✓",
            style = metricStyle(10.sp, FontWeight.Medium, resultGreenColor.copy(alpha = 0.85f))
        )
    }
    }
}

@Composable
private fun TreeSessionRow(
    session     : SessionTreeData,
    expanded    : Boolean,
    toleranceMs : Double = 0.0,
    onToggle    : () -> Unit,
    helpMode    : Boolean = false
) {
    val textColor  = MaterialTheme.colorScheme.baseTextColor
    // Время старта HH:mm вместо даты (Alex 2026-07-12): дата избыточна —
    // родительский узел дерева и есть день.
    val sdf        = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr   = "Session #${session.sessionNumber}  ${sdf.format(Date(session.startTime))}"
    // Play Time сессии = только время когда MainScreen был активен (activeTimeMs),
    // а не wall-clock (endTime − startTime). Это исключает минуты, проведённые на
    // других экранах или в background.
    val durationMs = session.activeTimeMs
    val winPct     = if (session.attempts > 0)
        (session.wins.toDouble() / session.attempts * 100).roundToInt() else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // enabled=false в help-режиме — тап проходит насквозь к
            // helpTapGestures на внешнем Box вместо разворачивания сессии.
            .clickable(enabled = !helpMode) { onToggle() }
            .padding(start = 36.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ── Header row ───────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text  = if (expanded) "▾ " else "▸ ",
                    style = labelStyle(9.sp, color = textColor.copy(alpha = 0.38f))
                )
                BasicText(
                    text  = startStr,
                    style = metricStyle(11.sp, FontWeight.Medium, textColor.copy(alpha = 0.83f))
                )
                // Индикатор успешности сессии (Alex 2026-07-23): зелёный палец
                // вверх — квест взят; серый вниз — без победы; активная — без иконки.
                if (session.thumbUp != null) {
                    online.playoftime.ui_lib.Icon(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(11.dp)
                            .graphicsLayer { if (session.thumbUp == false) rotationZ = 180f },
                        painter = androidx.compose.ui.res.painterResource(
                            online.playoftime.R.drawable.ic_thumb_up
                        ),
                        color = if (session.thumbUp == true) resultGreenColor
                                else Color(0xFFB8B8B8)
                    )
                }
            }
            // Lucky index for the session — центрирован по столбцу. Если ни одной
            // тройки — вместо «—» пишем «no luck» (Alex 2026-07-26).
            val lucky = session.luckyIndex()
            val noLuck = lucky <= 0.0
            val luckyColor = if (noLuck)
                textColor.copy(alpha = subtleText) else textColor
            FitValueText(
                modifier = Modifier.width(TREE_COL_LUCKY),
                text  = if (noLuck) "no luck" else formatLucky(lucky),
                minSize = 7.sp,
                style = metricStyle(if (noLuck) 8.sp else 11.sp, FontWeight.Normal, luckyColor)
                    .copy(textAlign = TextAlign.Center)
            )
            // Geom. mean — early(red) / late(blue) error, ms
            SlashCell(
                leftText   = formatGeomMs(session.geomEarlyMs(), early = true),
                rightText  = formatGeomMs(session.geomLateMs(), early = false),
                leftStyle  = metricStyle(11.sp, FontWeight.Normal, resultRedColor.copy(alpha = 0.9f)),
                slashStyle = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.48f)),
                rightStyle = metricStyle(11.sp, FontWeight.Normal, resultBlueColor.copy(alpha = 0.9f)),
                leftW = TREE_EL_LEFT_W, slashW = TREE_EL_SLASH_W, rightW = TREE_EL_RIGHT_W
            )
            // att4win — attempts per one clean-three win; центрирован по столбцу
            FitValueText(
                modifier = Modifier.width(TREE_COL_ATT4WIN),
                text  = formatAttemptsPerWin(session.attempts, session.wins),
                style = labelStyle(11.sp, color = textColor.copy(alpha = 0.58f))
                    .copy(textAlign = TextAlign.Center)
            )
        }

        // ── Duration + win% sub-row ──────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BasicText(
                text  = formatDuration(durationMs),
                style = metricStyle(10.sp, FontWeight.Normal, textColor.copy(alpha = 0.48f))
            )
            BasicText(
                text  = "$winPct% ✓",
                style = metricStyle(10.sp, FontWeight.Medium, resultGreenColor.copy(alpha = 0.85f))
            )
        }

        // ── Quest text ───────────────────────────────────────────────────
        if (session.questLevelId > 0 || session.questTaskId > 0) {
            BasicText(
                text  = "LVL ${session.questLevelId}  •  task ${session.questTaskId}",
                style = labelStyle(9.sp, color = textColor.copy(alpha = 0.43f))
            )
        }

        // ── Crown marker ─────────────────────────────────────────────────
        if (session.isQuestComplete) {
            BasicText(
                text  = "👑 Quest done",
                style = labelStyle(10.sp, FontWeight.SemiBold, Color(0xFFFFD700))
            )
        }

        // ── Attempt bars + mini histogram (expanded only) ────────────────
        if (expanded && session.attemptBars.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            AttemptBarsSection(session.attemptBars)
            Spacer(Modifier.height(8.dp))
            MiniHistogram(session.attemptBars, toleranceMs)
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Attempt Bars (развёрнутая сессия)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun AttemptBarsSection(bars: List<online.playoftime.features.stats.domain.entity.AttemptBarData>) {
    val maxAbs = bars.maxOf { abs(it.errorMs) }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        bars.forEach { bar -> AttemptBarRow(bar = bar, maxAbsMs = maxAbs) }
    }
}

@Composable
private fun AttemptBarRow(
    bar      : online.playoftime.features.stats.domain.entity.AttemptBarData,
    maxAbsMs : Double
) {
    val barColor = when {
        bar.isWin       -> resultGreenColor
        bar.errorMs < 0 -> resultRedColor
        else            -> resultBlueColor
    }
    val textColor  = MaterialTheme.colorScheme.baseTextColor
    val bgColor    = MaterialTheme.colorScheme.baseBackgroundColor
    val textMeasurer = rememberTextMeasurer()

    val timeStr = if (bar.durationMs > 0L)
        "%.${bar.accuracy}f".format(bar.durationMs / 1000.0) else ""

    val errorText: String = if (!bar.isWin) {
        val absVal = abs(bar.errorMs / 1000.0)
        val fmt    = when (bar.accuracy) { 3 -> "%.3f"; 1 -> "%.1f"; 0 -> "%.0f"; else -> "%.2f" }
        val raw    = fmt.format(absVal).removePrefix("0")
        if (bar.errorMs < 0) "-$raw" else raw
    } else ""

    val valueStyle = TextStyle(
        fontSize   = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color      = barColor
    )
    val measuredText = if (errorText.isNotEmpty()) textMeasurer.measure(errorText, valueStyle) else null

    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Номер
        BasicText(
            text     = bar.number.toString(),
            modifier = Modifier.width(20.dp),
            style    = labelStyle(9.sp, color = textColor.copy(alpha = 0.43f))
        )
        // Время
        BasicText(
            text     = timeStr,
            modifier = Modifier.width(44.dp),
            style    = TextStyle(
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                color      = barColor
            )
        )
        // Центрированный бар (аналог SessionStatisticItem на главном экране)
        Canvas(modifier = Modifier.weight(1f).height(20.dp)) {
            val w        = size.width
            val cx       = w / 2f
            val cy       = size.height / 2f
            val scaleMax = maxAbsMs.coerceAtLeast(1.0)
            val fraction = (abs(bar.errorMs) / scaleMax).coerceIn(0.0, 1.0).toFloat()
            val barLen   = cx * fraction
            val barH     = 2.dp.toPx()
            val dotR     = 3.5.dp.toPx()
            val textGap  = 3.dp.toPx()
            val cornerR  = CornerRadius(barH / 2f)

            // Центральная ось (ноль)
            drawLine(
                color       = textColor.copy(alpha = 0.12f),
                start       = Offset(cx, 0f),
                end         = Offset(cx, size.height),
                strokeWidth = 0.8.dp.toPx()
            )

            when {
                bar.isWin -> drawCircle(resultGreenColor, radius = dotR, center = Offset(cx, cy))

                bar.errorMs < 0 -> {
                    drawRoundRect(
                        color        = resultRedColor,
                        topLeft      = Offset(cx - barLen, cy - barH / 2f),
                        size         = Size(barLen, barH),
                        cornerRadius = cornerR
                    )
                    measuredText?.let { ml ->
                        val tw   = ml.size.width.toFloat()
                        val th   = ml.size.height.toFloat()
                        val idealTx = cx - barLen - textGap - tw
                        val tx   = if (idealTx >= 0f) idealTx else (cx - barLen + textGap).coerceAtMost(cx - tw)
                        val ty   = cy - th / 2f
                        drawRect(bgColor, topLeft = Offset(tx - 1f, ty - 1f), size = Size(tw + 2f, th + 2f))
                        drawText(ml, topLeft = Offset(tx, ty))
                    }
                }

                else -> {
                    drawRoundRect(
                        color        = resultBlueColor,
                        topLeft      = Offset(cx, cy - barH / 2f),
                        size         = Size(barLen, barH),
                        cornerRadius = cornerR
                    )
                    measuredText?.let { ml ->
                        val tw   = ml.size.width.toFloat()
                        val th   = ml.size.height.toFloat()
                        val idealTx = cx + barLen + textGap
                        val tx   = if (idealTx + tw <= w) idealTx else (cx + barLen - textGap - tw).coerceAtLeast(cx)
                        val ty   = cy - th / 2f
                        drawRect(bgColor, topLeft = Offset(tx - 1f, ty - 1f), size = Size(tw + 2f, th + 2f))
                        drawText(ml, topLeft = Offset(tx, ty))
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Mini Histogram (распределение внутри сессии)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniHistogram(
    bars        : List<online.playoftime.features.stats.domain.entity.AttemptBarData>,
    toleranceMs : Double = 0.0
) {
    if (bars.size < 2) return
    val textColor = MaterialTheme.colorScheme.baseTextColor

    val errors = bars.map { it.errorMs }
    val minE = errors.min()
    val maxE = errors.max()
    if (maxE - minE < 1.0) return

    // Symmetric scale around zero — zero is always at canvas center.
    // 71 бакет и тонкие столбики-иглы — тот же стиль, что DistribChart на
    // главном экране (Alex 2026-07-23: «как на рисунке 1, не skyline»).
    val scaleMax = maxOf(abs(minE), abs(maxE)).coerceAtLeast(1.0)
    val rangeMin = -scaleMax
    val bucketCount = 71
    val bucketW = (2.0 * scaleMax) / bucketCount
    val counts  = IntArray(bucketCount)
    val wins    = IntArray(bucketCount)
    bars.forEach { b ->
        val idx = ((b.errorMs - rangeMin) / bucketW).toInt().coerceIn(0, bucketCount - 1)
        counts[idx]++
        if (b.isWin) wins[idx]++
    }
    val maxCount = counts.max().coerceAtLeast(1)

    // Same horizontal layout as AttemptBarRow: padding(12dp) + label(64dp) + canvas(weight=1f)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicText(
            text     = "distribution",
            modifier = Modifier.width(64.dp),
            style    = labelStyle(8.sp, color = textColor.copy(alpha = 0.25f))
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
        ) {
            val w    = size.width
            val gap  = w / bucketCount
            // Тонкие иглы: 60% слота, зажато между 0.5dp и 3dp — как в
            // DistribChart на главном экране.
            val barW    = (gap * 0.6f).coerceIn(0.5.dp.toPx(), 3.dp.toPx())
            val cornerR = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())

            // Centre line at w/2 (zero)
            drawLine(
                color       = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                start       = Offset(w / 2f, 0f),
                end         = Offset(w / 2f, size.height),
                strokeWidth = 1.dp.toPx()
            )

            counts.forEachIndexed { i, count ->
                if (count == 0) return@forEachIndexed
                val barH = (count.toFloat() / maxCount) * size.height * 0.9f
                val bucketCenter = rangeMin + (i + 0.5) * bucketW
                val inTolerance  = toleranceMs > 0 && abs(bucketCenter) <= toleranceMs
                val color = when {
                    wins[i] > 0 || inTolerance -> resultGreenColor
                    bucketCenter < 0.0         -> resultRedColor
                    else                       -> resultBlueColor
                }
                val x = (gap * i + (gap - barW) / 2).coerceIn(0f, w - barW)
                drawRoundRect(
                    color        = color.copy(alpha = 0.9f),
                    topLeft      = Offset(x, size.height - barH),
                    size         = Size(barW, barH),
                    cornerRadius = cornerR
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Pace Sparkline Chart (with axes and legend)
// ──────────────────────────────────────────────────────────────────────────────

/** Ряды графика Pace of learning (Alex 2026-10-07). */
// Порядок пунктов в списке — порядок объявления (Alex 2026-10-19: Mean error,
// Faces, Lucky index, Hit rate; Pace of learning — последним).
private enum class PaceMetric(val title: String, val legend: String, val formatY: (Float) -> String) {
    MEAN ("Mean error",       "mean",       { formatMeanMs(it) + " ms" }),
    FACES("Faces",            "faces",      { "%.0f".format(it) }),
    LUCKY("Lucky index",      "lucky",      { "%.2f".format(it) }),
    HIT  ("Hit rate",         "hit rate",   { "%.1f%%".format(it) }),
    // Эффективность 0..1 → в процентах, 1 знак (см. комментарий в PaceSparklineChart).
    PACE ("Pace of learning", "efficiency", { "%.1f%%".format(it * 100) })
}

// Цвета рожиц на графике Faces: весёлая — зелёная (как FaceIcon NICE),
// грустная — красная (Alex 2026-10-19: «рожица либо зелёная, либо красная»).
private val FACE_HAPPY_COLOR = Color(0xFF3FA864)
private val FACE_SAD_COLOR   = Color(0xFFDE4264)

/**
 * Faces за сессию: по маленькой рожице на каждую попытку, по порядку, с номером
 * попытки под ней (Alex 2026-10-19). Зелёная — весёлая, красная — грустная.
 * У первой попытки сессии рожицы нет (её не с чем сравнить) — тусклый кружок.
 */
@Composable
private fun SessionFacesStrip(happy: List<Float?>, sad: List<Float?>) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val cell = 22.dp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = (maxWidth / cell).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            happy.indices.chunked(perRow).forEach { rowIdx ->
                Row {
                    rowIdx.forEach { i ->
                        val kind: Boolean? = when {
                            happy[i] != null -> true
                            sad.getOrNull(i) != null -> false
                            else -> null
                        }
                        Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                            SmallFace(kind, 16.dp)
                            BasicText(
                                "${i + 1}",
                                style = labelStyle(7.sp, color = textColor.copy(alpha = 0.43f))
                            )
                        }
                    }
                }
            }
        }
    }
    Row(
        modifier              = Modifier.padding(top = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SmallFace(true, 9.dp)
        BasicText("happy", style = labelStyle(8.sp, color = FACE_HAPPY_COLOR.copy(alpha = 0.8f)))
        Spacer(Modifier.width(8.dp))
        SmallFace(false, 9.dp)
        BasicText("sad", style = labelStyle(8.sp, color = FACE_SAD_COLOR.copy(alpha = 0.8f)))
    }
}

/** Маленькая рожица: залитый круг, глаза и рот. happy = null — тусклый пустой кружок. */
@Composable
private fun SmallFace(happy: Boolean?, size: Dp) {
    Canvas(Modifier.size(size)) {
        val r  = this.size.minDimension / 2f
        val c  = Offset(this.size.width / 2f, this.size.height / 2f)
        if (happy == null) {
            drawCircle(Color.White.copy(alpha = 0.15f), r * 0.85f, c, style = Stroke(1.dp.toPx()))
            return@Canvas
        }
        drawCircle(if (happy) FACE_HAPPY_COLOR else FACE_SAD_COLOR, r, c)
        val ink = Color(0xFF1A1A1A)
        drawCircle(ink, r * 0.13f, Offset(c.x - r * 0.35f, c.y - r * 0.2f))
        drawCircle(ink, r * 0.13f, Offset(c.x + r * 0.35f, c.y - r * 0.2f))
        val mouth = Path().apply {
            if (happy) {
                moveTo(c.x - r * 0.45f, c.y + r * 0.15f)
                quadraticBezierTo(c.x, c.y + r * 0.65f, c.x + r * 0.45f, c.y + r * 0.15f)
            } else {
                moveTo(c.x - r * 0.42f, c.y + r * 0.5f)
                quadraticBezierTo(c.x, c.y + r * 0.08f, c.x + r * 0.42f, c.y + r * 0.5f)
            }
        }
        drawPath(mouth, ink, style = Stroke(width = (r * 0.16f).coerceAtLeast(1f), cap = StrokeCap.Round))
    }
}

/** Средняя ошибка в мс: до 10 — с десятыми, дальше целые. */
private fun formatMeanMs(ms: Float): String =
    if (ms < 10f) "%.1f".format(ms) else "%.0f".format(ms)

@Composable
private fun PaceMetricDropdown(selected: PaceMetric, onSelect: (PaceMetric) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // Заметная кнопка-список на месте заголовка (Alex 2026-10-19: «сделай
        // дроплист более заметным»): белый текст в рамке цвета акцента,
        // стрелка акцентом.
        val accent = MaterialTheme.colorScheme.accentColor
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.12f))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText(selected.title, style = blockTitleStyle.copy(color = Color.White))
            BasicText("▾", style = blockTitleStyle.copy(color = accent))
        }
        DropdownMenu(
            expanded         = open,
            onDismissRequest = { open = false },
            modifier         = Modifier.background(cardBg)
        ) {
            PaceMetric.entries.forEach { m ->
                DropdownMenuItem(
                    text    = {
                        BasicText(
                            m.title,
                            style = if (m == selected) metricValueStyle() else metricLabelStyle()
                        )
                    },
                    onClick = { onSelect(m); open = false }
                )
            }
        }
    }
}

@Composable
private fun PaceSparklineChart(
    values   : List<Float?>,
    lineColor: Color,
    xLabels  : List<String> = emptyList(),
    formatY  : (Float) -> String = { "%.1f%%".format(it * 100) },
    legend   : String = "efficiency",
    // Второй ряд — столбик рядом с первым в той же точке (Mean error: ранние
    // и поздние; Faces: весёлые и грустные). null — график из одного ряда.
    values2  : List<Float?>? = null,
    color2   : Color = lineColor,
    legend2  : String = "",
    // true — в точке не пара, а один столбик того ряда, у которого есть
    // значение (сессия: столбик = попытка, Alex 2026-10-19).
    single   : Boolean = false
) {
    // Столбики от нуля с подписью даты/времени под ними (Alex 2026-10-07:
    // «сделай столбиковой диаграммой с указанием даты или времени»).
    // null = точка без данных: место под столбик остаётся пустым, подпись есть.
    val realValues = values.filterNotNull() + (values2?.filterNotNull() ?: emptyList())
    if (values.size < 2 || realValues.isEmpty()) return
    val textColor   = MaterialTheme.colorScheme.baseTextColor
    val yAxisWidth  = 44.dp
    val chartHeight = 64.dp
    val headroom    = 20.dp
    val maxV        = realValues.max().coerceAtLeast(0.0001f)
    val lastRealIdx = values.indices.lastOrNull { values[it] != null || values2?.getOrNull(it) != null } ?: -1
    // Подписи — не теснее одной на ~8 столбиков: при всех точках периода
    // (до 30+) подписывать каждую не влезает.
    val step = ((values.size + 7) / 8).coerceAtLeast(1)
    // Тап с удержанием на столбике показывает его значение (Alex 2026-10-19);
    // палец отпущен — подсказка исчезает.
    var selected by remember(values, values2) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val tipStyle = labelStyle(10.sp, FontWeight.Medium, Color.White)

    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .padding(top = headroom - 5.dp)
                    .height(chartHeight + 5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(formatY(maxV), style = labelStyle(8.sp, color = textColor.copy(alpha = 0.53f)))
                BasicText(formatY(0f),   style = labelStyle(8.sp, color = textColor.copy(alpha = 0.53f)))
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight + headroom)
                    .pointerInput(values.size) {
                        detectTapGestures(
                            onPress     = { tryAwaitRelease(); selected = null },
                            onLongPress = { off ->
                                selected = (off.x / (size.width.toFloat() / values.size)).toInt()
                                    .coerceIn(0, values.size - 1)
                            }
                        )
                    }
            ) {
                val w     = size.width
                val h     = size.height
                val slot  = w / values.size
                val pair  = values2 != null && !single
                val barW  = slot * (if (pair) 0.36f else 0.62f)
                val gap   = if (pair) slot * 0.04f else 0f
                // Сверху — полоса под подпись значения: столбики до неё не
                // достают, подпись не закрывает их верх (Alex 2026-10-19).
                val top   = headroom.toPx()
                val sel   = selected
                fun bar(i: Int, v: Float, color: Color, x: Float) {
                    if (v <= 0f) return
                    val a = when {
                        sel != null  -> if (i == sel) 1f else 0.3f
                        i == lastRealIdx -> 1f
                        else -> 0.7f
                    }
                    val barH = ((v / maxV) * (h - top)).coerceAtLeast(1.dp.toPx())
                    drawRoundRect(
                        color        = color.copy(alpha = color.alpha * a),
                        topLeft      = Offset(x, h - barH),
                        size         = Size(barW, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
                values.indices.forEach { i ->
                    val left = slot * i + (slot - (if (pair) 2 * barW + gap else barW)) / 2f
                    values[i]?.let { bar(i, it, lineColor, left) }
                    values2?.getOrNull(i)?.let { bar(i, it, color2, if (pair) left + barW + gap else left) }
                }
                drawLine(
                    color       = textColor.copy(alpha = 0.25f),
                    start       = Offset(0f, h),
                    end         = Offset(w, h),
                    strokeWidth = 1.dp.toPx()
                )
                // Подсказка над графиком: подпись точки и значение(я).
                if (sel != null) {
                    val fmt = { v: Float? -> v?.let(formatY) ?: "—" }
                    val head = xLabels.getOrElse(sel) { "" }.replace('\n', ' ')
                    val v1 = values[sel]
                    val v2 = values2?.getOrNull(sel)
                    fun named(name: String, v: Float) = formatY(v).let { if (it.isBlank()) name else "$name $it" }
                    val body = when {
                        pair       -> "$legend ${fmt(v1)} · $legend2 ${fmt(v2)}"
                        single     -> when {
                            v1 != null -> named(legend, v1)
                            v2 != null -> named(legend2, v2)
                            else       -> "—"
                        }
                        else       -> fmt(v1)
                    }
                    val tl  = measurer.measure(if (head.isEmpty()) body else "$head · $body", tipStyle)
                    val padX = 6.dp.toPx()
                    val padY = 3.dp.toPx()
                    val bw  = tl.size.width + 2 * padX
                    val bh  = tl.size.height + 2 * padY
                    // Тонкая линия по середине столбика (у пары — по середине
                    // точки) на всю высоту графика, значение — над её верхом
                    // (Alex 2026-10-19).
                    val lx  = slot * (sel + 0.5f)
                    drawLine(
                        color       = Color.White.copy(alpha = 0.8f),
                        start       = Offset(lx, bh),
                        end         = Offset(lx, h),
                        strokeWidth = 1.dp.toPx()
                    )
                    val cx  = lx.coerceIn(bw / 2f, (w - bw / 2f).coerceAtLeast(bw / 2f))
                    drawRoundRect(
                        color        = Color(0xF2333333),
                        topLeft      = Offset(cx - bw / 2f, 0f),
                        size         = Size(bw, bh),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawText(tl, topLeft = Offset(cx - bw / 2f + padX, padY))
                }
            }
        }
        // Подписи оси X — по центру своего столбика.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
            Spacer(Modifier.width(yAxisWidth))
            Row(modifier = Modifier.weight(1f)) {
                values.indices.forEach { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (i % step == 0) {
                            BasicText(
                                text     = xLabels.getOrElse(i) { "" },
                                style    = labelStyle(8.sp, color = textColor.copy(alpha = 0.43f))
                                    .copy(textAlign = TextAlign.Center),
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true)
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier              = Modifier.padding(start = yAxisWidth, top = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Canvas(modifier = Modifier.width(8.dp).height(8.dp)) {
                drawRect(lineColor)
            }
            BasicText(legend, style = labelStyle(8.sp, color = lineColor.copy(alpha = 0.7f)))
            if (values2 != null) {
                Spacer(Modifier.width(8.dp))
                Canvas(modifier = Modifier.width(8.dp).height(8.dp)) {
                    drawRect(color2)
                }
                BasicText(legend2, style = labelStyle(8.sp, color = color2.copy(alpha = 0.7f)))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  TrendSparkline
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Маленький линейный график тренда.
 * [values] — float 0..N, старые → новые.
 * [color]  — цвет линии.
 * Высота фиксирована; ширина = fillMaxWidth * доля.
 */
@Composable
private fun TrendSparkline(values: List<Float>, color: Color) {
    if (values.size < 2) return
    Canvas(
        modifier = Modifier
            .height(28.dp)
            .fillMaxWidth()
    ) {
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(0.001f)
        val w = size.width
        val h = size.height
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1)
            val y = h - (v - minV) / range * h * 0.85f - h * 0.075f   // 7.5% padding top/bottom
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path  = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
        // точка «сейчас»
        val lastX = w
        val lastY = h - (values.last() - minV) / range * h * 0.85f - h * 0.075f
        drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Утилиты форматирования
// ──────────────────────────────────────────────────────────────────────────────

/** σ% с cap: защита от 688% на шумных данных */
/** Фактор геометрического разброса: stdPct = (exp(σ_ln) − 1)·100 → exp(σ_ln).
 *  ОДИН символ «×» (Alex 2026-07-23): «×1.4» — стандартная запись fold-change;
 *  что фактор работает в обе стороны (mean ÷ 1.4 … mean × 1.4), объясняет
 *  подпись под карточкой. «±» некорректен: геом. разброс мультипликативен
 *  и несимметричен. */
private fun fmtFold(pct: Double): String = when {
    pct <= 0.0  -> "—"
    pct > 999.0 -> "÷>10"
    else        -> "÷%.1f".format(1.0 + pct / 100.0)
}

@Composable
private fun DeviationRow(
    label      : String,
    profile    : DeviationProfile,
    labelColor : Color,
    negated    : Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            modifier = Modifier.weight(1f),
            text  = label,
            style = metricLabelStyle(color = labelColor)
        )
        val displayMs = if (negated) -profile.medianMs else profile.medianMs
        FitValueText(
            text  = if (profile.medianMs != 0.0)
                // Фактор разброса вплотную к значению через двойной пробел:
                // «−18ms  ×/÷1.4».
                groupDigits("%s  %s".format(formatMs(displayMs), fmtFold(profile.stdPct)))
            else "—",
            style = metricValueStyle()
        )
    }
}

private fun formatMs(ms: Double): String {
    val sign  = if (ms < 0) "−" else "+"
    val absMs = abs(ms)
    return when {
        absMs >= 1000.0 -> "${sign}%.2fs".format(absMs / 1000.0)
        else            -> "${sign}%.0fms".format(absMs)
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSec = ms / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "${groupDigits(h.toString())}h %02dm %02ds".format(m, s)
    else "%dm %02ds".format(m, s)
}

// ──────────────────────────────────────────────────────────────────────────────
//  Дерево год → месяц → день
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Палец, длительность и процент побед у узлов дерева выше сессии
 * (Alex 2026-10-06: «палец вверх и другие атрибуты сессии вынеси на все
 * верхние уровни… для недели, месяца и тп считается как среднее по дням,
 * когда играли»).
 *
 * День — по самому дню: палец вверх, если взят хотя бы один квест (полный
 * или с индульгенцией — оба ставят isQuestComplete), время — сколько
 * наиграно за день, процент — по всем попыткам дня. Неделя, месяц, год —
 * среднее по игровым дням: время и процент усредняются по дням, палец
 * вверх, если зелёных дней не меньше половины.
 *
 * thumbUp == null — судить не о чем: в узле только идущая сейчас сессия.
 */
private data class NodeSummary(val playMs: Long, val winPct: Double, val thumbUp: Boolean?)

private fun daySummary(sessions: List<SessionTreeData>): NodeSummary {
    val attempts = sessions.sumOf { it.attempts }
    val thumb = when {
        sessions.any { it.isQuestComplete } -> true
        sessions.all { it.thumbUp == null } -> null
        else -> false
    }
    return NodeSummary(
        playMs  = sessions.sumOf { it.activeTimeMs },
        winPct  = if (attempts > 0) sessions.sumOf { it.wins } * 100.0 / attempts else 0.0,
        thumbUp = thumb
    )
}

private fun averageOfDays(days: List<NodeSummary>): NodeSummary {
    val judged = days.mapNotNull { it.thumbUp }
    return NodeSummary(
        playMs  = days.sumOf { it.playMs } / days.size.coerceAtLeast(1),
        winPct  = days.sumOf { it.winPct } / days.size.coerceAtLeast(1),
        thumbUp = if (judged.isEmpty()) null else judged.count { it } * 2 >= judged.size
    )
}

private data class SessionDayNode(val day: Int, val label: String,
    val sessions: List<SessionTreeData>, val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double,
    val luckyIndex: Double, val summary: NodeSummary)
// Неделя как отдельная сущность дерева (Alex 2026-09-09: "сделай неделю как
// сущность и фильтр на экране аналитикс, не забудь отразить в дереве попыток")
// — календарная ISO-неделя (Calendar.WEEK_OF_YEAR), группирует дни ВНУТРИ
// одного месяца (не глобально по всей истории): последняя неделя августа и
// первая неделя сентября — разные узлы под разными месяцами, даже если это
// технически один и тот же WEEK_OF_YEAR на стыке года. Это тот же паттерн,
// что уже использует WEEK-фильтр в других местах — самостоятельная сущность,
// а не побочный продукт фильтра "последние 7 дней".
private data class SessionWeekNode(val weekOfYear: Int, val label: String,
    val days: List<SessionDayNode>, val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double,
    val luckyIndex: Double, val summary: NodeSummary)
private data class SessionMonthNode(val month: Int, val label: String,
    val weeks: List<SessionWeekNode>, val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double,
    val luckyIndex: Double, val summary: NodeSummary)
private data class SessionYearNode(val year: Int, val months: List<SessionMonthNode>,
    val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double,
    val luckyIndex: Double, val summary: NodeSummary)

/** exp(mean(ln(x))) over positive values — 0.0 if the list is empty. */
private fun geometricMean(values: List<Double>): Double {
    val valid = values.filter { it > 0.0 }
    if (valid.isEmpty()) return 0.0
    return kotlin.math.exp(valid.sumOf { kotlin.math.ln(it) } / valid.size)
}

/** Geometric mean of early (red) attempt errors, ms. Excludes tolerance wins. */
private fun geomEarlyMsOf(bars: List<online.playoftime.features.stats.domain.entity.AttemptBarData>): Double =
    geometricMean(bars.filter { !it.isWin && it.errorMs < 0.0 }.map { abs(it.errorMs) })

/** Geometric mean of late (blue) attempt errors, ms. Excludes tolerance wins. */
private fun geomLateMsOf(bars: List<online.playoftime.features.stats.domain.entity.AttemptBarData>): Double =
    geometricMean(bars.filter { !it.isWin && it.errorMs > 0.0 }.map { it.errorMs })

/**
 * Lucky index для набора попыток: отношение эмпирической вероятности победы
 * к теоретической (дисперсионной). Чем больше — тем лучше игрок.
 *   slotMs = 100 / 10 / 1 (по большинству попыток в наборе) — ширина слота
 *            совпадает с шириной окна «чистой тройки» числителя (Alex 2026-07-10)
 *   outcomeSlots = (range / slotMs) + 1
 *   dispersion = 1 / outcomeSlots     — теоретическая вероятность попасть в 0 наугад
 *   empirical  = luckyWins / bars.size
 *   lucky      = empirical / dispersion
 * 0.0, если чистых побед не было.
 */
private fun luckyIndexOf(bars: List<online.playoftime.features.stats.domain.entity.AttemptBarData>): Double {
    if (bars.isEmpty()) return 0.0
    // Range = P3..P97 персентиль подписанных ошибок (Alex 2026-08-14: та же
    // обрезка, что и headline Lucky на CHANCE OF WIN в AnalyticsUseCase.kt).
    // Раньше здесь был буквальный max−min по всем попыткам — один аномальный
    // выброс (например, случайно затянутый первый тап сессии) раздувал range,
    // а с ним outcomeSlots и сам Lucky в разы относительно headline-метрики,
    // которая от таких выбросов защищена персентильной обрезкой.
    val errs = bars.map { it.errorMs }.sorted()
    val loIdx = (errs.size * 0.03).toInt().coerceIn(0, errs.size - 1)
    val hiIdx = (errs.size * 0.97).toInt().coerceIn(0, errs.size - 1)
    val range = abs(errs[loIdx]) + abs(errs[hiIdx])
    // Lucky ВСЕГДА в тысячных (Alex 2026-07-30): слот = 1 мс, «чистая тройка» =
    // ровно 3.000 (±0.5 мс) на всех уровнях. Внутренняя нормировка, не влияет на
    // att4win в дереве (там победы сессий — с допуском, как Attempts per win).
    val slotMs = 1.0
    val outcomeSlots = (range / slotMs).toLong().coerceAtLeast(1L) + 1L
    val dispersion = 1.0 / outcomeSlots
    val luckyWins = bars.count { abs(it.errorMs) < 0.5 }
    val empirical = luckyWins.toDouble() / bars.size
    return if (dispersion > 0) empirical / dispersion else 0.0
}

private fun SessionTreeData.geomEarlyMs(): Double = geomEarlyMsOf(attemptBars)
private fun SessionTreeData.geomLateMs(): Double  = geomLateMsOf(attemptBars)
private fun SessionTreeData.luckyIndex(): Double  = luckyIndexOf(attemptBars)

private fun buildTree(sessions: List<SessionTreeData>): List<SessionYearNode> {
    val cal = Calendar.getInstance()
    return sessions
        .groupBy { s -> cal.also { it.timeInMillis = s.startTime }.get(Calendar.YEAR) }
        .map { (year, ySessions) ->
            val months = ySessions
                .groupBy { s -> cal.also { it.timeInMillis = s.startTime }.get(Calendar.MONTH) }
                .map { (month, mSessions) ->
                    val days = mSessions
                        .groupBy { s -> cal.also { it.timeInMillis = s.startTime }.get(Calendar.DAY_OF_MONTH) }
                        .map { (day, dSessions) ->
                            val dayLabel = cal.also {
                                it.set(year, month, day)
                            }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH) +
                                    " %02d".format(day)
                            val dBars = dSessions.flatMap { it.attemptBars }
                            SessionDayNode(day, dayLabel, dSessions,
                                dSessions.sumOf { it.attempts },
                                dSessions.sumOf { it.wins },
                                geomEarlyMsOf(dBars),
                                geomLateMsOf(dBars),
                                                                luckyIndexOf(dBars),
                                daySummary(dSessions))
                        }
                        .sortedByDescending { it.day }
                    val monthLabel = cal.also {
                        it.set(year, month, 1)
                    }.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: "Month $month"
                    val mBars = mSessions.flatMap { it.attemptBars }
                    // Неделя — группировка дней ЭТОГО месяца по календарной ISO-неделе
                    // (WEEK_OF_YEAR); дни, попавшие в один WEEK_OF_YEAR но в разных
                    // месяцах, окажутся в разных недельных узлах — это ожидаемо,
                    // недельные узлы живут внутри своего месяца, а не глобально.
                    val weeks = days
                        .groupBy { d -> cal.also { it.set(year, month, d.day) }.get(Calendar.WEEK_OF_YEAR) }
                        .map { (weekOfYear, wDays) ->
                            val sortedDays = wDays.sortedByDescending { it.day }
                            val minDay = wDays.minOf { it.day }
                            val maxDay = wDays.maxOf { it.day }
                            val weekLabel = if (minDay == maxDay) "$minDay" else "$minDay–$maxDay"
                            val wBars = wDays.flatMap { d -> d.sessions.flatMap { it.attemptBars } }
                            SessionWeekNode(weekOfYear, weekLabel, sortedDays,
                                wDays.sumOf { it.totalAttempts },
                                wDays.sumOf { it.totalWins },
                                geomEarlyMsOf(wBars),
                                geomLateMsOf(wBars),
                                                                luckyIndexOf(wBars),
                                averageOfDays(wDays.map { it.summary }))
                        }
                        .sortedByDescending { it.weekOfYear }
                    SessionMonthNode(month, monthLabel, weeks,
                        days.sumOf { it.totalAttempts },
                        days.sumOf { it.totalWins },
                        geomEarlyMsOf(mBars),
                        geomLateMsOf(mBars),
                                                luckyIndexOf(mBars),
                        averageOfDays(days.map { it.summary }))
                }
                .sortedByDescending { it.month }
            val yBars = ySessions.flatMap { it.attemptBars }
            SessionYearNode(year, months,
                months.sumOf { it.totalAttempts },
                months.sumOf { it.totalWins },
                geomEarlyMsOf(yBars),
                geomLateMsOf(yBars),
                                luckyIndexOf(yBars),
                averageOfDays(months.flatMap { m -> m.weeks.flatMap { w -> w.days.map { it.summary } } }))
        }
        .sortedByDescending { it.year }
}
