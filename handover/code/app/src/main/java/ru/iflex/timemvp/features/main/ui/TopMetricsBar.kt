package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import ru.iflex.timemvp.core.utils.Hand
import ru.iflex.timemvp.features.main.domain.BalanceSide
import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.fullShape
import ru.iflex.timemvp.ui_theme.greyBackgroundColor
import ru.iflex.timemvp.ui_theme.largeShape
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor
import kotlin.math.cos
import kotlin.math.sin

private fun formatCoinsTotal(value: Double): String = when {
    value < 10.0   -> "%.2f".format(value)
    value < 100.0  -> "%.1f".format(value)
    value < 1000.0 -> "%.0f".format(value)
    else           -> { val v = value.toLong(); "${v / 1000} ${String.format("%03d", v % 1000)}" }
}

/**
 * Верхняя строка метрик:
 *  [Balance gauge]   [True three hit rate]   …spacer…   [Serenity coins]
 */
@Composable
fun TopMetricsBar(
    redCount: Int,
    blueCount: Int,
    balanceSide: BalanceSide,
    totalSerenityBalance: Double,
    todaySerenityDelta: Double,
    trueThreeCount: Int,
    totalAttempts: Int,
    selectedHand: Hand = Hand.RIGHT,
    onHandSelected: (Hand) -> Unit = {},
    modifier: Modifier = Modifier,
    // Тап-зоны интерактивной справки: MainScreen передаёт фабрику модификаторов,
    // регистрирующих Rect каждого элемента. По умолчанию — no-op.
    helpZone: (HelpTopic) -> Modifier = { Modifier }
) {
    val labelStyle = TextStyle(
        fontSize   = 6.sp,
        fontWeight = FontWeight.Medium,
        color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.75f),
    )
    val valueStyle = TextStyle(
        fontSize   = 10.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.baseTextColor
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // ── Left: Balance + True Three ────────────────────────────────────
        Row(
            modifier          = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = helpZone(HelpTopic.BALANCE)
            ) {
                BasicText("Balance", style = labelStyle)
                Spacer(Modifier.height(1.dp))
                BalanceGauge(
                    redCount  = redCount,
                    blueCount = blueCount,
                    modifier  = Modifier.size(width = 34.dp, height = 17.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = helpZone(HelpTopic.HIT_RATE)
            ) {
                BasicText("True three hit rate", style = labelStyle)
                val rateText = if (totalAttempts == 0) "—"
                               else "%.2f".format(trueThreeCount.toDouble() / totalAttempts)
                BasicText(
                    text  = rateText,
                    style = valueStyle.copy(
                        fontSize = 13.sp,
                        color    = if (trueThreeCount > 0) resultGreenColor
                                   else MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.38f)
                    )
                )
            }
        }

        // ── Center: Hand toggle ───────────────────────────────────────────
        CompactHandToggle(
            selectedHand   = selectedHand,
            onHandSelected = onHandSelected,
            modifier       = helpZone(HelpTopic.HAND)
        )

        // ── Right: Serenity Coins ─────────────────────────────────────────
        Box(
            modifier        = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = helpZone(HelpTopic.COINS)
            ) {
                BasicText(
                    text  = "Serenity coins",
                    style = labelStyle.copy(textAlign = TextAlign.End)
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    BasicText(
                        text  = formatCoinsTotal(totalSerenityBalance),
                        style = valueStyle
                    )
                    CoinStack(modifier = Modifier.size(width = 27.dp, height = 9.dp))
                    if (todaySerenityDelta != 0.0) {
                        val sign = if (todaySerenityDelta > 0) "+" else ""
                        BasicText(
                            text  = "$sign${"%.2f".format(todaySerenityDelta)}",
                            style = TextStyle(
                                fontSize = 9.sp,
                                color    = if (todaySerenityDelta >= 0)
                                               resultGreenColor.copy(alpha = 0.80f)
                                           else resultRedColor.copy(alpha = 0.80f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compact inline hand toggle:  L  [●]  R
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CompactHandToggle(
    selectedHand: Hand,
    onHandSelected: (Hand) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackW   = 28.dp
    val trackH   = 14.dp
    val thumbSz  = 10.dp
    val thumbPad = 2.dp

    val thumbOffsetDp = if (selectedHand == Hand.RIGHT) trackW - thumbSz - thumbPad * 2 else 0.dp

    val labelStyle = TextStyle(
        fontSize   = 10.sp,
        fontWeight = FontWeight.Normal,
        color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.38f)
    )
    val activeStyle = labelStyle.copy(
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.accentColor
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text  = "hand",
            style = TextStyle(
                fontSize = 6.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.75f)
            )
        )
        Spacer(Modifier.height(1.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicText(
                text     = "L",
                style    = if (selectedHand == Hand.LEFT) activeStyle else labelStyle,
                modifier = Modifier.clickable { onHandSelected(Hand.LEFT) }
            )
            Box(
                modifier = Modifier
                    .width(trackW)
                    .height(trackH)
                    .clip(largeShape)
                    .background(MaterialTheme.colorScheme.greyBackgroundColor)
                    .clickable { onHandSelected(if (selectedHand == Hand.LEFT) Hand.RIGHT else Hand.LEFT) }
            ) {
                Box(
                    modifier = Modifier
                        .padding(thumbPad)
                        .offset(x = thumbOffsetDp)
                        .size(thumbSz)
                        .clip(fullShape)
                        .background(MaterialTheme.colorScheme.accentColor)
                )
            }
            BasicText(
                text     = "R",
                style    = if (selectedHand == Hand.RIGHT) activeStyle else labelStyle,
                modifier = Modifier.clickable { onHandSelected(Hand.RIGHT) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Balance gauge: semicircle arc, red left / blue right, white needle
// Canvas: width=72dp, height=36dp (= radius).
// Arc circle center sits at bottom-center of the canvas (outside visible area).
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BalanceGauge(
    redCount: Int,
    blueCount: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeW    = 4.dp.toPx()
        val inset      = strokeW / 2f
        val arcTopLeft = Offset(inset, inset)
        val arcSize    = Size(size.width - strokeW, size.width - strokeW)

        // Left arc: red  (180° → 270°, clockwise = top-left quarter)
        drawArc(
            color      = resultRedColor.copy(alpha = 0.85f),
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter  = false,
            topLeft    = arcTopLeft,
            size       = arcSize,
            style      = Stroke(strokeW, cap = StrokeCap.Round)
        )
        // Right arc: blue (270° → 360°, clockwise = top-right quarter)
        drawArc(
            color      = resultBlueColor.copy(alpha = 0.85f),
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter  = false,
            topLeft    = arcTopLeft,
            size       = arcSize,
            style      = Stroke(strokeW, cap = StrokeCap.Round)
        )

        // Needle position: 270° = vertical up (balanced), ±80° max range
        val total  = (redCount + blueCount).toFloat()
        val ratio  = if (total == 0f) 0f
                     else ((blueCount - redCount) / total).coerceIn(-1f, 1f)
        val angleDeg   = 270f + ratio * 80f
        val angleRad   = Math.toRadians(angleDeg.toDouble())
        val effectiveR = (size.width - strokeW) / 2f
        val cx = size.width  / 2f
        val cy = size.height                          // arc circle center = canvas bottom
        val endX = cx + (effectiveR * cos(angleRad)).toFloat()
        val endY = cy + (effectiveR * sin(angleRad)).toFloat()

        // Needle line
        drawLine(
            color       = Color.White,
            start       = Offset(cx, cy),
            end         = Offset(endX, endY),
            strokeWidth = 1.5.dp.toPx(),
            cap         = StrokeCap.Round
        )
        // Pivot dot
        drawCircle(Color.White.copy(alpha = 0.90f), 2.dp.toPx(), Offset(cx, cy))
        // Tip dot — green if balanced, dominant color otherwise
        val tipColor = when {
            ratio < -0.15f -> resultRedColor
            ratio >  0.15f -> resultBlueColor
            else           -> resultGreenColor
        }
        drawCircle(tipColor, 2.8.dp.toPx(), Offset(endX, endY))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Coin stack: a row of overlapping circles (last one filled = most recent)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CoinStack(modifier: Modifier = Modifier) {
    val dimColor    = Color.White.copy(alpha = 0.22f)
    val brightColor = Color.White.copy(alpha = 0.62f)
    Canvas(modifier = modifier) {
        val total = 7
        val coinR = size.height / 2f
        val step  = if (total > 1) (size.width - coinR * 2f) / (total - 1) else 0f
        val cy    = size.height / 2f
        repeat(total) { i ->
            val cx = coinR + i * step
            if (i < total - 1) {
                drawCircle(dimColor, coinR, Offset(cx, cy), style = Stroke(1.5f))
            } else {
                drawCircle(brightColor, coinR, Offset(cx, cy))
            }
        }
    }
}
