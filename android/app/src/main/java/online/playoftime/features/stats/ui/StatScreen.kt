package online.playoftime.features.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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

private val interProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

private val InterFamily = FontFamily(
    Font(GoogleFont("Inter"), interProvider, FontWeight.Normal),
    Font(GoogleFont("Inter"), interProvider, FontWeight.Medium),
    Font(GoogleFont("Inter"), interProvider, FontWeight.SemiBold),
    Font(GoogleFont("Inter"), interProvider, FontWeight.Bold),
)

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
            StatCard("CHANCE OF WIN", pl) {
                CardMetricRow(
                    label = "Hit rate",
                    value = "%.1f%%".format(data.trueThreeHitRatePct)
                )
                CardMetricRow(
                    label = "Attempts per win",
                    value = if (data.attemptsPerWin > 0) "%.1f".format(data.attemptsPerWin) else "—"
                )
                if (data.luckyIndex > 0) {
                    CardMetricRow(
                        label = "Lucky Index",
                        value = "%.2f".format(data.luckyIndex),
                        note  = "(empirical / theoretical probability — higher is better)"
                    )
                }
            }
            }
        }

        // ── GEOMETRIC MEAN + VARIABILITY ─────────────────────────────────
        // (moved above RANGE OF DEVIATIONS per design)
        item {
            val textColor = MaterialTheme.colorScheme.baseTextColor
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_GEOM)) {
            StatCard("GEOMETRIC MEAN ÷ SPREAD", pl) {
                DeviationRow("Early",    data.deviationEarly, resultRedColor,  textColor, negated = true)
                DeviationRow("Absolute", data.deviationAll,   textColor,       textColor)
                DeviationRow("Late",     data.deviationLate,  resultBlueColor, textColor)
            }
            }
        }

        // ── RANGE OF DEVIATIONS (со встроенными MODES OF DEVIATION) ──────
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_RANGE)) {
            StatCard("RANGE OF DEVIATIONS", pl) {
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
                    text  = "The range where 97% of your results fall, with extreme outliers removed.",
                    style = labelStyle(11.sp, color = textColor.copy(alpha = 0.48f))
                )
            }
            }
        }

        // ── PACE OF LEARNING ─────────────────────────────────────────────
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_PACE)) {
            StatCard("PACE OF LEARNING", pl) {
                if (data.paceChartValues.size >= 2) {
                    PaceSparklineChart(data.paceChartValues, accent.copy(alpha = 0.65f), data.paceChartLabels)
                    Spacer(Modifier.height(6.dp))
                }
                CardMetricRow(
                    label = "SC/att",
                    value = "%+.1f".format(data.paceOfLearning),
                    note  = "Growth in Serenity Index per attempt"
                )
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
                    text  = "SESSIONS LOG",
                    style = labelStyle(size = 10.sp, weight = FontWeight.SemiBold,
                        color = baseColor.copy(alpha = subtleText))
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
                    trueThreeWins = yearNode.trueThreeWins,
                    luckyIndex    = yearNode.luckyIndex,
                    expanded      = yearNode.year in expandedYears,
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
                            trueThreeWins = monthNode.trueThreeWins,
                            luckyIndex    = monthNode.luckyIndex,
                            expanded      = monthKey in expandedMonths,
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
                                    trueThreeWins = weekNode.trueThreeWins,
                                    luckyIndex    = weekNode.luckyIndex,
                                    expanded      = weekKey in expandedWeeks,
                                    onClick       = { onToggleWeek(weekKey) },
                                    helpMode      = helpMode
                                )
                                }
                            }
                            if (weekKey in expandedWeeks) {
                                weekNode.days.forEach { dayNode ->
                                    val dayKey = "${yearNode.year}-${monthNode.month}-${dayNode.day}"
                                    item {
                                        // Палец у дня — по большинству успешных сессий
                                        // (isQuestComplete). Активные сессии (thumbUp==null)
                                        // из знаменателя исключаем.
                                        val judged = dayNode.sessions.filter { it.thumbUp != null }
                                        val dayThumb = if (judged.isEmpty()) null
                                            else judged.count { it.isQuestComplete } * 2 > judged.size
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
                                            trueThreeWins = dayNode.trueThreeWins,
                                            luckyIndex    = dayNode.luckyIndex,
                                            expanded      = dayKey in expandedDays,
                                            thumbUp       = dayThumb,
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
    val textColor = MaterialTheme.colorScheme.baseTextColor

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
            // Сколько сыграно и сколько попыток — одной строкой над остальным
            // (Alex 2026-09-27: «поставим количество попыток рядом со временем»).
            // Оба значения отвечают на один вопрос — сколько было игры — и
            // читаются вместе. Раньше ATTEMPTS стоял третьей строкой в левой
            // колонке, между SESSIONS и WINS/PRECISE.
            //
            // Строка занимает всю ширину карточки, поэтому каждому значению
            // достаётся вдвое больше места, чем внутри колонки, и «2h 05m 13s»
            // умещается целиком, без уменьшения шрифта.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStat("PLAY TIME", textColor, Modifier.weight(1f)) {
                    SummaryValue(formatDuration(data.periodPlayTimeMs), textColor)
                }
                SummaryStat("ATTEMPTS", textColor, Modifier.weight(1f)) {
                    SummaryValue(formatNumber(data.periodAttempts.toString()), textColor)
                }
            }

            // Колонки равной ширины (Alex 2026-09-27). Было 0.64/0.36, и правой
            // не хватало места: «SERENITY COINS» с иконкой монеты переносило
            // дробную часть значения на вторую строку.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SummaryStat("SESSIONS", textColor) {
                        SummaryValue(formatNumber(data.periodSessionsCount.toString()), textColor)
                    }
                    SummaryStat("WINS/PRECISE", textColor) {
                        SummaryValue(
                            "${formatNumber(data.periodWins.toString())}/${formatNumber(data.periodPrecise.toString())}",
                            resultGreenColor
                        )
                    }
                    SummaryStat("PRACTICE", textColor) {
                        SummaryValue(
                            if (data.practiceDensityMinPerDay > 0)
                                groupDigits("%.1f min/day".format(data.practiceDensityMinPerDay))
                            else "—",
                            textColor
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SummaryStat("SERENITY COINS", textColor) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            online.playoftime.ui_lib.CoinIcon(Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            // weight(1f) — значению достаётся ровно остаток
                            // строки после иконки, по нему оно и подгоняет шрифт.
                            SummaryValue(
                                groupDigits("%.2f".format(data.periodScm)),
                                textColor,
                                Modifier.weight(1f)
                            )
                        }
                    }
                    SummaryStat("PACE OF PLAY", textColor) {
                        SummaryValue(
                            if (data.paceOfPlayPerMin > 0)
                                groupDigits("%.1f att/min".format(data.paceOfPlayPerMin))
                            else "—",
                            textColor
                        )
                    }
                    // Дата первой попытки (Alex 2026-09-27: «дополнить карточку
                    // датой начала игры»). Единственное значение в карточке,
                    // которое НЕ подчиняется фильтру периода: «играю с такого-то»
                    // по смыслу всегда за всю историю, иначе при выборе «сегодня»
                    // оно показывало бы сегодняшнее число и теряло смысл.
                    SummaryStat("PLAYING SINCE", textColor) {
                        SummaryValue(
                            if (data.firstPlayedMs > 0)
                                SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
                                    .format(Date(data.firstPlayedMs))
                            else "—",
                            textColor
                        )
                    }
                }
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
    textColor: Color,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text  = label,
            style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
        )
        value()
    }
}

@Composable
private fun SummaryValue(text: String, color: Color, modifier: Modifier = Modifier) {
    // 12sp Medium — тот же размер, что у значений в следующей карточке
    // (CardMetricRow), чтобы верхняя карточка не выбивалась из ряда
    // (Alex 2026-09-27). Раньше было 14sp SemiBold.
    FitValueText(
        text     = text,
        style    = metricStyle(12.sp, FontWeight.Medium, color),
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
    content : @Composable () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BasicText(
                text  = title,
                style = labelStyle(10.sp, FontWeight.SemiBold, textColor.copy(alpha = subtleText))
            )
            if (period.isNotEmpty()) {
                BasicText(
                    text  = period,
                    style = labelStyle(10.sp, color = textColor.copy(alpha = subtleText))
                )
            }
        }
        content()
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
                style = labelStyle(11.sp, FontWeight.Medium, color = textColor.copy(alpha = 0.73f))
            )
            if (note != null) BasicText(
                text  = note,
                style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
            )
        }
        Spacer(Modifier.width(8.dp))
        FitValueText(
            text  = groupDigits(value),
            style = metricStyle(12.sp, FontWeight.Medium,
                if (valueColor == Color.Unspecified) textColor else valueColor)
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

/** attempts ÷ clean true-three wins, "—" if no true-three win in this node. */
private fun formatAttemptsPerWin(attempts: Int, trueThreeWins: Int): String {
    if (trueThreeWins <= 0) return "—"
    val ratio = attempts.toDouble() / trueThreeWins
    return groupDigits("%.1f".format(ratio))
}

/** Lucky index formatted as "0.87". "—" if lucky index is 0.0 (нет данных / нет побед). */
private fun formatLucky(l: Double): String =
    if (l <= 0.0) "—" else "%.2f".format(l)

@Composable
private fun TreeYearRow(year: Int, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(0.dp, year.toString(), attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        metricStyle(14.sp, FontWeight.Bold, MaterialTheme.colorScheme.baseTextColor), onClick, helpMode = helpMode)

@Composable
private fun TreeMonthRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(12.dp, label, attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        metricStyle(13.sp, FontWeight.Medium, MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.90f)), onClick, helpMode = helpMode)

@Composable
private fun TreeWeekRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(18.dp, label, attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        metricStyle(12.5.sp, FontWeight.Medium, MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.84f)), onClick, helpMode = helpMode)

@Composable
private fun TreeDayRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, thumbUp: Boolean?, onClick: () -> Unit, helpMode: Boolean = false) =
    TreeRow(24.dp, label, attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        labelStyle(12.sp, color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.78f)), onClick,
        thumbUp = thumbUp, helpMode = helpMode)

@Composable
private fun TreeRow(
    indent        : Dp,
    label         : String,
    attempts      : Int,
    geomEarlyMs   : Double,
    geomLateMs    : Double,
    trueThreeWins : Int,
    luckyIndex    : Double,
    expanded      : Boolean,
    labelStyle2   : TextStyle,
    onClick       : () -> Unit,
    // Item 2 (Alex 2026-07-26): палец у дней — успех по большинству сессий.
    // null → иконка не рисуется (год/месяц).
    thumbUp       : Boolean? = null,
    // В режиме справки тап по строке дерева должен показывать бабл STAT_SESSIONS
    // (через helpTapGestures на внешнем Box), а не разворачивать/сворачивать
    // строку — иначе .clickable() ниже consume'ит тап раньше, чем очередь
    // дойдёт до обработчика справки (Alex 2026-09-10: "спрака должна
    // реагировать соответственно" при выборе в дереве лога/месяцев/недель/
    // дней). enabled=false здесь НЕ consume'ит тап — он проходит насквозь.
    helpMode      : Boolean = false
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !helpMode) { onClick() }
            .padding(start = indent, top = 4.dp, bottom = 4.dp),
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
            text  = formatAttemptsPerWin(attempts, trueThreeWins),
            style = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.58f))
                .copy(textAlign = TextAlign.Center)
        )
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
                text  = formatAttemptsPerWin(session.attempts, session.trueThreeWins()),
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
                text  = "👑 Done! Level up",
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

@Composable
private fun PaceSparklineChart(values: List<Float?>, lineColor: Color, xLabels: List<String> = emptyList()) {
    // null = бакет без данных (Alex 2026-08-14: «прерывай графики для периодов,
    // данных о которых у тебя нет») — исключается из min/max и разрывает линию,
    // вместо того чтобы тянуть её сквозь пропуск или опускать в ноль.
    val realValues = values.filterNotNull()
    if (values.size < 2 || realValues.size < 2) return
    val textColor   = MaterialTheme.colorScheme.baseTextColor
    val axisAlpha   = 0.25f
    val yAxisWidth  = 44.dp
    val chartHeight = 60.dp

    val minV  = realValues.min()
    val maxV  = realValues.max()
    val range = (maxV - minV).coerceAtLeast(0.001f)

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Chart row: Y-axis labels + canvas
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Значения — доля эффективности 0..1 (Alex 2026-08-14: нормализовано
                // по «потолку» SC для точности каждой попытки, чтобы график не был
                // задавлен масштабом levelFactor между уровнями) — показываем в %.
                // 1 знак после запятой (Alex 05.09: «почему по оси Y два одинаковых
                // 1%?» — %.0f округлял 1.4% и 0.6% до одного и того же «1%», раз
                // efficiency у реальных попыток часто однозначный процент).
                BasicText("%.1f%%".format(maxV * 100), style = labelStyle(8.sp, color = textColor.copy(alpha = 0.53f)))
                BasicText("%.1f%%".format(minV * 100), style = labelStyle(8.sp, color = textColor.copy(alpha = 0.53f)))
            }

            // Line chart canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
            ) {
                val w   = size.width
                val h   = size.height
                val pad = 6.dp.toPx()

                // Y = 0 reference line (if range crosses zero)
                val zeroFraction = if (minV < 0f && maxV > 0f) (-minV) / range else -1f
                if (zeroFraction in 0f..1f) {
                    val zy = h - pad - zeroFraction * (h - 2 * pad)
                    drawLine(
                        color       = textColor.copy(alpha = 0.15f),
                        start       = Offset(0f, zy),
                        end         = Offset(w, zy),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Y-axis left border
                drawLine(
                    color       = textColor.copy(alpha = axisAlpha),
                    start       = Offset(0f, 0f),
                    end         = Offset(0f, h),
                    strokeWidth = 1.dp.toPx()
                )
                // X-axis bottom border
                drawLine(
                    color       = textColor.copy(alpha = axisAlpha),
                    start       = Offset(0f, h),
                    end         = Offset(w, h),
                    strokeWidth = 1.dp.toPx()
                )

                // Trend line — рвётся на null-бакетах: moveTo (не lineTo) начинает
                // новый сегмент, так что пропуск данных не соединяется линией.
                val path = Path()
                var segmentOpen = false
                values.forEachIndexed { i, v ->
                    if (v == null) {
                        segmentOpen = false
                        return@forEachIndexed
                    }
                    val x = if (values.size > 1) w * i / (values.size - 1) else w / 2f
                    val y = h - pad - ((v - minV) / range) * (h - 2 * pad)
                    if (!segmentOpen) { path.moveTo(x, y); segmentOpen = true }
                    else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

                // Current-value dot — на последнем РЕАЛЬНОМ (не null) значении.
                val lastRealIdx = values.indexOfLast { it != null }
                if (lastRealIdx >= 0) {
                    val v = values[lastRealIdx]!!
                    val lastX = if (values.size > 1) w * lastRealIdx / (values.size - 1) else w / 2f
                    val lastY = h - pad - ((v - minV) / range) * (h - 2 * pad)
                    drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
                }
            }
        }

        // X-axis: session dates or fallback "older"/"now"
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(yAxisWidth))
            Row(
                modifier              = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Чтобы подписи не наезжали при большом числе бакетов — показываем
                // только первую / среднюю / последнюю (или все, если их ≤ 3).
                val shown = if (xLabels.size <= 3) xLabels
                            else listOf(xLabels.first(), xLabels[xLabels.size / 2], xLabels.last())
                if (shown.isNotEmpty()) {
                    shown.forEach { lab ->
                        BasicText(lab, style = labelStyle(8.sp, color = textColor.copy(alpha = 0.43f)))
                    }
                } else {
                    BasicText("older", style = labelStyle(8.sp, color = textColor.copy(alpha = 0.43f)))
                    BasicText("now",   style = labelStyle(8.sp, color = textColor.copy(alpha = 0.43f)))
                }
            }
        }

        // Legend
        Row(
            modifier              = Modifier.padding(start = yAxisWidth),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Canvas(modifier = Modifier.width(8.dp).height(8.dp)) {
                drawCircle(lineColor, radius = size.minDimension / 2f)
            }
            BasicText("efficiency", style = labelStyle(8.sp, color = lineColor.copy(alpha = 0.7f)))
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
    textColor  : Color,
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
            style = labelStyle(11.sp, FontWeight.Medium, labelColor)
        )
        val displayMs = if (negated) -profile.medianMs else profile.medianMs
        FitValueText(
            text  = if (profile.medianMs != 0.0)
                // Фактор разброса вплотную к значению через двойной пробел:
                // «−18ms  ×/÷1.4».
                groupDigits("%s  %s".format(formatMs(displayMs), fmtFold(profile.stdPct)))
            else "—",
            style = metricStyle(12.sp, FontWeight.Medium, textColor.copy(alpha = 0.88f))
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

private data class SessionDayNode(val day: Int, val label: String,
    val sessions: List<SessionTreeData>, val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double, val trueThreeWins: Int,
    val luckyIndex: Double)
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
    val geomEarlyMs: Double, val geomLateMs: Double, val trueThreeWins: Int,
    val luckyIndex: Double)
private data class SessionMonthNode(val month: Int, val label: String,
    val weeks: List<SessionWeekNode>, val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double, val trueThreeWins: Int,
    val luckyIndex: Double)
private data class SessionYearNode(val year: Int, val months: List<SessionMonthNode>,
    val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double, val trueThreeWins: Int,
    val luckyIndex: Double)

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
 * Count of «чистых троек» — попадание в нулевой слот В ТОЧНОСТИ СВОЕЙ попытки
 * (та же логика, что countTrueThree в AnalyticsUseCase): L1 — окно ~100 мс
 * (округляется до 3.0), L2 — 10 мс, L3 — 1 мс. Tolerance-победы не считаются.
 * Раньше здесь было мс-точное |error| < 0.5 — из-за этого L1-строки дерева
 * показывали нули троек и lucky, хотя игрок бил чистые 3.0.
 */
private fun trueThreeWinsOf(bars: List<online.playoftime.features.stats.domain.entity.AttemptBarData>): Int =
    bars.count { bar ->
        val slot = when (bar.accuracy) { 3 -> 1.0; 2 -> 10.0; else -> 100.0 }
        abs(bar.errorMs) < slot / 2
    }

/**
 * Lucky index для набора попыток: отношение эмпирической вероятности победы
 * к теоретической (дисперсионной). Чем больше — тем лучше игрок.
 *   slotMs = 100 / 10 / 1 (по большинству попыток в наборе) — ширина слота
 *            совпадает с шириной окна «чистой тройки» числителя (Alex 2026-07-10)
 *   outcomeSlots = (range / slotMs) + 1
 *   dispersion = 1 / outcomeSlots     — теоретическая вероятность попасть в 0 наугад
 *   empirical  = trueThreeWins / bars.size
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
    // отображаемые wins (trueThreeWinsOf там считается в точности уровня).
    val slotMs = 1.0
    val outcomeSlots = (range / slotMs).toLong().coerceAtLeast(1L) + 1L
    val dispersion = 1.0 / outcomeSlots
    val luckyWins = bars.count { abs(it.errorMs) < 0.5 }
    val empirical = luckyWins.toDouble() / bars.size
    return if (dispersion > 0) empirical / dispersion else 0.0
}

private fun SessionTreeData.geomEarlyMs(): Double = geomEarlyMsOf(attemptBars)
private fun SessionTreeData.geomLateMs(): Double  = geomLateMsOf(attemptBars)
private fun SessionTreeData.trueThreeWins(): Int  = trueThreeWinsOf(attemptBars)
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
                                trueThreeWinsOf(dBars),
                                luckyIndexOf(dBars))
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
                                trueThreeWinsOf(wBars),
                                luckyIndexOf(wBars))
                        }
                        .sortedByDescending { it.weekOfYear }
                    SessionMonthNode(month, monthLabel, weeks,
                        days.sumOf { it.totalAttempts },
                        days.sumOf { it.totalWins },
                        geomEarlyMsOf(mBars),
                        geomLateMsOf(mBars),
                        trueThreeWinsOf(mBars),
                        luckyIndexOf(mBars))
                }
                .sortedByDescending { it.month }
            val yBars = ySessions.flatMap { it.attemptBars }
            SessionYearNode(year, months,
                months.sumOf { it.totalAttempts },
                months.sumOf { it.totalWins },
                geomEarlyMsOf(yBars),
                geomLateMsOf(yBars),
                trueThreeWinsOf(yBars),
                luckyIndexOf(yBars))
        }
        .sortedByDescending { it.year }
}
