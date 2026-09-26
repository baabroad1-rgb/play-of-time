package ru.iflex.timemvp.features.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

// ──────────────────────────────────────────────────────────────────────────────
//  StatScreen — точка входа
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun StatScreen(drawerState: DrawerState, onEarnMore: () -> Unit = {}) {
    val viewModel  = koinViewModel<StatsViewModel>()
    val state      = viewModel.viewState.value
    val setEvent: (Event) -> Unit = { viewModel.setEvent(it) }

    Screen(
        title          = "Analytics",
        drawerState    = drawerState,
        actionIconId   = R.drawable.ic_export,
        onActionIconClick = { setEvent(Event.ExportToExel) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.baseBackgroundColor)
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
                    expandedDays     = state.expandedDays,
                    expandedSessions = state.expandedSessions,
                    onSelectPeriod   = { setEvent(Event.SelectPeriod(it)) },
                    onToggleYear     = { setEvent(Event.ToggleYear(it)) },
                    onToggleMonth    = { setEvent(Event.ToggleMonth(it)) },
                    onToggleDay      = { setEvent(Event.ToggleDay(it)) },
                    onToggleSession  = { setEvent(Event.ToggleSession(it)) },
                    onEarnMore       = onEarnMore
                )

                else -> BasicText(
                    text     = "No data yet",
                    modifier = Modifier.align(Alignment.Center),
                    style    = labelStyle(color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = subtleText))
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
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Основное содержимое
// ──────────────────────────────────────────────────────────────────────────────

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
    onEarnMore       : () -> Unit = {}
) {
    val tree = remember(data.sessions) { buildTree(data.sessions) }
    var helpText by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Шапка: Total SCM всегда ALL_TIME ────────────────────────────
        item { SummaryHeader(data, onScmHelp = {
            helpText = "Serenity Coin (SCM) — валюта спокойствия. Начисляется за точность и стабильность: учитывает не только попадание в цель, но и консистентность — связь текущей попытки с соседними. Чем спокойнее и стабильнее система, тем больше монет."
        }) }

        // ── Period Pill ──────────────────────────────────────────────────
        item { PeriodPill(selectedPeriod, onSelectPeriod) }

        // ── Pace ─────────────────────────────────────────────────────────
        item {
            val paceLabel = when (selectedPeriod) {
                PeriodFilter.SESSION  -> "per session"
                PeriodFilter.TODAY    -> "per day"
                PeriodFilter.MONTH    -> "per month"
                PeriodFilter.ALL_TIME -> "overall"
            }
            StatCard(title = "PACE", onHelp = {
                helpText = "Pace of Play — скорость попыток (попыток в минуту). Pace of Learning — прирост SCM на одну попытку: насколько быстро система обучается. Practice Density — сколько минут в день вы тренируетесь."
            }) {
                CardMetricRow(
                    label = "Pace of Learning $paceLabel",
                    value = "%+.1f SC/att".format(data.paceOfLearning)
                )
                if (data.sparklinePaceOfLearning.size >= 2) {
                    Spacer(Modifier.height(4.dp))
                    TrendSparkline(
                        data.sparklinePaceOfLearning,
                        MaterialTheme.colorScheme.accentColor.copy(alpha = 0.65f)
                    )
                }
                CardMetricRow(
                    label = "Pace of Play",
                    value = if (data.paceOfPlayPerMin > 0)
                        "%.1f att/min".format(data.paceOfPlayPerMin) else "—"
                )
                CardMetricRow(
                    label = "Practice Density",
                    value = if (data.practiceDensityMinPerDay > 0)
                        "%.1f min/day".format(data.practiceDensityMinPerDay) else "—"
                )
            }
        }

        // ── Range of Errors (visual bar) ─────────────────────────────────
        item {
            StatCard(title = "RANGE OF ERRORS", onHelp = {
                helpText = "Диапазон, в который попадает 94% результатов (от 3-го до 97-го процентиля), без случайных выбросов."
            }) {
                RangeBar(earlyMs = data.rangeEarlyMs, lateMs = data.rangeLateMs)
            }
        }

        // ── Top Deviations (Modes) + Deviation Profile ────────────────
        item { TopDeviationsCard(data, onHelp = {
            helpText = "Modes of Deviation: ваши наиболее типичные ошибки — значения, которые повторяются чаще всего. Early (красные) = система возбуждена, нажатие раньше цели. Late (синие) = торможение, нажатие позже цели."
        }) }

        // ── Deviation Profile (compact rows, not a separate card) ────────
        item {
            val textColor = MaterialTheme.colorScheme.baseTextColor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardBg)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DeviationRow("early",    data.deviationEarly, resultRedColor,  textColor)
                DeviationRow("absolute", data.deviationAll,   textColor,       textColor)
                DeviationRow("late",     data.deviationLate,  resultBlueColor, textColor)
            }
        }

        // ── Chance of Win (True Three Hit Rate) ──────────────────────────
        item {
            StatCard(title = "CHANCE OF WIN", onHelp = {
                helpText = "True Three Hit Rate — доля попыток, в которых вы попали точно в цель с нужной точностью."
            }) {
                CardMetricRow(
                    label      = "Hit rate",
                    value      = "%.1f%%".format(data.trueThreeHitRatePct),
                    valueColor = resultGreenColor
                )
                CardMetricRow(
                    label = "Attempts per win",
                    value = if (data.attemptsPerWin > 0) {
                        // For attempts/win, lower is better — invert the arrow from win rate trend
                        val winTrend = trendArrow(data.sparklineChanceOfWin)
                        val arrow = when (winTrend) { "↑" -> "↓"; "↓" -> "↑"; else -> "→" }
                        "%.1f %s".format(data.attemptsPerWin, arrow)
                    } else "—",
                    note = if (data.sparklineChanceOfWin.size >= 2) {
                        val winTrend = trendArrow(data.sparklineChanceOfWin)
                        when (winTrend) {
                            "↑" -> "favored already"    // win rate up = fewer attempts needed
                            "↓" -> "needs more practice" // win rate down = more attempts needed
                            else -> "stable"
                        }
                    } else null
                )
                if (data.sparklineChanceOfWin.size >= 2) {
                    Spacer(Modifier.height(4.dp))
                    TrendSparkline(data.sparklineChanceOfWin, resultGreenColor.copy(alpha = 0.7f))
                }
            }
        }

        // ── Sessions Tree ────────────────────────────────────────────────
        item {
            val baseColor = MaterialTheme.colorScheme.baseTextColor
            BasicText(
                text  = "SESSIONS",
                style = labelStyle(size = 10.sp, weight = FontWeight.SemiBold,
                    color = baseColor.copy(alpha = subtleText))
            )
        }

        tree.forEach { yearNode ->
            item {
                TreeYearRow(
                    year      = yearNode.year,
                    attempts  = yearNode.totalAttempts,
                    wins      = yearNode.totalWins,
                    expanded  = yearNode.year in expandedYears,
                    onClick   = { onToggleYear(yearNode.year) }
                )
            }
            if (yearNode.year in expandedYears) {
                yearNode.months.forEach { monthNode ->
                    val monthKey = "${yearNode.year}-${monthNode.month}"
                    item {
                        TreeMonthRow(
                            label    = monthNode.label,
                            attempts = monthNode.totalAttempts,
                            wins     = monthNode.totalWins,
                            expanded = monthKey in expandedMonths,
                            onClick  = { onToggleMonth(monthKey) }
                        )
                    }
                    if (monthKey in expandedMonths) {
                        monthNode.days.forEach { dayNode ->
                            val dayKey = "${yearNode.year}-${monthNode.month}-${dayNode.day}"
                            item {
                                TreeDayRow(
                                    label    = dayNode.label,
                                    attempts = dayNode.totalAttempts,
                                    wins     = dayNode.totalWins,
                                    expanded = dayKey in expandedDays,
                                    onClick  = { onToggleDay(dayKey) }
                                )
                            }
                            if (dayKey in expandedDays) {
                                items(dayNode.sessions) { session ->
                                    TreeSessionRow(
                                        session  = session,
                                        expanded = session.sessionId in expandedSessions,
                                        onToggle = { onToggleSession(session.sessionId) }
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
    } // end LazyColumn

    // ── Help modal (поверх контента) ────────────────────────────────────
    helpText?.let { text ->
        HelpModal(text = text, onClose = { helpText = null })
    }
    } // end Box
}

// ──────────────────────────────────────────────────────────────────────────────
//  Summary Header (всегда ALL_TIME)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryHeader(data: AnalyticsData, onScmHelp: (() -> Unit)? = null) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent    = MaterialTheme.colorScheme.accentColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: "Total" label + help button
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BasicText(
                text  = "Total",
                style = labelStyle(10.sp, FontWeight.SemiBold, textColor.copy(alpha = subtleText))
            )
            if (onScmHelp != null) {
                BasicText(
                    text     = "?",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onScmHelp() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style    = labelStyle(10.sp, FontWeight.Bold, accent.copy(alpha = 0.55f))
                )
            }
        }

        // Row 2: Big SCM + play time + wins
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text  = "%.1f".format(data.totalScm),
                style = metricStyle(size = 36.sp, color = accent)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BasicText(
                    text  = formatDuration(data.totalPlayTimeMs),
                    style = metricStyle(size = 14.sp, weight = FontWeight.Medium, color = textColor.copy(alpha = 0.7f))
                )
                BasicText(
                    text  = data.totalWins.toString(),
                    style = metricStyle(size = 14.sp, weight = FontWeight.Medium, color = resultGreenColor.copy(alpha = 0.8f))
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(dividerClr))
        Spacer(Modifier.height(2.dp))

        // Row 3: Compact stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactStat(data.daysSinceStart.toString(), "days", textColor)
            CompactStat(data.sessionsCount.toString(), "sessions", textColor)
            CompactStat(data.totalAttempts.toString(), "attempts", textColor)
            CompactStat(data.totalWins.toString(), "wins", textColor)
        }

        // Since date
        BasicText(
            text  = "since ${data.startDateStr}",
            style = labelStyle(9.sp, color = textColor.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun CompactStat(value: String, label: String, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text  = formatNumber(value),
            style = metricStyle(14.sp, FontWeight.SemiBold, textColor)
        )
        BasicText(
            text  = label,
            style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
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
private fun PeriodPill(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent    = MaterialTheme.colorScheme.accentColor

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
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
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Activation Spectrum (Deviation Profile)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivationSpectrumCard(data: AnalyticsData, onHelp: (() -> Unit)? = null) {
    val textColor = MaterialTheme.colorScheme.baseTextColor

    StatCard(title = "ACTIVATION SPECTRUM", onHelp = onHelp) {
        // Header
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(76.dp))
            BasicText(
                text     = "Median",
                modifier = Modifier.weight(1f),
                style    = labelStyle(size = 10.sp,
                    color = textColor.copy(alpha = subtleText),
                    weight = FontWeight.Medium).copy(textAlign = TextAlign.Center)
            )
            BasicText(
                text     = "σ%",
                modifier = Modifier.weight(1f),
                style    = labelStyle(size = 10.sp,
                    color = textColor.copy(alpha = subtleText),
                    weight = FontWeight.Medium).copy(textAlign = TextAlign.Center)
            )
        }

        Spacer(Modifier.height(6.dp))

        SpectrumRow(
            label      = "Absolute",
            subtitle   = "combined",
            medianStr  = formatMs(data.deviationAll.medianMs),
            sigmaStr   = fmtSigma(data.deviationAll.stdPct),
            labelColor = textColor
        )
        Spacer(Modifier.height(4.dp))
        SpectrumRow(
            label      = "Early",
            subtitle   = "↑ over-excitation",
            medianStr  = formatMs(-data.deviationEarly.medianMs),   // early = abs stored, show negative
            sigmaStr   = fmtSigma(data.deviationEarly.stdPct),
            labelColor = resultRedColor
        )
        Spacer(Modifier.height(4.dp))
        SpectrumRow(
            label      = "Late",
            subtitle   = "↓ inhibition",
            medianStr  = formatMs(data.deviationLate.medianMs),
            sigmaStr   = fmtSigma(data.deviationLate.stdPct),
            labelColor = resultBlueColor
        )
    }
}

@Composable
private fun SpectrumRow(
    label      : String,
    subtitle   : String,
    medianStr  : String,
    sigmaStr   : String,
    labelColor : Color
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(76.dp)) {
            BasicText(text = label,    style = metricStyle(12.sp, FontWeight.SemiBold, labelColor))
            BasicText(text = subtitle, style = labelStyle(9.sp, color = labelColor.copy(alpha = 0.6f)))
        }
        BasicText(
            text     = medianStr,
            modifier = Modifier.weight(1f),
            style    = metricStyle(12.sp, FontWeight.Medium, labelColor)
                .copy(textAlign = TextAlign.Center)
        )
        BasicText(
            text     = sigmaStr,
            modifier = Modifier.weight(1f),
            style    = metricStyle(12.sp, color = labelColor.copy(alpha = 0.75f))
                .copy(textAlign = TextAlign.Center)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Range Bar (visual P3–P97 indicator)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun RangeBar(earlyMs: Double, lateMs: Double) {
    val textColor = MaterialTheme.colorScheme.baseTextColor

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text  = formatMs(earlyMs),
                style = metricStyle(14.sp, FontWeight.SemiBold, resultRedColor)
            )
            BasicText(
                text  = "0",
                style = labelStyle(10.sp, color = textColor.copy(alpha = 0.3f))
            )
            BasicText(
                text  = formatMs(lateMs),
                style = metricStyle(14.sp, FontWeight.SemiBold, resultBlueColor)
            )
        }

        // Visual bar
        val totalRange = abs(earlyMs) + abs(lateMs)
        val earlyFraction = if (totalRange > 0) (abs(earlyMs) / totalRange).toFloat() else 0.5f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            drawRect(color = Color(0xFF2A2A2A), size = size)
            drawRect(
                color   = resultRedColor.copy(alpha = 0.65f),
                topLeft = Offset.Zero,
                size    = Size(size.width * earlyFraction, size.height)
            )
            drawRect(
                color   = resultBlueColor.copy(alpha = 0.65f),
                topLeft = Offset(size.width * earlyFraction, 0f),
                size    = Size(size.width * (1f - earlyFraction), size.height)
            )
            drawLine(
                color       = Color.White.copy(alpha = 0.5f),
                start       = Offset(size.width * earlyFraction, 0f),
                end         = Offset(size.width * earlyFraction, size.height),
                strokeWidth = 2f
            )
        }

        BasicText(
            text  = "94% of attempts fall within this range",
            style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Top Deviations
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopDeviationsCard(data: AnalyticsData, onHelp: (() -> Unit)? = null) {
    StatCard(title = "MODES OF DEVIATION", onHelp = onHelp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Early column
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text  = "Early",
                    style = labelStyle(10.sp, FontWeight.SemiBold, resultRedColor)
                )
                data.topEarly.forEach { dev ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text  = formatMs(dev.errorMs),
                            style = metricStyle(12.sp, color = resultRedColor)
                        )
                        BasicText(
                            text  = "  ×${dev.count}",
                            style = labelStyle(10.sp, color = resultRedColor.copy(alpha = 0.65f))
                        )
                    }
                }
                if (data.topEarly.isEmpty()) BasicText("—", style = metricStyle(12.sp))
            }
            // Late column
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text  = "Late",
                    style = labelStyle(10.sp, FontWeight.SemiBold, resultBlueColor)
                )
                data.topLate.forEach { dev ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text  = formatMs(dev.errorMs),
                            style = metricStyle(12.sp, color = resultBlueColor)
                        )
                        BasicText(
                            text  = "  ×${dev.count}",
                            style = labelStyle(10.sp, color = resultBlueColor.copy(alpha = 0.65f))
                        )
                    }
                }
                if (data.topLate.isEmpty()) BasicText("—", style = metricStyle(12.sp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Stat Card (универсальная карточка)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    title   : String,
    onHelp  : (() -> Unit)? = null,
    content : @Composable () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent    = MaterialTheme.colorScheme.accentColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            if (onHelp != null) {
                BasicText(
                    text     = "?",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onHelp() }
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style    = labelStyle(10.sp, FontWeight.Bold, accent.copy(alpha = 0.55f))
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(dividerClr))
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
                style = labelStyle(13.sp, color = textColor.copy(alpha = 0.65f))
            )
            if (note != null) BasicText(
                text  = note,
                style = labelStyle(9.sp, color = textColor.copy(alpha = subtleText))
            )
        }
        BasicText(
            text  = value,
            style = metricStyle(14.sp, FontWeight.SemiBold,
                if (valueColor == Color.Unspecified) textColor else valueColor)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Дерево сессий
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TreeYearRow(year: Int, attempts: Int, wins: Int, expanded: Boolean, onClick: () -> Unit) =
    TreeRow(0.dp, year.toString(), attempts, wins, expanded,
        metricStyle(14.sp, FontWeight.Bold, MaterialTheme.colorScheme.baseTextColor), onClick)

@Composable
private fun TreeMonthRow(label: String, attempts: Int, wins: Int, expanded: Boolean, onClick: () -> Unit) =
    TreeRow(12.dp, label, attempts, wins, expanded,
        metricStyle(13.sp, FontWeight.Medium, MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.85f)), onClick)

@Composable
private fun TreeDayRow(label: String, attempts: Int, wins: Int, expanded: Boolean, onClick: () -> Unit) =
    TreeRow(24.dp, label, attempts, wins, expanded,
        labelStyle(12.sp, color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.70f)), onClick)

@Composable
private fun TreeRow(
    indent     : Dp,
    label      : String,
    attempts   : Int,
    wins       : Int,
    expanded   : Boolean,
    labelStyle2: TextStyle,
    onClick    : () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = indent, top = 4.dp, bottom = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = if (expanded) "▾ " else "▸ ", style = labelStyle2.copy(color = labelStyle2.color.copy(alpha = 0.6f)))
            BasicText(text = label, style = labelStyle2)
        }
        BasicText(
            text  = "$attempts / $wins",
            style = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun TreeSessionRow(
    session  : SessionTreeData,
    expanded : Boolean,
    onToggle : () -> Unit
) {
    val textColor  = MaterialTheme.colorScheme.baseTextColor
    val sdf        = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr   = sdf.format(Date(session.startTime))
    val durationMs = session.endTime - session.startTime
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
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text  = if (expanded) "▾ " else "▸ ",
                    style = labelStyle(9.sp, color = textColor.copy(alpha = 0.30f))
                )
                BasicText(
                    text  = startStr,
                    style = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.5f))
                )
            }
            BasicText(
                text  = formatDuration(durationMs),
                style = metricStyle(11.sp, FontWeight.Normal, textColor.copy(alpha = 0.40f))
            )
            BasicText(
                text  = "${session.attempts} att",
                style = labelStyle(11.sp, color = textColor.copy(alpha = 0.5f))
            )
            BasicText(
                text  = "$winPct% ✓",
                style = metricStyle(11.sp, FontWeight.Medium, resultGreenColor.copy(alpha = 0.85f))
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
            MiniHistogram(session.attemptBars)
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
    val color = when {
        bar.isWin       -> resultGreenColor
        bar.errorMs < 0 -> resultRedColor
        else            -> resultBlueColor
    }
    val fraction  = (abs(bar.errorMs) / maxAbsMs).toFloat().coerceIn(0.02f, 1f)
    val textColor = MaterialTheme.colorScheme.baseTextColor

    Row(
        modifier          = Modifier.fillMaxWidth().padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // номер попытки
        BasicText(
            text     = "%2d".format(bar.number),
            modifier = Modifier.width(20.dp),
            style    = labelStyle(8.sp, color = textColor.copy(alpha = 0.30f))
        )
        // трек + заливка
        Box(Modifier.weight(1f).height(5.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(dividerClr)
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.72f))
            )
        }
        Spacer(Modifier.width(6.dp))
        // значение ошибки
        BasicText(
            text     = formatMs(bar.errorMs),
            modifier = Modifier.width(46.dp),
            style    = metricStyle(8.sp, FontWeight.Normal, color.copy(alpha = 0.72f))
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Help Modal
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun HelpModal(text: String, onClose: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent    = MaterialTheme.colorScheme.accentColor

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText(
                text  = text,
                style = labelStyle(13.sp, color = textColor.copy(alpha = 0.85f))
            )
            BasicText(
                text     = "OK",
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                style    = labelStyle(12.sp, FontWeight.SemiBold, accent)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Mini Histogram (распределение внутри сессии)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniHistogram(bars: List<ru.iflex.timemvp.features.stats.domain.entity.AttemptBarData>) {
    if (bars.size < 2) return
    val textColor = MaterialTheme.colorScheme.baseTextColor

    val errors = bars.map { it.errorMs }
    val minE = errors.min()
    val maxE = errors.max()
    if (maxE - minE < 1.0) return   // все одинаковые — нет смысла

    val bucketCount = 9
    val bucketW = (maxE - minE) / bucketCount
    val counts = IntArray(bucketCount)
    errors.forEach { e ->
        val idx = ((e - minE) / bucketW).toInt().coerceIn(0, bucketCount - 1)
        counts[idx]++
    }
    val maxCount = counts.max().coerceAtLeast(1)

    // Бакет, содержащий 0ms (граница early/late)
    val zeroBucket = if (minE < 0 && maxE > 0)
        ((-minE) / bucketW).toInt().coerceIn(0, bucketCount - 1) else -1

    Column(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
        BasicText(
            text  = "distribution",
            style = labelStyle(8.sp, color = textColor.copy(alpha = 0.25f))
        )
        Spacer(Modifier.height(3.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            val gap  = size.width / bucketCount
            val barW = gap * 0.72f

            counts.forEachIndexed { i, count ->
                if (count == 0) return@forEachIndexed
                val barH = (count.toFloat() / maxCount) * size.height * 0.9f
                val bucketCenter = minE + (i + 0.5) * bucketW
                val color = when {
                    i == zeroBucket    -> resultGreenColor.copy(alpha = 0.70f)
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

/** Trend arrow based on last two sparkline values */
private fun trendArrow(sparkline: List<Float>): String {
    if (sparkline.size < 2) return ""
    val prev = sparkline[sparkline.size - 2]
    val curr = sparkline.last()
    val diff = curr - prev
    return when {
        diff > 0.01f  -> "↑"
        diff < -0.01f -> "↓"
        else          -> "→"
    }
}

@Composable
private fun DeviationRow(
    label: String,
    profile: DeviationProfile,
    labelColor: Color,
    textColor: Color
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
        BasicText(
            text  = if (profile.medianMs != 0.0)
                "%s ± %s".format(formatMs(profile.medianMs), fmtSigma(profile.stdPct))
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
    val totalHours = ms / 3_600_000.0
    return if (totalHours >= 1.0) "%.2f h".format(totalHours)
    else {
        val totalMin = ms / 60_000.0
        "%.1f min".format(totalMin)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Дерево год → месяц → день
// ──────────────────────────────────────────────────────────────────────────────

private data class SessionDayNode(val day: Int, val label: String,
    val sessions: List<SessionTreeData>, val totalAttempts: Int, val totalWins: Int)
private data class SessionMonthNode(val month: Int, val label: String,
    val days: List<SessionDayNode>, val totalAttempts: Int, val totalWins: Int)
private data class SessionYearNode(val year: Int, val months: List<SessionMonthNode>,
    val totalAttempts: Int, val totalWins: Int)

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
                            SessionDayNode(day, dayLabel, dSessions,
                                dSessions.sumOf { it.attempts }, dSessions.sumOf { it.wins })
                        }
                        .sortedByDescending { it.day }
                    val monthLabel = cal.also {
                        it.set(year, month, 1)
                    }.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: "Month $month"
                    SessionMonthNode(month, monthLabel, days,
                        days.sumOf { it.totalAttempts }, days.sumOf { it.totalWins })
                }
                .sortedByDescending { it.month }
            SessionYearNode(year, months, months.sumOf { it.totalAttempts }, months.sumOf { it.totalWins })
        }
        .sortedByDescending { it.year }
}
