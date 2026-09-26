package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.features.main.domain.SessionComparisonStats
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor
import kotlin.math.roundToInt

private val BubbleBackground = Color(0xFFF5C02E)
private val BubbleText       = Color(0xFF1A1200)

@Composable
fun SessionSummaryOverlay(
    stats: SessionComparisonStats,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BubbleBackground)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Headline + dismiss ─────────────────────────────────────
            val headline = when {
                !stats.hasPrevious  -> "Session complete!"
                stats.overallBetter -> "✓ Session was BETTER than previous"
                stats.overallWorse  -> "Session was WORSE than previous"
                else                -> "Session was about the SAME as previous"
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text     = ru.iflex.timemvp.core.utils.tidyLineBreaks(headline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 28.dp),
                    style = TextStyle(
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = BubbleText,
                        textAlign  = TextAlign.Start
                    )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text  = "×",
                        style = TextStyle(
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = BubbleText.copy(alpha = 0.55f)
                        )
                    )
                }
            }

            // ── Stats comparison table ─────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BubbleText.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (stats.hasPrevious) {
                    SessionMetricRow(
                        label        = "SCM pace",
                        prevText     = "%.2f c/att".format(stats.scmPacePrevious),
                        currText     = "%.2f c/att".format(stats.scmPaceCurrent),
                        improved     = stats.scmPaceCurrent > stats.scmPacePrevious && stats.scmPacePrevious > 0,
                        worsened     = stats.scmPaceCurrent < stats.scmPacePrevious && stats.scmPacePrevious > 0,
                        higherBetter = true
                    )
                    SessionMetricRow(
                        label        = "Early errors",
                        prevText     = "${stats.earlyErrPreviousMs.roundToInt()} ms",
                        currText     = "${stats.earlyErrCurrentMs.roundToInt()} ms",
                        improved     = stats.earlyErrCurrentMs < stats.earlyErrPreviousMs && stats.earlyErrPreviousMs > 0,
                        worsened     = stats.earlyErrCurrentMs > stats.earlyErrPreviousMs && stats.earlyErrPreviousMs > 0,
                        higherBetter = false
                    )
                    SessionMetricRow(
                        label        = "Late errors",
                        prevText     = "${stats.lateErrPreviousMs.roundToInt()} ms",
                        currText     = "${stats.lateErrCurrentMs.roundToInt()} ms",
                        improved     = stats.lateErrCurrentMs < stats.lateErrPreviousMs && stats.lateErrPreviousMs > 0,
                        worsened     = stats.lateErrCurrentMs > stats.lateErrPreviousMs && stats.lateErrPreviousMs > 0,
                        higherBetter = false
                    )
                    SessionMetricRow(
                        label        = "Hit rate",
                        prevText     = "${stats.hitRatePrevious}%",
                        currText     = "${stats.hitRateCurrent}%",
                        improved     = stats.hitRateCurrent > stats.hitRatePrevious,
                        worsened     = stats.hitRateCurrent < stats.hitRatePrevious,
                        higherBetter = true
                    )
                } else {
                    SingleMetricRow("SCM pace",    "%.2f c/att".format(stats.scmPaceCurrent))
                    SingleMetricRow("Early errors","${stats.earlyErrCurrentMs.roundToInt()} ms")
                    SingleMetricRow("Late errors", "${stats.lateErrCurrentMs.roundToInt()} ms")
                    SingleMetricRow("Hit rate",    "${stats.hitRateCurrent}%")
                }
            }

            // ── Best trio ──────────────────────────────────────────────
            if (stats.bestTrio.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    BasicText(
                        text  = "Best trio:",
                        style = TextStyle(fontSize = 11.sp, color = BubbleText.copy(alpha = 0.6f))
                    )
                    BasicText(
                        text     = stats.bestTrio.joinToString("  ·  "),
                        modifier = Modifier.fillMaxWidth(),
                        style    = TextStyle(
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = BubbleText,
                            textAlign  = TextAlign.Center
                        )
                    )
                }
            }

            // ── Personal record ────────────────────────────────────────
            if (stats.isPersonalRecord) {
                BasicText(
                    text     = "🏆 New personal record: ${stats.personalRecordMs} ms!",
                    modifier = Modifier.fillMaxWidth(),
                    style    = TextStyle(
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = BubbleText,
                        textAlign  = TextAlign.Center
                    )
                )
            }

            BasicText(
                text     = "Tap anywhere to dismiss",
                modifier = Modifier.fillMaxWidth(),
                style    = TextStyle(
                    fontSize  = 11.sp,
                    color     = BubbleText.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun SessionMetricRow(
    label: String,
    prevText: String,
    currText: String,
    improved: Boolean,
    worsened: Boolean,
    higherBetter: Boolean
) {
    val arrowColor = when {
        improved -> resultGreenColor
        worsened -> resultRedColor
        else     -> BubbleText.copy(alpha = 0.45f)
    }
    val arrow = when {
        improved && higherBetter  -> "↑"
        improved                  -> "↓"
        worsened && !higherBetter -> "↑"
        worsened                  -> "↓"
        else                      -> "→"
    }
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        BasicText(label,
            style = TextStyle(fontSize = 12.sp, color = BubbleText.copy(alpha = 0.7f)))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicText(prevText,
                style = TextStyle(fontSize = 12.sp, color = BubbleText))
            BasicText(arrow,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = arrowColor))
            BasicText(currText,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = arrowColor))
        }
    }
}

@Composable
private fun SingleMetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label,
            style = TextStyle(fontSize = 12.sp, color = BubbleText.copy(alpha = 0.7f)))
        BasicText(value,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BubbleText))
    }
}
