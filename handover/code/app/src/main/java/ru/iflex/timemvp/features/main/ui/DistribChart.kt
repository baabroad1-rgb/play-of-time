package ru.iflex.timemvp.features.main.ui

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.features.main.domain.DistribBucket
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor

/**
 * Ширина левого лейбла — совпадает с левой частью SessionStatisticItem:
 *   number(20dp) + time(44dp) = 64dp
 */
internal val CHART_LABEL_WIDTH = 64.dp

/**
 * Ширина правого отступа — совпадает с колонкой SC в SessionStatisticItem (32dp),
 * чтобы "0" гистограммы и "0" баров попыток приходились на одну вертикаль.
 */
internal val CHART_SC_PAD = 32.dp

/**
 * Гистограмма частот ошибок.
 * Левый лейбл "Distrib." + гистограмма с осью в секундах.
 */
@Composable
fun DistribChart(
    buckets: List<DistribBucket>,
    scaleMaxMs: Double,
    earlyMs: Double      = 0.0,
    lateMs: Double       = 0.0,
    absoluteMs: Double   = 0.0,
    accuracy: Accuracy   = Accuracy.DECISECONDS,
    modifier: Modifier   = Modifier,
    /**
     * «Капля» в полёте (Alex 2026-07-15): вклад свежей попытки с этой ошибкой
     * (мс, signed) временно СКРЫТ — из содержащего бакета вычитается 1.
     * Когда капля приземляется (параметр становится null), столбик подрастает —
     * визуально капля «вливается» в гистограмму.
     */
    suppressErrorMs: Double? = null
) {
    if (buckets.isEmpty()) return

    // Подавление вклада летящей капли — до расчёта maxCount, чтобы столбики
    // не прыгали по вертикальной шкале в момент приземления без необходимости.
    val effBuckets = if (suppressErrorMs == null) buckets else {
        val idx = buckets.indexOfFirst {
            suppressErrorMs >= it.startMs && suppressErrorMs < it.endMs
        }.takeIf { it >= 0 }
            ?: buckets.indices.minByOrNull {
                kotlin.math.abs(buckets[it].centerMs - suppressErrorMs)
            }
        if (idx == null) buckets
        else buckets.mapIndexed { i, b ->
            if (i == idx) b.copy(count = (b.count - 1).coerceAtLeast(0)) else b
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor   = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.45f)
    val axisColor    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.2f)

    val labelStyle = TextStyle(
        fontSize   = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color      = labelColor
    )

    // Fixed-width symmetric buckets — height = raw count, floored at 5 so a
    // lone attempt doesn't slam to full canvas height.
    val maxCount = maxOf(effBuckets.maxOfOrNull { it.count } ?: 1, 5)
    val scaleMax = scaleMaxMs.coerceAtLeast(1.0)

    val countLabelStyle = TextStyle(
        fontSize   = 7.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.85f)
    )
    val sideAxisStyle = TextStyle(
        fontSize   = 7.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.45f)
    )

    /** Форматирует мс → секунды с нужной точностью уровня */
    fun fmt(ms: Double): String {
        val pattern = when (accuracy) {
            Accuracy.SECONDS      -> if (ms == 0.0) "0" else (if (ms > 0) "+" else "") + "%.0f".format(ms / 1000.0)
            Accuracy.DECISECONDS  -> if (ms == 0.0) "0" else (if (ms > 0) "+" else "") + "%.1f".format(ms / 1000.0)
            Accuracy.CENTISECONDS -> if (ms == 0.0) "0" else (if (ms > 0) "+" else "") + "%.2f".format(ms / 1000.0)
            Accuracy.MILLISECONDS -> if (ms == 0.0) "0" else (if (ms > 0) "+" else "") + "%.3f".format(ms / 1000.0)
        }
        return pattern
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // ── Гистограмма (без лейбла — он переехал вниз к оси) ────
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CHART_LABEL_WIDTH))
            Column(modifier = Modifier.weight(1f).padding(end = CHART_SC_PAD)) {

        // Гистограмма с подписями: счётчик над столбиком, ось Y по обоим бокам.
        // Высота 48dp (Alex попросил уменьшить область гистограммы ~на 1/4 против
        // прежних 64dp) — счётчики и Y-тики продолжают помещаться при topPad=6dp.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
                val w       = size.width
                val h       = size.height
                // Отступ снизу для оси Y подписей не нужен — они идут на «сторонах»;
                // отступ сверху ~6dp оставляет место для count-меток над столбиками
                // (при высоте канваса 48dp остаётся ~40dp на бары).
                val topPad   = 6.dp.toPx()
                val barAreaH = h - 2.dp.toPx() - topPad
                val axisY   = topPad + barAreaH
                val bucketW = w / effBuckets.size.toFloat()
                val cornerR = CornerRadius(1.5.dp.toPx())
                val dashFx  = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)

                // Позиции линий геом.средних
                val xEarlyFrac = if (earlyMs > 0.0)
                    ((-earlyMs / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) else -1f
                val xLateFrac  = if (lateMs  > 0.0)
                    ((lateMs   / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) else -1f
                val xAbsFrac   = if (absoluteMs != 0.0)
                    ((absoluteMs / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) else -1f

                // Закрашивание зон (под барами, рисуем сначала)
                if (xEarlyFrac >= 0f)
                    drawRect(resultRedColor.copy(alpha = 0.07f),
                        topLeft = Offset(0f, topPad), size = Size(w * xEarlyFrac, barAreaH))
                if (xLateFrac >= 0f)
                    drawRect(resultBlueColor.copy(alpha = 0.07f),
                        topLeft = Offset(w * xLateFrac, topPad), size = Size(w - w * xLateFrac, barAreaH))

                // Горизонтальная ось
                drawLine(axisColor, Offset(0f, axisY), Offset(w, axisY), 1.dp.toPx())

                // Центральная линия «0» убрана: она рисовалась на всю высоту графика и
                // визуально "продолжала" зелёный столбик true-three выше его реальной
                // высоты. Столбик 0 теперь строго зелёный и ровно по количеству попаданий.

                // ── Y-ось: целочисленные тики, каждый ровно на своей высоте ──
                // Выбираем «красивый» шаг (1/2/5/10/20/50/…), даёт 2–5 видимых линий.
                val niceStep = run {
                    val target = maxCount.toDouble() / 4.0
                    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(target.coerceAtLeast(1.0))))
                    val norm = target / mag
                    val s: Double = if (norm <= 1.5) 1.0
                                    else if (norm <= 3.5) 2.0
                                    else if (norm <= 7.5) 5.0
                                    else 10.0
                    (s * mag).toInt().coerceAtLeast(1)
                }
                var tickVal = niceStep
                while (tickVal <= maxCount) {
                    val yy = axisY - (tickVal.toFloat() / maxCount.toFloat()) * barAreaH
                    val txt = textMeasurer.measure(tickVal.toString(), sideAxisStyle)
                    drawText(txt, topLeft = Offset(0f, yy - txt.size.height / 2f))
                    drawText(txt, topLeft = Offset(w - txt.size.width, yy - txt.size.height / 2f))
                    drawLine(
                        color = axisColor.copy(alpha = 0.25f),
                        start = Offset(txt.size.width + 2.dp.toPx(), yy),
                        end   = Offset(w - txt.size.width - 2.dp.toPx(), yy),
                        strokeWidth = 0.5.dp.toPx()
                    )
                    tickVal += niceStep
                }

                // Цветные пунктиры на позициях геом.средних
                if (xEarlyFrac >= 0f)
                    drawLine(
                        color       = resultRedColor.copy(alpha = 0.9f),
                        start       = Offset(w * xEarlyFrac, 0f),
                        end         = Offset(w * xEarlyFrac, axisY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = dashFx
                    )
                if (xLateFrac >= 0f)
                    drawLine(
                        color       = resultBlueColor.copy(alpha = 0.9f),
                        start       = Offset(w * xLateFrac, 0f),
                        end         = Offset(w * xLateFrac, axisY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = dashFx
                    )
                // Белая пунктирная линия общего среднего (Absolute)
                if (xAbsFrac >= 0f)
                    drawLine(
                        color       = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                        start       = Offset(w * xAbsFrac, 0f),
                        end         = Offset(w * xAbsFrac, axisY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = dashFx
                    )

                // Столбики — позиция по centerMs относительно текущего scaleMax
                // (бакеты за пределами scaleMax не рисуем, чтобы не «уезжали» за оси)
                // Bars fill the entire bucket slot (no gap), so adjacent buckets look
                // like a continuous skyline. Floor at 1.5 dp keeps them visible if the
                // canvas is unusually narrow.
                // Fixed-width symmetric bars centred on each bucket. The drawn
                // bar takes only HALF the bucket slot width — the other half is
                // empty space, so bars and gaps alternate at equal width.
                // Каждое уникальное значение ошибки = РОВНО один столбик.
                // Ширина адаптивна: ≤ 3dp когда мест мало, до ½px-минимума
                // когда столбиков очень много (выбросы). Так столбики не
                // перекрывают друг друга и сумма видна корректно.
                val slotW = w / effBuckets.size.toFloat()
                val barW = (slotW * 0.6f).coerceIn(0.5.dp.toPx(), 3.dp.toPx())
                effBuckets.forEach { bucket ->
                    if (bucket.count == 0) return@forEach
                    if (bucket.endMs < -scaleMax || bucket.startMs > scaleMax) return@forEach
                    val barColor = when {
                        bucket.winsCount > 0 -> resultGreenColor
                        bucket.centerMs < 0.0 -> resultRedColor
                        else -> resultBlueColor
                    }
                    val barH = (bucket.count.toFloat() / maxCount.toFloat()) * barAreaH
                    val xCenterFrac = ((bucket.centerMs / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
                    val cx = w * xCenterFrac
                    val barX = (cx - barW / 2f).coerceIn(0f, w - barW)
                    drawRoundRect(
                        color        = barColor.copy(alpha = 0.9f),
                        topLeft      = Offset(barX, axisY - barH),
                        size         = Size(barW, barH),
                        cornerRadius = cornerR
                    )
                }
            }

            }  // end Column (histogram)
        }  // end histogram Row

        // ── Ось: лейбл "Distrib." + подписи значений ─────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = "Distrib.",
                modifier = Modifier.width(CHART_LABEL_WIDTH),
                style = TextStyle(
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.55f)
                )
            )
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = CHART_SC_PAD)
                    .height(10.dp)
            ) {
                val w = size.width
                listOf(
                    0.00f to fmt(-scaleMax),
                    0.25f to fmt(-scaleMax / 2),
                    0.50f to fmt(0.0),
                    0.75f to fmt(scaleMax / 2),
                    1.00f to fmt(scaleMax)
                ).forEach { (xFrac, text) ->
                    val ml = textMeasurer.measure(text, labelStyle)
                    val x  = (w * xFrac - ml.size.width / 2f).coerceIn(0f, w - ml.size.width.toFloat())
                    drawText(ml, topLeft = Offset(x, 0f))
                }
            }
        }
    }  // end outer Column
}
