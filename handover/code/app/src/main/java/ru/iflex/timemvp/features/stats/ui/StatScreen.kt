package ru.iflex.timemvp.features.stats.ui

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
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.features.stats.domain.entity.AnalyticsData
import ru.iflex.timemvp.features.stats.domain.entity.DeviationProfile
import ru.iflex.timemvp.features.stats.domain.entity.SessionTreeData
import ru.iflex.timemvp.features.main.ui.HelpTopic
import ru.iflex.timemvp.features.main.ui.helpZoneDisposable
import ru.iflex.timemvp.features.stats.ui.StatsContract.Event
import ru.iflex.timemvp.ui_lib.AppSpinner
import ru.iflex.timemvp.ui_lib.InfoDialog
import ru.iflex.timemvp.ui_lib.Screen
import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.baseBackgroundColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor
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

private val cardBg     = Color(0xFF1A1A1A)
private val dividerClr = Color(0xFF2A2A2A)
private val subtleText = 0.45f   // alpha для неактивного текста

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
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            modifier = Modifier.width(leftW),
            text  = leftText,
            style = leftStyle.copy(textAlign = TextAlign.End)
        )
        BasicText(
            modifier = Modifier.width(slashW),
            text  = "/",
            style = slashStyle.copy(textAlign = TextAlign.Center)
        )
        BasicText(
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
        mutableStateOf<ru.iflex.timemvp.features.main.ui.HelpTopic?>(null)
    }
    val helpZones = remember {
        androidx.compose.runtime.mutableStateMapOf<
            ru.iflex.timemvp.features.main.ui.HelpTopic,
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
        if (currentPage == 1) setEvent(Event.Refresh)
    }

    Screen {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.baseBackgroundColor)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        expandedDays     = state.expandedDays,
                        expandedSessions = state.expandedSessions,
                        onSelectPeriod   = { setEvent(Event.SelectPeriod(it)) },
                        onToggleYear     = { setEvent(Event.ToggleYear(it)) },
                        onToggleMonth    = { setEvent(Event.ToggleMonth(it)) },
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

                // Оверлей интерактивной справки — поверх карточек, под диалогами
                if (helpMode) {
                    ru.iflex.timemvp.features.main.ui.HelpTapOverlay(
                        zones        = helpZones,
                        questionRect = helpQuestionRect,
                        selected     = helpTopic,
                        onSelect     = { helpTopic = it },
                        onExit       = { helpMode = false; helpTopic = null },
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

            // Sticky нижняя навигация — та же на всех экранах.
            val exportAction = ru.iflex.timemvp.ui_lib.rememberExcelShareAction()
            ru.iflex.timemvp.ui_lib.BottomNavBar(
                currentPage  = currentPage,
                onSelectPage = onSelectPage,
                onExport     = exportAction
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Основное содержимое
// ──────────────────────────────────────────────────────────────────────────────

private fun PeriodFilter.toSlashLabel() = when (this) {
    PeriodFilter.SESSION  -> "/ session"
    PeriodFilter.TODAY    -> "/ day"
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
    expandedDays     : Set<String>,
    expandedSessions : Set<Int>,
    onSelectPeriod   : (PeriodFilter) -> Unit,
    onToggleYear     : (Int) -> Unit,
    onToggleMonth    : (String) -> Unit,
    onToggleDay      : (String) -> Unit,
    onToggleSession  : (Int) -> Unit,
    onEarnMore       : () -> Unit = {},
    helpZones        : MutableMap<ru.iflex.timemvp.features.main.ui.HelpTopic,
                           androidx.compose.ui.geometry.Rect> = mutableMapOf(),
    helpMode         : Boolean = false,
    onHelpToggle     : () -> Unit = {},
    onQuestionRect   : (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val tree    = remember(data.sessions) { buildTree(data.sessions) }
    val accent  = MaterialTheme.colorScheme.accentColor
    val pl      = selectedPeriod.toSlashLabel()

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
                val periodContext: String? = when (selectedPeriod) {
                    PeriodFilter.SESSION  -> data.sessions.maxByOrNull { it.startTime }
                        ?.sessionNumber?.let { "Session #$it" }
                    PeriodFilter.TODAY    -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(Date())
                    PeriodFilter.MONTH    -> SimpleDateFormat("LLLL yyyy", Locale.ENGLISH)
                        .format(Date())
                    PeriodFilter.ALL_TIME -> null
                }
                if (periodContext != null) {
                    BasicText(
                        text     = periodContext,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp, end = 4.dp),
                        style    = labelStyle(
                            10.sp,
                            FontWeight.Medium,
                            MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.55f)
                        )
                    )
                }
            }
        }
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_SUMMARY)) {
                SummaryHeader(data)
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
            StatCard("GEOMETRIC MEAN + VARIABILITY", pl) {
                DeviationRow("Early",    data.deviationEarly, resultRedColor,  textColor, negated = true)
                DeviationRow("Absolute", data.deviationAll,   textColor,       textColor)
                DeviationRow("Late",     data.deviationLate,  resultBlueColor, textColor)
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text  = "Geometric mean of your timing errors and their spread (geometric CV%).",
                    style = labelStyle(11.sp, color = textColor.copy(alpha = 0.40f))
                )
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
                    style = labelStyle(11.sp, color = textColor.copy(alpha = 0.40f))
                )
            }
            }
        }

        // ── PACE OF LEARNING ─────────────────────────────────────────────
        item {
            Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.STAT_PACE)) {
            StatCard("PACE OF LEARNING", pl) {
                if (data.sparklinePaceOfLearning.size >= 2) {
                    PaceSparklineChart(data.sparklinePaceOfLearning, accent.copy(alpha = 0.65f), data.sparklineSessionDates)
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
                    .helpZoneDisposable(helpZones, HelpTopic.STAT_SESSIONS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    modifier = Modifier.weight(1f),
                    text  = "SESSIONS",
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
                TreeYearRow(
                    year          = yearNode.year,
                    attempts      = yearNode.totalAttempts,
                    geomEarlyMs   = yearNode.geomEarlyMs,
                    geomLateMs    = yearNode.geomLateMs,
                    trueThreeWins = yearNode.trueThreeWins,
                    luckyIndex    = yearNode.luckyIndex,
                    expanded      = yearNode.year in expandedYears,
                    onClick       = { onToggleYear(yearNode.year) }
                )
            }
            if (yearNode.year in expandedYears) {
                yearNode.months.forEach { monthNode ->
                    val monthKey = "${yearNode.year}-${monthNode.month}"
                    item {
                        TreeMonthRow(
                            label         = monthNode.label,
                            attempts      = monthNode.totalAttempts,
                            geomEarlyMs   = monthNode.geomEarlyMs,
                            geomLateMs    = monthNode.geomLateMs,
                            trueThreeWins = monthNode.trueThreeWins,
                            luckyIndex    = monthNode.luckyIndex,
                            expanded      = monthKey in expandedMonths,
                            onClick       = { onToggleMonth(monthKey) }
                        )
                    }
                    if (monthKey in expandedMonths) {
                        monthNode.days.forEach { dayNode ->
                            val dayKey = "${yearNode.year}-${monthNode.month}-${dayNode.day}"
                            item {
                                TreeDayRow(
                                    label         = dayNode.label,
                                    attempts      = dayNode.totalAttempts,
                                    geomEarlyMs   = dayNode.geomEarlyMs,
                                    geomLateMs    = dayNode.geomLateMs,
                                    trueThreeWins = dayNode.trueThreeWins,
                                    luckyIndex    = dayNode.luckyIndex,
                                    expanded      = dayKey in expandedDays,
                                    onClick       = { onToggleDay(dayKey) }
                                )
                            }
                            if (dayKey in expandedDays) {
                                items(dayNode.sessions) { session ->
                                    TreeSessionRow(
                                        session      = session,
                                        expanded     = session.sessionId in expandedSessions,
                                        toleranceMs  = data.toleranceMs,
                                        onToggle     = { onToggleSession(session.sessionId) }
                                    )
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
//  Summary Header (всегда ALL_TIME)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryHeader(data: AnalyticsData) {
    val textColor = MaterialTheme.colorScheme.baseTextColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SERENITY COINS | PLAY TIME
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text  = "SERENITY COINS",
                    style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
                )
                BasicText(
                    text  = "%.2f".format(data.periodScm),
                    style = metricStyle(16.sp, FontWeight.SemiBold, textColor)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicText(
                    text  = "PLAY TIME",
                    style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
                )
                BasicText(
                    text  = formatDuration(data.periodPlayTimeMs),
                    style = metricStyle(16.sp, FontWeight.SemiBold, textColor)
                )
            }
        }

        // Stats row: label on top, value below
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactStat(data.periodSessionsCount.toString(), "SESSIONS", textColor)
            CompactStat(data.periodAttempts.toString(),      "ATTEMPTS", textColor)
            CompactStat(data.periodWins.toString(),          "WINS",     resultGreenColor)
        }

        // Pace of Play row
        if (data.paceOfPlayPerMin > 0 || data.practiceDensityMinPerDay > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        text  = "PACE OF PLAY",
                        style = labelStyle(8.sp, color = textColor.copy(alpha = subtleText))
                    )
                    BasicText(
                        text  = if (data.paceOfPlayPerMin > 0) "%.1f att/min".format(data.paceOfPlayPerMin) else "—",
                        style = metricStyle(13.sp, FontWeight.SemiBold, textColor)
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        text  = "PRACTICE",
                        style = labelStyle(8.sp, color = textColor.copy(alpha = subtleText))
                    )
                    BasicText(
                        text  = if (data.practiceDensityMinPerDay > 0) "%.1f min/day".format(data.practiceDensityMinPerDay) else "—",
                        style = metricStyle(13.sp, FontWeight.SemiBold, textColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStat(value: String, label: String, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text  = label,
            style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
        )
        BasicText(
            text  = formatNumber(value),
            style = metricStyle(16.sp, FontWeight.SemiBold, textColor)
        )
    }
}

/** Format large numbers with space as thousands separator */
private fun formatNumber(value: String): String {
    val num = value.toLongOrNull() ?: return value
    return "%,d".format(num).replace(',', ' ')
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

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Название экрана заменено на маленькую bar-chart иконку — таже, что и
        // в BottomNav у вкладки Analytics. Так экран идентифицируется одним взглядом
        // без съедания ширины у 4 фильтров (Session/Today/Month/All time).
        Image(
            painter            = painterResource(id = ru.iflex.timemvp.R.drawable.ic_stat),
            contentDescription = null,
            colorFilter        = ColorFilter.tint(accent),
            modifier           = Modifier
                .padding(end = 8.dp)
                .size(18.dp)
        )
        PeriodFilter.entries.forEach { period ->
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
                    color = accent.copy(alpha = if (helpActive) 1f else 0.75f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .onGloballyPositioned { onQuestionRect(it.boundsInRoot()) }
                .clickable { onHelpClick() },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text  = "?",
                style = TextStyle(
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = accent.copy(alpha = 0.95f)
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
    topEarly: List<ru.iflex.timemvp.features.stats.domain.entity.TopDeviation> = emptyList(),
    topLate:  List<ru.iflex.timemvp.features.stats.domain.entity.TopDeviation> = emptyList()
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
        color         = textColor.copy(alpha = 0.45f),
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
                if (n >= 1000L) "%,d".format(n).replace(',', ' ') else n.toString()

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
                    val floorFormatting   = { dev: ru.iflex.timemvp.features.stats.domain.entity.TopDeviation ->
                        // Разделитель разрядов пробелом для count > 999.
                        val countStr = "%,d".format(dev.count).replace(',', ' ')
                        "%.3f x %s".format(abs(dev.errorMs) / 1000.0, countStr)
                    }
                    val sortedModes = modeRows.sortedBy { it.second.errorMs }

                    // Заранее размещаем метки, вычисляем нужно ли переходить в
                    // «разнесённый» режим и сохраняем результат для использования
                    // как соединителями сверху, так и подписями ниже.
                    data class LayoutItem(
                        val dev: ru.iflex.timemvp.features.stats.domain.entity.TopDeviation,
                        val labelX: Float, val labelW: Float,
                        val color: Color, val text: String,
                        val stripFrac: Float,
                        val spread: Boolean
                    )

                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val innerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            maxWidth.toPx()
                        }
                        val leftReservePx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            42.dp.toPx()   // место для «Modes»
                        }
                        val padPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            6.dp.toPx()
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
                            // левую полосу под «Modes».
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
                                    // Не наезжать на «Modes» слева и правый край справа.
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
                            // Этаж с подписями. Слева — заголовок «Modes». Весь блок
                            // обведён тонкой линией.
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
                                    text     = "Modes",
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
 * Опциональная подпись слева (например «Modes» на среднем этаже).
 */
/**
 * Оставляем ровно по ОДНОЙ моде на цвет — самую частую среди early (красные) и
 * самую частую среди late (синие). Обе моды теперь на одном этаже, поэтому
 * значение floor всегда 0. Все остальные модусы (и их отметки на ленте) не рисуем.
 */
private fun assignModesToFloors(
    topEarly: List<ru.iflex.timemvp.features.stats.domain.entity.TopDeviation>,
    topLate:  List<ru.iflex.timemvp.features.stats.domain.entity.TopDeviation>
): List<Pair<Int, ru.iflex.timemvp.features.stats.domain.entity.TopDeviation>> {
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
        Column {
            BasicText(
                text  = label,
                style = labelStyle(11.sp, FontWeight.Medium, color = textColor.copy(alpha = 0.65f))
            )
            if (note != null) BasicText(
                text  = note,
                style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
            )
        }
        BasicText(
            text  = value,
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
    if (ms <= 0.0) "—" else ms.roundToInt().toString()

/** attempts ÷ clean true-three wins, "—" if no true-three win in this node. */
private fun formatAttemptsPerWin(attempts: Int, trueThreeWins: Int): String {
    if (trueThreeWins <= 0) return "—"
    val ratio = attempts.toDouble() / trueThreeWins
    return if (ratio >= 1000.0) {
        val intPart = ratio.toLong()
        val frac    = ((ratio - intPart) * 10).toLong()
        "%s.%d".format("%,d".format(intPart).replace(',', ' '), frac)
    } else "%.1f".format(ratio)
}

/** Lucky index formatted as "0.87". "—" if lucky index is 0.0 (нет данных / нет побед). */
private fun formatLucky(l: Double): String =
    if (l <= 0.0) "—" else "%.2f".format(l)

@Composable
private fun TreeYearRow(year: Int, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, onClick: () -> Unit) =
    TreeRow(0.dp, year.toString(), attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        metricStyle(14.sp, FontWeight.Bold, MaterialTheme.colorScheme.baseTextColor), onClick)

@Composable
private fun TreeMonthRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, onClick: () -> Unit) =
    TreeRow(12.dp, label, attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        metricStyle(13.sp, FontWeight.Medium, MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.85f)), onClick)

@Composable
private fun TreeDayRow(label: String, attempts: Int, geomEarlyMs: Double, geomLateMs: Double, trueThreeWins: Int, luckyIndex: Double, expanded: Boolean, onClick: () -> Unit) =
    TreeRow(24.dp, label, attempts, geomEarlyMs, geomLateMs, trueThreeWins, luckyIndex, expanded,
        labelStyle(12.sp, color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.70f)), onClick)

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
    onClick       : () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = indent, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = if (expanded) "▾ " else "▸ ", style = labelStyle2.copy(color = labelStyle2.color.copy(alpha = 0.6f)))
            BasicText(text = label, style = labelStyle2)
        }
        // Lucky index — leftmost data column; центрирован по ширине столбца,
        // как его заголовок "lucky" (Alex 2026-07-12).
        val luckyColor = if (luckyIndex <= 0.0)
            textColor.copy(alpha = subtleText) else textColor
        BasicText(
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
            slashStyle = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.4f)),
            rightStyle = metricStyle(11.sp, FontWeight.Normal, resultBlueColor.copy(alpha = 0.9f)),
            leftW = TREE_EL_LEFT_W, slashW = TREE_EL_SLASH_W, rightW = TREE_EL_RIGHT_W
        )
        // att4win — attempts per one clean-three win; центрирован по столбцу
        BasicText(
            modifier = Modifier.width(TREE_COL_ATT4WIN),
            text  = formatAttemptsPerWin(attempts, trueThreeWins),
            style = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.5f))
                .copy(textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun TreeSessionRow(
    session     : SessionTreeData,
    expanded    : Boolean,
    toleranceMs : Double = 0.0,
    onToggle    : () -> Unit
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
            .clickable { onToggle() }
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
                    style = labelStyle(9.sp, color = textColor.copy(alpha = 0.30f))
                )
                BasicText(
                    text  = startStr,
                    style = metricStyle(11.sp, FontWeight.Medium, textColor.copy(alpha = 0.75f))
                )
            }
            // Lucky index for the session — центрирован по столбцу
            val lucky = session.luckyIndex()
            val luckyColor = if (lucky <= 0.0)
                textColor.copy(alpha = subtleText) else textColor
            BasicText(
                modifier = Modifier.width(TREE_COL_LUCKY),
                text  = formatLucky(lucky),
                style = metricStyle(11.sp, FontWeight.Normal, luckyColor)
                    .copy(textAlign = TextAlign.Center)
            )
            // Geom. mean — early(red) / late(blue) error, ms
            SlashCell(
                leftText   = formatGeomMs(session.geomEarlyMs(), early = true),
                rightText  = formatGeomMs(session.geomLateMs(), early = false),
                leftStyle  = metricStyle(11.sp, FontWeight.Normal, resultRedColor.copy(alpha = 0.9f)),
                slashStyle = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.4f)),
                rightStyle = metricStyle(11.sp, FontWeight.Normal, resultBlueColor.copy(alpha = 0.9f)),
                leftW = TREE_EL_LEFT_W, slashW = TREE_EL_SLASH_W, rightW = TREE_EL_RIGHT_W
            )
            // att4win — attempts per one clean-three win; центрирован по столбцу
            BasicText(
                modifier = Modifier.width(TREE_COL_ATT4WIN),
                text  = formatAttemptsPerWin(session.attempts, session.trueThreeWins()),
                style = labelStyle(11.sp, color = textColor.copy(alpha = 0.5f))
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
                style = metricStyle(10.sp, FontWeight.Normal, textColor.copy(alpha = 0.40f))
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
                style = labelStyle(9.sp, color = textColor.copy(alpha = 0.35f))
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
private fun AttemptBarsSection(bars: List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>) {
    val maxAbs = bars.maxOf { abs(it.errorMs) }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        bars.forEach { bar -> AttemptBarRow(bar = bar, maxAbsMs = maxAbs) }
    }
}

@Composable
private fun AttemptBarRow(
    bar      : ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData,
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
            style    = labelStyle(9.sp, color = textColor.copy(alpha = 0.35f))
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
    bars        : List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>,
    toleranceMs : Double = 0.0
) {
    if (bars.size < 2) return
    val textColor = MaterialTheme.colorScheme.baseTextColor

    val errors = bars.map { it.errorMs }
    val minE = errors.min()
    val maxE = errors.max()
    if (maxE - minE < 1.0) return

    // Symmetric scale around zero — zero is always at canvas center
    val scaleMax = maxOf(abs(minE), abs(maxE)).coerceAtLeast(1.0)
    val rangeMin = -scaleMax
    val bucketCount = 9
    val bucketW = (2.0 * scaleMax) / bucketCount
    val counts = IntArray(bucketCount)
    errors.forEach { e ->
        val idx = ((e - rangeMin) / bucketW).toInt().coerceIn(0, bucketCount - 1)
        counts[idx]++
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
            val barW = gap * 0.72f

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
                    inTolerance        -> resultGreenColor.copy(alpha = 0.70f)
                    bucketCenter < 0.0 -> resultRedColor.copy(alpha = 0.65f)
                    else               -> resultBlueColor.copy(alpha = 0.65f)
                }
                val x = gap * i + (gap - barW) / 2
                drawRect(
                    color   = color,
                    topLeft = Offset(x, size.height - barH),
                    size    = Size(barW, barH)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Pace Sparkline Chart (with axes and legend)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaceSparklineChart(values: List<Float>, lineColor: Color, sessionDates: List<Long> = emptyList()) {
    if (values.size < 2) return
    val textColor   = MaterialTheme.colorScheme.baseTextColor
    val axisAlpha   = 0.25f
    val yAxisWidth  = 44.dp
    val chartHeight = 60.dp

    val minV  = values.min()
    val maxV  = values.max()
    val range = (maxV - minV).coerceAtLeast(0.001f)

    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.ENGLISH) }

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
                BasicText("%+.2f".format(maxV), style = labelStyle(8.sp, color = textColor.copy(alpha = 0.45f)))
                BasicText("%+.2f".format(minV), style = labelStyle(8.sp, color = textColor.copy(alpha = 0.45f)))
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

                // Trend line
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = if (values.size > 1) w * i / (values.size - 1) else w / 2f
                    val y = h - pad - ((v - minV) / range) * (h - 2 * pad)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

                // Current-value dot
                val lastX = w
                val lastY = h - pad - ((values.last() - minV) / range) * (h - 2 * pad)
                drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
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
                if (sessionDates.size >= values.size) {
                    values.indices.forEach { i ->
                        BasicText(
                            text  = dateFmt.format(Date(sessionDates[i])),
                            style = labelStyle(8.sp, color = textColor.copy(alpha = 0.35f))
                        )
                    }
                } else {
                    BasicText("older", style = labelStyle(8.sp, color = textColor.copy(alpha = 0.35f)))
                    BasicText("now",   style = labelStyle(8.sp, color = textColor.copy(alpha = 0.35f)))
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
            BasicText("SC/att per session", style = labelStyle(8.sp, color = lineColor.copy(alpha = 0.7f)))
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
private fun fmtSigma(pct: Double): String = when {
    pct <= 0.0  -> "—"
    pct > 999.0 -> ">999%"
    else        -> "%.0f%%".format(pct)
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
            text  = label,
            style = labelStyle(11.sp, FontWeight.Medium, labelColor)
        )
        val displayMs = if (negated) -profile.medianMs else profile.medianMs
        BasicText(
            text  = if (profile.medianMs != 0.0)
                // «±» ставим ВПЛОТНУЮ к своей variability (нет пробела справа),
                // а от предыдущего числа отделяем двойным пробелом — знак читается
                // как часть variability, а не разделитель между двумя числами.
                "%s  ±%s".format(formatMs(displayMs), fmtSigma(profile.stdPct))
            else "—",
            style = metricStyle(12.sp, FontWeight.Medium, textColor.copy(alpha = 0.8f))
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
    return if (h > 0) "${h}h %02dm %02ds".format(m, s)
    else "%dm %02ds".format(m, s)
}

// ──────────────────────────────────────────────────────────────────────────────
//  Дерево год → месяц → день
// ──────────────────────────────────────────────────────────────────────────────

private data class SessionDayNode(val day: Int, val label: String,
    val sessions: List<SessionTreeData>, val totalAttempts: Int, val totalWins: Int,
    val geomEarlyMs: Double, val geomLateMs: Double, val trueThreeWins: Int,
    val luckyIndex: Double)
private data class SessionMonthNode(val month: Int, val label: String,
    val days: List<SessionDayNode>, val totalAttempts: Int, val totalWins: Int,
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
private fun geomEarlyMsOf(bars: List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>): Double =
    geometricMean(bars.filter { !it.isWin && it.errorMs < 0.0 }.map { abs(it.errorMs) })

/** Geometric mean of late (blue) attempt errors, ms. Excludes tolerance wins. */
private fun geomLateMsOf(bars: List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>): Double =
    geometricMean(bars.filter { !it.isWin && it.errorMs > 0.0 }.map { it.errorMs })

/**
 * Count of «чистых троек» — попадание в нулевой слот В ТОЧНОСТИ СВОЕЙ попытки
 * (та же логика, что countTrueThree в AnalyticsUseCase): L1 — окно ~100 мс
 * (округляется до 3.0), L2 — 10 мс, L3 — 1 мс. Tolerance-победы не считаются.
 * Раньше здесь было мс-точное |error| < 0.5 — из-за этого L1-строки дерева
 * показывали нули троек и lucky, хотя игрок бил чистые 3.0.
 */
private fun trueThreeWinsOf(bars: List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>): Int =
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
private fun luckyIndexOf(bars: List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>): Double {
    if (bars.isEmpty()) return 0.0
    val errs = bars.map { it.errorMs }
    val range = (errs.maxOrNull() ?: 0.0) - (errs.minOrNull() ?: 0.0)
    val accuracy = bars.groupBy { it.accuracy }.maxByOrNull { it.value.size }?.key ?: 3
    val slotMs = when (accuracy) { 3 -> 1.0; 2 -> 10.0; else -> 100.0 }
    val outcomeSlots = (range / slotMs).toLong().coerceAtLeast(1L) + 1L
    val dispersion = 1.0 / outcomeSlots
    val trueThreeWins = trueThreeWinsOf(bars)
    val empirical = trueThreeWins.toDouble() / bars.size
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
                    SessionMonthNode(month, monthLabel, days,
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
