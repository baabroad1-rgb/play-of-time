package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.ui_theme.baseBackgroundColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor
import ru.iflex.timemvp.ui_theme.statisticTextStyle
import kotlin.math.abs

/** One of 12 freehand-marker form-factors for the twin-shot outline.
 *  Picked deterministically by seed (attempt.startTimestamp) per twin event. */
private data class MarkerConfig(
    val tiltDeg: Float,
    val rxFactor: Float,
    val ryFactor: Float,
    val startAtBottom: Boolean,
    val doubleLoop: Boolean
)

@Composable
private fun SessionHeaderRow(label: String, dateLabel: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Дата — только у самого верхнего заголовка, прижата к левому краю.
        // Остальная часть заголовка не меняется.
        if (dateLabel.isNotEmpty()) {
            BasicText(
                text = "$dateLabel  ",
                style = statisticTextStyle.copy(
                    fontSize = 9.sp,
                    color    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.35f)
                )
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.15f))
        )
        BasicText(
            text = "  $label  ",
            style = statisticTextStyle.copy(
                fontSize  = 9.sp,
                color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.35f),
                textAlign = TextAlign.Center
            )
        )
        Box(
            Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.15f))
        )
    }
}

@Composable
fun SessionStatisticItem(
    state: SessionStatisticItemState,
    scaleMaxMs: Double = 200.0,
    /** Геом. среднее ранних ошибок (мс, > 0) — рисуется красная пунктирная линия */
    earlyMeanMs: Double = 0.0,
    /** Геом. среднее поздних ошибок (мс, > 0) — рисуется синяя пунктирная линия */
    lateMeanMs: Double = 0.0,
    /** Общее среднее смещение (мс, signed) — рисуется белая пунктирная линия */
    absoluteMeanMs: Double = 0.0,
    accuracy: Accuracy = Accuracy.CENTISECONDS,
    /** false for the last row in the list */
    showScm: Boolean = true,
    /** true for the first row — renders "SC" column header instead of a value */
    isScmHeader: Boolean = false,
    /** >0 — эта строка ВЕРХНЯЯ в twin-группе; число строк (включая себя) в группе.
     *  Только верхняя строка рисует общий маркерный овал, обводящий все строки группы.
     *  0 — не рисовать овал (либо не twin, либо это не верхняя строка группы). */
    twinGroupHeight: Int = 0
) {
    val textMeasurer = rememberTextMeasurer()

    if (state.isSessionHeader) {
        SessionHeaderRow(label = state.sessionLabel, dateLabel = state.sessionDateLabel)
        return
    }

    val errorMs   = state.error * 1000.0          // signed, ms
    val isTrueThree = state.isWin                 // победа с учётом допуска T±t
    val isEarly     = state.error < 0.0

    val barColor = when {
        isTrueThree -> resultGreenColor
        isEarly     -> resultRedColor
        else        -> resultBlueColor
    }

    // Цвет времени — совпадает с цветом бара
    val timeColor = barColor

    // Формат ошибки: секунды без ведущего нуля, знак "-" для early
    // 0.04 → ".04",  0.21 → ".21",  early 0.21 → "-.21"
    val errorText: String = if (!isTrueThree) {
        val absVal = abs(state.error)                          // секунды
        val fmt    = when (accuracy) {
            Accuracy.SECONDS      -> "%.0f"
            Accuracy.DECISECONDS  -> "%.1f"
            Accuracy.CENTISECONDS -> "%.2f"
            Accuracy.MILLISECONDS -> "%.3f"
        }
        val raw = fmt.format(absVal).removePrefix("0")         // e.g. ".091"
        if (isEarly) "-$raw" else raw
    } else ""

    val valueStyle = TextStyle(
        fontSize   = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color      = barColor
    )
    val measuredText = if (errorText.isNotEmpty())
        textMeasurer.measure(errorText, valueStyle) else null

    // Цвета для Canvas (захвачены до входа в лямбду)
    val gridLineColor = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.10f)
    val bgColor       = MaterialTheme.colorScheme.baseBackgroundColor

    val twinShot = state.twinShot
    // Симметричный отступ контура от текста: одинаковый зазор слева от номера
    // и справа от значения времени. Измеряем фактическую ширину текста "X.XXX",
    // чтобы правая грань шла сразу после последней цифры значения.
    val timeTextStyle = statisticTextStyle.copy(
        fontSize  = 11.sp,
        color     = timeColor,
        textAlign = TextAlign.Start
    )
    // Include twinShot in the remember key — otherwise the first composition caches 0
    // (when twinShot was still false) and the outline's right edge stays at ~20 dp
    // instead of moving past the time text.
    val timeTextWidthPx = remember(state.time, twinShot) {
        if (twinShot && state.time.isNotEmpty())
            textMeasurer.measure(state.time, timeTextStyle).size.width
        else 0
    }
    val rowModifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp)
        .drawBehind {
            if (twinGroupHeight <= 0) return@drawBehind
            // Freehand «marker oval» — обводит # и time-колонки в группе из twinGroupHeight строк.
            // Сид — startTimestamp; одна из 12 заготовленных форм-конфигов выбирается по seed,
            // плюс мелкий jitter по контрольным точкам делает каждую пару уникальной.
            val gap     = 5.dp.toPx()
            val rectLeft   = -gap
            val rectRight  = 20.dp.toPx() + timeTextWidthPx + gap
            val rectTop    = -gap
            val rectBottom = size.height * twinGroupHeight + gap
            val cx = (rectLeft + rectRight) / 2f
            val cy = (rectTop  + rectBottom) / 2f
            val rxBase = (rectRight - rectLeft) / 2f
            val ryBase = (rectBottom - rectTop) / 2f

            val seed = state.startTimestamp.takeIf { it != 0L }
                ?: state.number * 7919L
            val random = java.util.Random(seed)

            // 12 form-configs — picked deterministically by seed:
            //   tilt (deg) · rxFactor · ryFactor · bottomStart · doubleLoop
            val configs = listOf(
                MarkerConfig(-12f, 1.00f, 1.00f, false, false),   // 1 left tilt
                MarkerConfig(  0f, 1.00f, 1.00f, false, true ),   // 2 1.5-wrap loop
                MarkerConfig(  0f, 1.00f, 1.00f, true,  false),   // 3 bottom-start
                MarkerConfig(  0f, 1.00f, 0.92f, false, false),   // 4 egg (taller top)
                MarkerConfig(-10f, 1.00f, 0.92f, false, false),   // 5 egg, left tilt
                MarkerConfig(  9f, 1.00f, 0.92f, false, false),   // 6 egg, right tilt
                MarkerConfig(-10f, 1.00f, 1.00f, true,  false),   // 7 bottom-start, left tilt
                MarkerConfig(  0f, 1.00f, 1.00f, true,  true ),   // 8 bottom-start + 1.5 wrap
                MarkerConfig(-13f, 1.00f, 1.00f, false, true ),   // 9 1.5 wrap, strong tilt
                MarkerConfig(  0f, 1.00f, 0.88f, false, false),   // 10 inverted egg
                MarkerConfig(  0f, 0.92f, 1.00f, false, false),   // 11 egg narrow right
                MarkerConfig( 10f, 1.00f, 1.00f, true,  false)    // 12 bottom-start, right tilt
            )
            val cfg = configs[(seed.toULong() % configs.size.toULong()).toInt()]

            val rx = rxBase * cfg.rxFactor
            val ry = ryBase * cfg.ryFactor
            val tiltRad = Math.toRadians(cfg.tiltDeg.toDouble())

            val nPoints   = if (cfg.doubleLoop) 22 else 14
            val totalArc  = if (cfg.doubleLoop) (Math.PI * 2 * 1.4) else (Math.PI * 2)
            // Start angle: from the top (with small offset) for normal, from the bottom for bottom-start.
            val baseStart = if (cfg.startAtBottom) Math.PI / 2.0 + 0.3 else -Math.PI / 2.0 + 0.3
            val cosT = Math.cos(tiltRad)
            val sinT = Math.sin(tiltRad)

            val pts = (0..nPoints).map { i ->
                val baseAngle   = baseStart + (i.toDouble() / nPoints) * totalArc
                val jitterAngle = baseAngle + (random.nextDouble() - 0.5) * 0.10
                // Double-loop: inner radius shrinks during the extra wrap.
                val radiusFactor = if (cfg.doubleLoop)
                    1.0 - (i.toDouble() / nPoints) * 0.35
                else 1.0
                val rxJ = rx * radiusFactor * (1.0 + (random.nextDouble() - 0.5) * 0.10)
                val ryJ = ry * radiusFactor * (1.0 + (random.nextDouble() - 0.5) * 0.10)
                val xLocal = rxJ * Math.cos(jitterAngle)
                val yLocal = ryJ * Math.sin(jitterAngle)
                // Rotate by tiltRad around (cx,cy).
                val xRot = xLocal * cosT - yLocal * sinT
                val yRot = xLocal * sinT + yLocal * cosT
                Offset(x = (cx + xRot).toFloat(), y = (cy + yRot).toFloat())
            }
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 0 until pts.size - 1) {
                    val a = pts[i]; val b = pts[i + 1]
                    val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    quadraticBezierTo(a.x, a.y, mid.x, mid.y)
                }
                lineTo(pts.last().x, pts.last().y)
                // Tail overshoot direction depends on start (top vs bottom).
                val tailDx = ((random.nextDouble() - 0.5) * 14.0).toFloat()
                val tailDy = if (cfg.startAtBottom) (gap + 2f) else (-gap - 2f)
                lineTo(pts.last().x + tailDx, pts.last().y + tailDy)
            }
            drawPath(
                path  = path,
                color = resultGreenColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.2.dp.toPx(),
                    cap   = androidx.compose.ui.graphics.StrokeCap.Round,
                    join  = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Номер попытки ─────────────────────────────────────────
        BasicText(
            modifier = Modifier.width(20.dp),
            text  = state.number.toString(),
            style = statisticTextStyle.copy(
                fontSize   = 12.sp,
                fontWeight = FontWeight.Normal,
                color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.45f),
                textAlign  = TextAlign.Start
            )
        )

        // ── Выбитое время — цвет по типу ошибки ──────────────────
        BasicText(
            modifier = Modifier.width(44.dp),
            text  = state.time,
            style = statisticTextStyle.copy(
                fontSize  = 11.sp,
                color     = timeColor,
                textAlign = TextAlign.Start
            )
        )

        // ── Бар ошибки ────────────────────────────────────────────
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
        ) {
            val w        = size.width
            val cx       = w / 2f
            val cy       = size.height / 2f
            val scaleMax = scaleMaxMs.coerceAtLeast(1.0)
            val fraction = (abs(errorMs) / scaleMax).coerceIn(0.0, 1.0).toFloat()
            val barLen   = cx * fraction
            val barH     = 2.5.dp.toPx()
            val dotR     = 4.5.dp.toPx()
            val textGap  = 3.dp.toPx()
            val dashFx   = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)

            // ── Зоны и пунктирные линии геом. средних ────────────
            val xEarlyFrac = if (earlyMeanMs > 0.0)
                ((-earlyMeanMs / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) else -1f
            val xLateFrac  = if (lateMeanMs  > 0.0)
                ((lateMeanMs  / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)  else -1f
            val xAbsFrac   = if (absoluteMeanMs != 0.0)
                ((absoluteMeanMs / scaleMax + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) else -1f

            // Закрашивание зон (рисуем первыми — под всем остальным)
            if (xEarlyFrac >= 0f)
                drawRect(resultRedColor.copy(alpha = 0.07f),
                    topLeft = Offset(0f, 0f), size = Size(w * xEarlyFrac, size.height))
            if (xLateFrac >= 0f)
                drawRect(resultBlueColor.copy(alpha = 0.07f),
                    topLeft = Offset(w * xLateFrac, 0f), size = Size(w - w * xLateFrac, size.height))

            // Центральная линия «0»
            drawLine(
                color       = gridLineColor.copy(alpha = 0.55f),
                start       = Offset(cx, 0f),
                end         = Offset(cx, size.height),
                strokeWidth = 1.dp.toPx()
            )

            // Пунктирные линии геом. средних
            if (xEarlyFrac >= 0f)
                drawLine(
                    color       = resultRedColor.copy(alpha = 0.9f),
                    start       = Offset(w * xEarlyFrac, 0f),
                    end         = Offset(w * xEarlyFrac, size.height),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect  = dashFx
                )
            if (xLateFrac >= 0f)
                drawLine(
                    color       = resultBlueColor.copy(alpha = 0.9f),
                    start       = Offset(w * xLateFrac, 0f),
                    end         = Offset(w * xLateFrac, size.height),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect  = dashFx
                )
            if (xAbsFrac >= 0f)
                drawLine(
                    color       = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                    start       = Offset(w * xAbsFrac, 0f),
                    end         = Offset(w * xAbsFrac, size.height),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect  = dashFx
                )

            // ── Бар / точка ───────────────────────────────────────
            when {
                isTrueThree -> {
                    drawCircle(
                        color  = resultGreenColor,
                        radius = dotR,
                        center = Offset(cx, cy)
                    )
                }

                isEarly -> {
                    // Красный бар влево от центра
                    drawRoundRect(
                        color        = resultRedColor,
                        topLeft      = Offset(cx - barLen, cy - barH / 2f),
                        size         = Size(barLen, barH),
                        cornerRadius = CornerRadius(barH / 2f)
                    )
                    // Значение у левого кончика бара (левее конца)
                    measuredText?.let { ml ->
                        val textW = ml.size.width.toFloat()
                        val textH = ml.size.height.toFloat()
                        // Идеальная позиция: левее кончика бара
                        val idealTx = cx - barLen - textGap - textW
                        // Если бар очень длинный — ставим текст внутри бара у его кончика
                        val tx = if (idealTx >= 0f) idealTx
                                 else (cx - barLen + textGap).coerceAtMost(cx - textW)
                        val ty = cy - textH / 2f
                        // Подложка, чтобы пунктирная линия не просвечивала сквозь текст
                        drawRect(
                            color   = bgColor,
                            topLeft = Offset(tx - 2f, ty - 1f),
                            size    = Size(textW + 4f, textH + 2f)
                        )
                        drawText(ml, topLeft = Offset(tx, ty))
                    }
                }

                else -> {
                    // Синий бар вправо от центра
                    drawRoundRect(
                        color        = resultBlueColor,
                        topLeft      = Offset(cx, cy - barH / 2f),
                        size         = Size(barLen, barH),
                        cornerRadius = CornerRadius(barH / 2f)
                    )
                    // Значение у правого кончика бара (правее конца)
                    measuredText?.let { ml ->
                        val textW = ml.size.width.toFloat()
                        val textH = ml.size.height.toFloat()
                        val idealTx = cx + barLen + textGap
                        // Если бар очень длинный — ставим текст внутри бара у кончика
                        val tx = if (idealTx + textW <= w) idealTx
                                 else (cx + barLen - textGap - textW).coerceAtLeast(cx)
                        val ty = cy - textH / 2f
                        // Подложка против просвечивания пунктирной линии
                        drawRect(
                            color   = bgColor,
                            topLeft = Offset(tx - 2f, ty - 1f),
                            size    = Size(textW + 4f, textH + 2f)
                        )
                        drawText(ml, topLeft = Offset(tx, ty))
                    }
                }
            }
        }

        // ── Serenity coins column ─────────────────────────────────
        val scmText = when {
            isScmHeader -> "SC"
            showScm && state.scm != null -> "%.2f".format(state.scm)
            else -> ""
        }
        BasicText(
            modifier = Modifier.width(32.dp),
            text  = scmText,
            style = statisticTextStyle.copy(
                fontSize   = 10.sp,
                fontWeight = if (isScmHeader) FontWeight.Bold else FontWeight.Normal,
                color      = MaterialTheme.colorScheme.baseTextColor.copy(
                                 alpha = if (isScmHeader) 0.6f else 0.45f),
                textAlign  = TextAlign.End
            )
        )
    }
}
