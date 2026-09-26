package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultRedColor

/**
 * Строка "Geometric Mean" — позиционированные маркеры на шкале.
 *
 * Шкала та же что у DistribChart: ±scaleMaxMs.
 * Early (красный) — геом. среднее ранних ошибок, откладывается влево от 0.
 * Absolute (белый) — геом. среднее |ошибок|, откладывается вправо от 0.
 * Late (синий) — геом. среднее поздних ошибок, откладывается вправо от 0.
 *
 * Подписи Early / Absolute / Late вынесены в отдельную строку под канвасом
 * (слева / по центру / справа), чтобы не перекрывались при близких пиках.
 */
@Composable
fun GeomMeanRow(
    earlyMs: Double,
    absoluteMs: Double,
    lateMs: Double,
    scaleMaxMs: Double = 200.0,
    accuracy: Accuracy = Accuracy.DECISECONDS,
    modifier: Modifier = Modifier
) {
    val scaleMax     = scaleMaxMs.coerceAtLeast(1.0)
    val axisColor    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.2f)
    val neutralColor = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.90f)

    val sublabelStyle = TextStyle(
        fontSize   = 9.sp,
        fontWeight = FontWeight.Normal,
        color      = neutralColor.copy(alpha = 0.5f)
    )

    fun fmtMs(ms: Double, prefix: String): String = when (accuracy) {
        Accuracy.SECONDS      -> "$prefix%.0f".format(ms / 1000.0)
        Accuracy.DECISECONDS  -> "$prefix%.1f".format(ms / 1000.0)
        Accuracy.CENTISECONDS -> "$prefix%.2f".format(ms / 1000.0)
        Accuracy.MILLISECONDS -> "$prefix%.3f".format(ms / 1000.0)
    }

    data class Marker(val signedMs: Double, val text: String, val color: Color, val label: String)

    val markers = buildList {
        if (earlyMs    > 0.0)  add(Marker(-earlyMs,   fmtMs(earlyMs, "−"), resultRedColor, "Early"))
        if (absoluteMs != 0.0) add(Marker(absoluteMs, fmtMs(kotlin.math.abs(absoluteMs),
                                              if (absoluteMs >= 0) "+" else "−"), neutralColor, "Absolute"))
        if (lateMs     > 0.0)  add(Marker(lateMs,     fmtMs(lateMs, "+"), resultBlueColor, "Late"))
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {

        // ── Шкала с пиками ────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(CHART_LABEL_WIDTH))

            androidx.compose.foundation.Canvas(
                modifier = Modifier.weight(1f).padding(end = CHART_SC_PAD).height(20.dp)
            ) {
                val w      = size.width
                val axisY  = 12.dp.toPx()
                val triH   = 8.dp.toPx()
                val triW   = 7.dp.toPx()
                val dashFx = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)

                val positions = markers.map { marker ->
                    val xFrac = ((marker.signedMs / scaleMax + 1.0) / 2.0).toFloat()
                        .coerceIn(0.02f, 0.98f)
                    marker to (w * xFrac)
                }

                // Background zones
                val earlyX = positions.find { it.first.label == "Early" }?.second
                val lateX  = positions.find { it.first.label == "Late"  }?.second
                if (earlyX != null)
                    drawRect(resultRedColor.copy(alpha = 0.07f),
                        topLeft = Offset(0f, 0f), size = Size(earlyX, size.height))
                if (lateX != null)
                    drawRect(resultBlueColor.copy(alpha = 0.07f),
                        topLeft = Offset(lateX, 0f), size = Size(size.width - lateX, size.height))

                // Axis + centre line
                drawLine(axisColor, Offset(0f, axisY), Offset(w, axisY), 1.dp.toPx())
                drawLine(axisColor.copy(alpha = 0.55f),
                    Offset(w * 0.5f, 0f), Offset(w * 0.5f, axisY), 1.dp.toPx())

                // Triangles ▲ + dashed lines upward
                positions.forEach { (marker, cx) ->
                    val tipY = axisY - triH
                    drawLine(
                        color       = marker.color.copy(alpha = 0.9f),
                        start       = Offset(cx, tipY),
                        end         = Offset(cx, 0f),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect  = dashFx
                    )
                    val triPath = Path().apply {
                        moveTo(cx, tipY)
                        lineTo(cx - triW / 2f, axisY)
                        lineTo(cx + triW / 2f, axisY)
                        close()
                    }
                    drawPath(triPath, marker.color)
                }
            }
        }

        // ── Подписи: "Geom. Mean" + Early | Absolute | Late ─────────────
        val earlyMarker = markers.find { it.label == "Early" }
        val absMarker   = markers.find { it.label == "Absolute" }
        val lateMarker  = markers.find { it.label == "Late" }

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = CHART_SC_PAD),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text  = "Geom. Mean",
                style = TextStyle(
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.63f)
                ),
                modifier = Modifier.width(CHART_LABEL_WIDTH)
            )
            // Внутренний ряд: используем SpaceAround вместо SpaceBetween,
            // чтобы отступ от "Geom. Mean" до Early был не меньше, чем
            // между Early/Absolute/Late (SpaceBetween прижимал Early к лейблу).
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Подписи Early / Absolute / Late убраны (Alex 2026-07-26) — цвет
                // значения уже кодирует сторону (красный/белый/синий).
                BasicText(
                    text     = earlyMarker?.text ?: "—",
                    style    = TextStyle(
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color      = if (earlyMarker != null) resultRedColor
                                     else neutralColor.copy(alpha = 0.3f)
                    )
                )
                BasicText(
                    text     = absMarker?.text ?: "—",
                    style    = TextStyle(
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color      = if (absMarker != null) neutralColor
                                     else neutralColor.copy(alpha = 0.3f)
                    )
                )
                BasicText(
                    text     = lateMarker?.text ?: "—",
                    style    = TextStyle(
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color      = if (lateMarker != null) resultBlueColor
                                     else neutralColor.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
