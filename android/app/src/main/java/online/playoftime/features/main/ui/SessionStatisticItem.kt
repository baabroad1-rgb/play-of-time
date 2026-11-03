package online.playoftime.features.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.playoftime.core.utils.Accuracy
import online.playoftime.ui_theme.baseBackgroundColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.resultBlueColor
import online.playoftime.ui_theme.resultGreenColor
import online.playoftime.ui_theme.winGreen
import online.playoftime.ui_theme.resultRedColor
import online.playoftime.ui_theme.resultYellowColor
import online.playoftime.ui_theme.statisticTextStyle
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
private fun SessionHeaderRow(
    label: String,
    dateLabel: String = "",
    thumbUp: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Дата попыток — ЯРКАЯ (Alex 2026-07-23), прижата к левому краю;
        // заголовок sticky, поэтому дата всегда видна при скролле лога.
        if (dateLabel.isNotEmpty()) {
            BasicText(
                text = "$dateLabel  ",
                style = statisticTextStyle.copy(
                    fontSize   = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.9f)
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
                color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.43f),
                textAlign = TextAlign.Center
            )
        )
        // Индикатор успешности сессии (Alex 2026-07-23): палец вверх (зелёный) —
        // квест взят в этой сессии; палец вниз (серый) — сессия без победы.
        // null (сессия ещё идёт) — не рисуем.
        if (thumbUp != null) {
            online.playoftime.ui_lib.Icon(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(11.dp)
                    .graphicsLayer { if (!thumbUp) rotationZ = 180f },
                painter = androidx.compose.ui.res.painterResource(
                    online.playoftime.R.drawable.ic_thumb_up
                ),
                color = if (thumbUp) resultGreenColor
                        else androidx.compose.ui.graphics.Color(0xFFB8B8B8)
            )
        }
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
    twinGroupHeight: Int = 0,
    /** >0 — верхняя строка neighbor-группы (соседние |error|); число строк в группе.
     *  Рисуется ЖЁЛТЫЙ freehand-овал вокруг значений ошибок группы. */
    neighborGroupHeight: Int = 0
) {
    val textMeasurer = rememberTextMeasurer()
    // Настройка "обводка близнецов/соседей" (Alex 2026-09-10) — можно выключить
    // в Settings. По умолчанию включено.
    val localStorage = org.koin.compose.koinInject<online.playoftime.core.data.LocalStorageService>()
    val twinOutlineEnabled = remember { localStorage.getTwinOutlineEnabled() }

    if (state.isSessionHeader) {
        SessionHeaderRow(
            label     = state.sessionLabel,
            dateLabel = state.sessionDateLabel,
            thumbUp   = state.sessionThumbUp
        )
        return
    }

    val errorMs   = state.error * 1000.0          // signed, ms
    val isTrueThree = state.isWin                 // победа с учётом допуска T±t
    val isEarly     = state.error < 0.0

    // Чистая тройка (ошибка 0 на точности уровня) — яркий зелёный; победа по
    // смягчению (в допуске, но не ровно 3) — блёклый (Alex 2026-11-03).
    val winColor = winGreen(abs(state.error) < 0.0005)
    val barColor = when {
        isTrueThree -> winColor
        isEarly     -> resultRedColor
        else        -> resultBlueColor
    }

    // Цвет времени — совпадает с цветом бара
    val timeColor = barColor

    // Формат ошибки: секунды без ведущего нуля, знак "-" для early
    // 0.04 → ".04",  0.21 → ".21",  early 0.21 → "-.21"
    // Разрядность — та, в которой игралась попытка (state.accuracy);
    // глобальная accuracy — только fallback для старых строк.
    val errorText: String = if (!isTrueThree) {
        val absVal = abs(state.error)                          // секунды
        val fmt    = when (state.accuracy ?: accuracy) {
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
    val timeTextWidthPx = remember(state.time, twinShot, state.neighborShot) {
        if ((twinShot || state.neighborShot) && state.time.isNotEmpty())
            textMeasurer.measure(state.time, timeTextStyle).size.width
        else 0
    }
    // Овал уже рисуется через drawBehind — z-order-корректно "под" содержимым
    // этой Row (число/время рисуются как дочерние элементы ПОСЛЕ фона Row,
    // drawBehind всегда красит раньше их). Alex 2026-09-09 всё равно читал
    // цифры сквозь него как "перекрытые" — не баг слоя, а то, что сплошной
    // ярко-насыщенный (alpha=1) обвод вплотную к мелкому 9-11sp моноширинному
    // шрифту создаёт визуальный шум прямо позади букв. Общая яркость обвода —
    // alpha 0.95 (100% − 5%, Alex 2026-09-10). ДОПОЛНИТЕЛЬНО (тот же день,
    // третий заход): приглушение до 30% — ТОЛЬКО там, где контур пересекает
    // значимые цифры РЕЗУЛЬТАТА (колонка времени, например "3.001") — именно
    // они должны читаться свободнее всего. Колонка "#" (номер попытки) —
    // не результат, а просто порядковый номер строки — остаётся на обычной
    // яркости 0.95 ВЕЗДЕ, даже там, где контур её пересекает. Реализовано как
    // два clip-прохода одного и того же path: снаружи dimGlyphPath (=
    // ТОЧНЫЙ контур глифов "X.XXX", не прямоугольник колонки, Alex 2026-09-11)
    // — обычная альфа 0.95, внутри — приглушённая 0.30.
    //
    // Ограничение: glyph path строится ТОЛЬКО из state.time ЭТОЙ строки —
    // когда группа (twinGroupHeight/neighborGroupHeight) растягивает овал на
    // соседнюю строку снизу, её собственные цифры этому composable-инстансу
    // не видны (там будет просто обычная яркость 0.95, без приглушения) —
    // тот же паттерн ограничения, что уже был у прежнего прямоугольного
    // dimRegion (ширина тоже бралась только из текста верхней строки).
    val rowModifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp)
        .drawBehind {
            if (!twinOutlineEnabled) return@drawBehind
            val dimGlyphPath = if (state.time.isNotEmpty())
                buildGlyphPath(
                    text = state.time,
                    style = timeTextStyle,
                    measurer = textMeasurer,
                    xLeft = 20.dp.toPx(),
                    rowHeightPx = size.height
                )
            else androidx.compose.ui.graphics.Path()
            // Twin shot: freehand-овал вокруг # и time-колонок группы (зелёный).
            if (twinGroupHeight > 0) {
                val gap = 5.dp.toPx()
                drawFreehandOval(
                    rectLeft   = -gap,
                    rectRight  = 20.dp.toPx() + timeTextWidthPx + gap,
                    rectTop    = -gap,
                    rectBottom = size.height * twinGroupHeight + gap,
                    seed  = state.startTimestamp.takeIf { it != 0L } ?: state.number * 7919L,
                    baseColor  = resultGreenColor,
                    dimGlyphPath = dimGlyphPath
                )
            }
            // Neighbor shot (Alex 2026-07-23): тот же freehand-овал и там же,
            // где twins — вокруг # и time-колонок (абсолютные значения), —
            // но ЖЁЛТЫЙ.
            if (neighborGroupHeight > 0) {
                val gap = 5.dp.toPx()
                drawFreehandOval(
                    rectLeft   = -gap,
                    rectRight  = 20.dp.toPx() + timeTextWidthPx + gap,
                    rectTop    = -gap,
                    rectBottom = size.height * neighborGroupHeight + gap,
                    seed  = (state.startTimestamp.takeIf { it != 0L }
                        ?: state.number * 7919L) + 13L,
                    baseColor  = resultYellowColor,
                    dimGlyphPath = dimGlyphPath
                )
            }
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
                fontSize   = 9.sp,   // мельче (Alex 2026-07-26)
                fontWeight = FontWeight.Normal,
                color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.53f),
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

            // Пунктирные линии геом. средних — мягче (Alex 2026-07-26): alpha
            // приглушена, линия тоньше, чтобы не забивала строки лога.
            if (xEarlyFrac >= 0f)
                drawLine(
                    color       = resultRedColor.copy(alpha = 0.40f),
                    start       = Offset(w * xEarlyFrac, 0f),
                    end         = Offset(w * xEarlyFrac, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect  = dashFx
                )
            if (xLateFrac >= 0f)
                drawLine(
                    color       = resultBlueColor.copy(alpha = 0.40f),
                    start       = Offset(w * xLateFrac, 0f),
                    end         = Offset(w * xLateFrac, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect  = dashFx
                )
            if (xAbsFrac >= 0f)
                drawLine(
                    color       = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
                    start       = Offset(w * xAbsFrac, 0f),
                    end         = Offset(w * xAbsFrac, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect  = dashFx
                )

            // ── Бар / точка ───────────────────────────────────────
            when {
                isTrueThree -> {
                    drawCircle(
                        color  = winColor,
                        radius = dotR,
                        center = Offset(cx, cy)
                    )
                }

                isEarly -> {
                    // Красный бар влево от центра. Если числу не хватает места
                    // ЗА кончиком бара — бар укорачивается и заканчивается, не
                    // доходя до текста (Alex 2026-07-23). Раньше текст рисовался
                    // ПОВЕРХ бара с подложкой, и отрезанный кончик торчал
                    // отдельной «точкой» перед числом.
                    val textW = measuredText?.size?.width?.toFloat() ?: 0f
                    val idealTx = cx - barLen - textGap - textW
                    val fits    = idealTx >= 0f
                    val visibleLen = if (fits) barLen
                                     else (cx - textW - textGap).coerceAtLeast(0f)
                    drawRoundRect(
                        color        = resultRedColor,
                        topLeft      = Offset(cx - visibleLen, cy - barH / 2f),
                        size         = Size(visibleLen, barH),
                        cornerRadius = CornerRadius(barH / 2f)
                    )
                    // Значение — всегда левее видимого кончика бара
                    measuredText?.let { ml ->
                        val textH = ml.size.height.toFloat()
                        val tx = if (fits) idealTx else 0f
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
                    // Синий бар вправо от центра — зеркальная логика: если число
                    // не помещается за кончиком, бар укорачивается до текста.
                    val textW = measuredText?.size?.width?.toFloat() ?: 0f
                    val idealTx = cx + barLen + textGap
                    val fits    = idealTx + textW <= w
                    val visibleLen = if (fits) barLen
                                     else (w - cx - textW - textGap).coerceAtLeast(0f)
                    drawRoundRect(
                        color        = resultBlueColor,
                        topLeft      = Offset(cx, cy - barH / 2f),
                        size         = Size(visibleLen, barH),
                        cornerRadius = CornerRadius(barH / 2f)
                    )
                    // Значение — всегда правее видимого кончика бара
                    measuredText?.let { ml ->
                        val textH = ml.size.height.toFloat()
                        val tx = if (fits) idealTx else w - textW
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
        // Ширина колонки = CHART_SC_PAD (не отдельный литерал — раньше была
        // жёстко 64dp и её надо было руками держать в синхроне с гистограммой;
        // Alex 2026-09-09 сдвинул CHART_SC_PAD вправо, эта колонка следует
        // автоматически). Текст выровнен по правому краю (TextAlign.End),
        // поэтому сами цифры SC визуально не сдвигаются от смены ширины —
        // сдвигается только невидимая левая граница колонки, что и сужает/
        // расширяет канвас бара слева от неё, смещая его центр.
        BasicText(
            modifier = Modifier.width(CHART_SC_PAD),
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

/**
 * Точный контур глифов текста результата (Alex 2026-09-11: "обводка теряла
 * яркость ТОЛЬКО на пересечении с линиями начертания букв... как только
 * обводка перестаёт пересекаться с пикселями буквы, яркость сразу
 * возвращается к исходной") — раньше приглушение работало по прямоугольнику
 * всей колонки (dimRegion), из-за чего между цифрами (там, где реально нет
 * никакого "начертания") контур тоже гас — небрежно, не по форме букв.
 *
 * Compose's TextLayoutResult.getPathForRange() даёт прямоугольники ПОДСВЕТКИ
 * выделения (построчные боксы), а не форму самих букв — для настоящего
 * контура чернил нужен нативный android.graphics.Paint.getTextPath(), тот же
 * API, которым пользуется text-to-path в любом Android-редакторе. Вертикаль
 * (baseline) при этом берётся из РЕАЛЬНОГО Compose-layout'а того же текста
 * (measure().getLineBaseline(0)), а не подбирается на глаз — иначе нативный
 * Paint и то, что реально нарисовал BasicText, разъедутся на пиксель-другой.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.buildGlyphPath(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    xLeft: Float,
    rowHeightPx: Float
): androidx.compose.ui.graphics.Path {
    val layout = measurer.measure(text, style)
    val textHeightPx = layout.size.height.toFloat()
    val topY = rowHeightPx / 2f - textHeightPx / 2f
    val baselineY = topY + layout.getLineBaseline(0)

    val nativePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = style.fontSize.toPx()
        typeface = if (style.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold)
            android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }
    val nativePath = android.graphics.Path()
    nativePaint.getTextPath(text, 0, text.length, xLeft, baselineY, nativePath)
    return nativePath.asComposePath()
}

/** Freehand «marker oval» — рисованная «от руки» обводка (общая для twin и
 *  neighbor shot). Сид выбирает одну из 12 форм; мелкий jitter контрольных
 *  точек делает каждую обводку уникальной. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFreehandOval(
    rectLeft: Float,
    rectRight: Float,
    rectTop: Float,
    rectBottom: Float,
    seed: Long,
    baseColor: androidx.compose.ui.graphics.Color,
    /** Точный контур глифов результата (колонка времени, НЕ "#") — контур
     *  ВНУТРИ формы букв рисуется приглушённым (30% исходной яркости),
     *  снаружи (включая промежутки между цифрами и всю колонку "#") —
     *  обычным 0.95 (Alex 2026-09-10/11: "#" сознательно не входит сюда,
     *  яркость там всегда высокая-оригинальная). */
    dimGlyphPath: androidx.compose.ui.graphics.Path
) {
    val gap = 5.dp.toPx()
    val cx = (rectLeft + rectRight) / 2f
    val cy = (rectTop  + rectBottom) / 2f
    val rxBase = (rectRight - rectLeft) / 2f
    val ryBase = (rectBottom - rectTop) / 2f
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
    val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
        width = 2.2.dp.toPx(),
        cap   = androidx.compose.ui.graphics.StrokeCap.Round,
        join  = androidx.compose.ui.graphics.StrokeJoin.Round
    )
    // Вне формы букв (включая промежутки между цифрами и всю колонку "#") —
    // обычная яркость 0.95.
    clipPath(
        path = dimGlyphPath,
        clipOp = androidx.compose.ui.graphics.ClipOp.Difference
    ) {
        drawPath(path = path, color = baseColor.copy(alpha = 0.95f), style = strokeStyle)
    }
    // Ровно внутри контура букв — приглушено до 30%.
    clipPath(
        path = dimGlyphPath,
        clipOp = androidx.compose.ui.graphics.ClipOp.Intersect
    ) {
        drawPath(path = path, color = baseColor.copy(alpha = 0.30f), style = strokeStyle)
    }
}
