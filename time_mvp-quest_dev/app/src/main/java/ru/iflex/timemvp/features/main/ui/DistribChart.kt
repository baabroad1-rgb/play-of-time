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
 * Ширина правого отступа. С 2026-08-22 по 2026-09-09 была РАВНА CHART_LABEL_WIDTH
 * (64dp) специально, чтобы «0» гистограммы/лога совпадал с истинным центром
 * экрана (тем же, что и центр круглой кнопки секундомера) — см. историю ниже.
 *
 * Alex 2026-09-09: та симметрия читалась как «странная пустота» справа —
 * слева всегда есть контент (номер+время/лейбл), справа за узкой колонкой SC
 * пусто, и одинаковая ширина отступа визуально НЕ ощущалась одинаковой.
 * Асимметрия теперь НАМЕРЕННАЯ и в обратную сторону: правый отступ меньше
 * левого, гистограмма и лог растянуты и сдвинуты вправо — центр их plot-области
 * съезжает вправо от истинного центра экрана на (CHART_LABEL_WIDTH−CHART_SC_PAD)/2.
 * Круглая кнопка секундомера остаётся на истинном центре (не двигаем её саму),
 * прогресс-бар над ней и мишени в QuestProgress получают такой же сдвиг
 * отдельно (StopWatchButton.progressBarOffsetX, TargetsCenteredRow в
 * QuestProgress.kt), чтобы визуально не отвязаться от гистограммы/лога.
 *
 * Значение — тот же исходный физический размер колонки SC (32dp), что был ДО
 * 2026-08-22. Alex сначала попросил уменьшить правый отступ до 12dp, но на
 * эмуляторе это оказалось «слишком сильно подвинул»: смотрится несимметрично
 * не потому что края экрана неровные (12dp слева/справа от контента —
 * буквально совпадают), а потому что левая колонка визуально ПЛОТНАЯ (всегда
 * заполнена цифрами), а правая при 12dp — пустая узкая полоска, читается как
 * перекос сильнее, чем реальная разница в 52dp. 32dp — компромисс: ощутимо
 * уже прежних 64dp (эффект растяжения/сдвига есть), но не настолько узко,
 * чтобы выглядеть обрезанным. Сдвиг центра при этом значении = 16dp.
 *
 * История: до 2026-08-22 здесь было 32dp (фактическая ширина колонки SC в
 * SessionStatisticItem) — центр съезжал на ~16dp ВПРАВО от истинного (слева
 * 64dp под номер+время, справа только 32dp под SC). 2026-08-22 подняли до
 * 64dp и совпадающе расширили колонку SC в SessionStatisticItem — проверено
 * пиксельно на эмуляторе (совпало с x≈540px при 1080×2400@420dpi). Колонка SC
 * в SessionStatisticItem ссылается на эту же константу (не отдельный литерал),
 * так что оба места всегда меняются синхронно.
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
     * «Капля» в полёте (Alex 2026-07-15): бакет свежей попытки с этой ошибкой.
     * Его высота ПРОГРЕССИВНО растёт от (count−1) до count по мере падения капли
     * (см. suppressReleaseFrac) — вершина столбца поднимается навстречу капле,
     * и капля пролетает ровно через неё.
     */
    suppressErrorMs: Double? = null,
    /** 0 — столбец-цель подавлен на 1 (капля ещё летит); 1 — полная высота
     *  (капля приземлилась). Дробные значения — плавный рост во время падения. */
    suppressReleaseFrac: Float = 0f
) {
    if (buckets.isEmpty()) return

    // Индекс столбца-цели капли. maxCount считаем по ПОЛНЫМ counts (шкала не
    // прыгает во время роста), а высоту цели уменьшаем на (1 − releaseFrac).
    val targetIdx: Int? = suppressErrorMs?.let { e ->
        buckets.indexOfFirst { e >= it.startMs && e < it.endMs }.takeIf { it >= 0 }
            ?: buckets.indices.minByOrNull { kotlin.math.abs(buckets[it].centerMs - e) }
    }
    fun effCount(i: Int, c: Int): Float =
        if (i == targetIdx) (c - 1 + suppressReleaseFrac).coerceAtLeast(0f) else c.toFloat()

    val textMeasurer = rememberTextMeasurer()
    val labelColor   = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.53f)
    val axisColor    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.2f)

    val labelStyle = TextStyle(
        fontSize   = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color      = labelColor
    )

    // Fixed-width symmetric buckets — height = raw count, floored at 5 so a
    // lone attempt doesn't slam to full canvas height. maxCount — по полным
    // counts: шкала стабильна, пока столбец-цель растёт под каплей.
    val maxCount = maxOf(buckets.maxOfOrNull { it.count } ?: 1, 5)
    val scaleMax = scaleMaxMs.coerceAtLeast(1.0)

    val countLabelStyle = TextStyle(
        fontSize   = 7.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.90f)
    )
    val sideAxisStyle = TextStyle(
        fontSize   = 7.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.53f)
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
                val bucketW = w / buckets.size.toFloat()
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
                    // Нумерация только справа (Alex 2026-07-26) — левую убрали.
                    drawText(txt, topLeft = Offset(w - txt.size.width, yy - txt.size.height / 2f))
                    // Горизонтальные линейки приглушены (0.25 → 0.10); тянутся от
                    // левого края до правой цифры.
                    drawLine(
                        color = axisColor.copy(alpha = 0.10f),
                        start = Offset(0f, yy),
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
                val slotW = w / buckets.size.toFloat()
                val barW = (slotW * 0.6f).coerceIn(0.5.dp.toPx(), 3.dp.toPx())
                buckets.forEachIndexed { i, bucket ->
                    if (bucket.count == 0) return@forEachIndexed
                    if (bucket.endMs < -scaleMax || bucket.startMs > scaleMax) return@forEachIndexed
                    // Зелёный столбик — ВСЕГДА один: только бакет, содержащий 0
                    // (ровно 3.000). В индульгированных субквестах победами
                    // считаются и 2.999/3.001, но зелёным помечаем лишь центр —
                    // чистую тройку (Alex 2026-07-26). Остальные победы окрашены
                    // по стороне (red/blue).
                    val isZeroBucket = bucket.startMs <= 0.0 && 0.0 < bucket.endMs
                    val barColor = when {
                        isZeroBucket -> resultGreenColor
                        bucket.centerMs < 0.0 -> resultRedColor
                        else -> resultBlueColor
                    }
                    val barH = (effCount(i, bucket.count) / maxCount.toFloat()) * barAreaH
                    if (barH <= 0f) return@forEachIndexed
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

        // ── Ось: подписи значений (лейбл "Distrib." убран — Alex 2026-08-22,
        //    самоочевидно без подписи; заодно строка стала ниже — Geom. Mean
        //    под ней подтягивается ближе к гистограмме). Spacer сохраняет ту
        //    же левую ширину, чтобы подписи оси остались на своих местах.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CHART_LABEL_WIDTH))
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
