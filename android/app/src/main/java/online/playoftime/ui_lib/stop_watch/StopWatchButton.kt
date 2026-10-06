package online.playoftime.ui_lib.stop_watch

import android.text.TextPaint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import online.playoftime.R
import online.playoftime.core.services.VibrationService
import online.playoftime.core.utils.ButtonResultType
import online.playoftime.core.utils.StopWatchCycle
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.darkGreyColor
import online.playoftime.ui_theme.resultBlueColor
import online.playoftime.ui_theme.resultGreenColor
import online.playoftime.ui_theme.resultRedColor

private val ButtonCardShape = RoundedCornerShape(24.dp)

/**
 * Горизонтальный прогресс-бар секунд (Alex 2026-07-23) — альтернатива числовому
 * отсчёту. Заполняется за 4 секунды; засечки на 1/2/3/4 с, «3» (aim) — зелёная
 * и выше остальных. Засечка «3» выравнивается по вертикали нуля диаграммы
 * ошибок (aimZeroXRoot, root-координата) — оттуда после попытки капает капля.
 * Головка заполнения — вертикальная засечка той же толщины/высоты, что и «3».
 */
@Composable
private fun SecondsProgressBar(
    /** Доля заполнения 0..1 — уже разрешённая снаружи (живое время во время
     *  бега ЛИБО замороженное по касанию STOP значение). Полоса — чистая
     *  функция отрисовки, никакой памяти/логики стопа внутри. */
    fillFrac: Float,
    fillColor: Color,
    /** Левый край окна (с) и его ширина (с). Центр «3.000» = startSec + spanSec/2. */
    startSec: Float,
    spanSec: Float,
    /** Знаков после запятой в подписях — по точности уровня: L1=1 (3.0), L2=2
     *  (3.00), L3=3 (3.000). Края и центр печатаются с одной «зернистостью». */
    decimals: Int,
    /** Допуск победы в мс (0 = нет индульгенции по точности — зона не рисуется). */
    toleranceMs: Int = 0,
    modifier: Modifier = Modifier,
    /** Геометрия для анимации капли: tick3XRoot, headXRoot, trackYRoot. */
    onGeometry: (Float, Float, Float) -> Unit = { _, _, _ -> }
) {
    val density     = LocalDensity.current
    val labelSizePx = with(density) { 9.sp.toPx() }
    val labelPaint  = remember {
        TextPaint().apply {
            isAntiAlias = true
            typeface    = android.graphics.Typeface.MONOSPACE
            textAlign   = android.graphics.Paint.Align.CENTER
        }
    }
    labelPaint.textSize = labelSizePx

    val trackColor = Color.White.copy(alpha = 0.14f)
    val labelColor = Color.White.copy(alpha = 0.45f)

    var barBounds by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }

    // Окно полосы задаётся снаружи (масштаб настраивается). «3.000» (aim) в центре
    // при симметричном окне — рисуем по aimFrac.
    val animFrac = fillFrac.coerceIn(0f, 1f)
    val aimFrac  = ((3.0f - startSec) / spanSec).coerceIn(0f, 1f)

    // Боковые поля (Alex 2026-07-30): трек/риски вжаты внутрь на пол-ширины
    // самой широкой подписи, чтобы КРАЙНИЕ числа центрировались строго под
    // своей риской и не обрезались краем канвы.
    val padGapPx     = with(density) { 3.dp.toPx() }
    val sampleLabel  = if (decimals > 0) "0." + "0".repeat(decimals) else "0"
    val labelHalfPx  = labelPaint.measureText(sampleLabel) / 2f
    val padPx        = labelHalfPx + padGapPx

    // Геометрия в root-координатах — для капли в MainScreen. tick3X = позиция
    // «3.000» (aim), headX = позиция головки (точка остановки). Учитываем поля.
    val b = barBounds
    if (b != null && b.width > 0f) {
        val innerW   = (b.width - 2f * padPx).coerceAtLeast(1f)
        val tick3XB  = padPx + innerW * aimFrac
        val headXB   = padPx + innerW * animFrac
        val trackYPx = with(density) { 11.dp.toPx() }
        androidx.compose.runtime.SideEffect {
            onGeometry(b.left + tick3XB, b.left + headXB, b.top + trackYPx)
        }
    }

    Canvas(
        modifier = modifier
            .height(34.dp)
            .onGloballyPositioned { barBounds = it.boundsInRoot() }
    ) {
        val w      = size.width
        val trackH = 5.dp.toPx()
        val trackY = 11.dp.toPx()         // центр линии трека
        val radius = trackH / 2f
        val corner = androidx.compose.ui.geometry.CornerRadius(radius)
        // Внутренняя область (вжата на padPx с каждой стороны) — чтобы крайние
        // подписи центрировались под риской и не обрезались.
        val innerLeft = padPx
        val innerW    = (w - 2f * padPx).coerceAtLeast(1f)

        // Трек
        drawRoundRect(
            color        = trackColor,
            topLeft      = androidx.compose.ui.geometry.Offset(innerLeft, trackY - trackH / 2f),
            size         = androidx.compose.ui.geometry.Size(innerW, trackH),
            cornerRadius = corner
        )
        // Зона победы на индульгированных субквестах (Alex 2026-08-12): когда
        // t > 0, любой результат в [3.000−t .. 3.000+t] засчитывается как победа —
        // подсвечиваем эту зону зелёным ДО отрисовки заполнения, чтобы прогресс
        // (fillColor) естественно перекрывал её при проходе головки бара.
        if (toleranceMs > 0) {
            val tolSec      = toleranceMs / 1000.0
            val zoneStartFrac = (((3.0 - tolSec) - startSec) / spanSec).coerceIn(0.0, 1.0)
            val zoneEndFrac   = (((3.0 + tolSec) - startSec) / spanSec).coerceIn(0.0, 1.0)
            val zoneLeft  = innerLeft + innerW * zoneStartFrac.toFloat()
            val zoneRight = innerLeft + innerW * zoneEndFrac.toFloat()
            if (zoneRight > zoneLeft) {
                drawRoundRect(
                    color        = resultGreenColor.copy(alpha = 0.35f),
                    topLeft      = androidx.compose.ui.geometry.Offset(zoneLeft, trackY - trackH / 2f),
                    size         = androidx.compose.ui.geometry.Size(zoneRight - zoneLeft, trackH),
                    cornerRadius = corner
                )
            }
        }
        // Заполнение — кончик ПЛОСКИЙ (без скругления), скруглён только левый край.
        val fillW = (innerW * animFrac).coerceIn(0f, innerW)
        if (fillW > 0f) {
            val fillPath = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                    left   = innerLeft,
                    top    = trackY - trackH / 2f,
                    right  = innerLeft + fillW,
                    bottom = trackY + trackH / 2f,
                    topLeftCornerRadius     = corner,
                    bottomLeftCornerRadius  = corner,
                    topRightCornerRadius    = androidx.compose.ui.geometry.CornerRadius.Zero,
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius.Zero
                ))
            }
            drawPath(fillPath, fillColor)
        }
        // Позиция секунды s на полосе окна [2.900..3.100].
        fun xOfSec(s: Float): Float = (innerLeft + ((s - startSec) / spanSec) * innerW)
            .coerceIn(innerLeft, innerLeft + innerW)

        // 10 делений; подписи только у КРАЁВ окна (левый/правый).
        val nTicks = 10
        val stepSec = spanSec / nTicks
        for (i in 0..nTicks) {
            val sec  = startSec + i * stepSec
            val edge = i == 0 || i == nTicks
            val x    = xOfSec(sec)
            val over = if (edge) 5.dp.toPx() else 3.5.dp.toPx()
            drawLine(
                color       = if (edge) Color.White.copy(alpha = 0.9f)
                              else      Color.White.copy(alpha = 0.6f),
                start       = androidx.compose.ui.geometry.Offset(x, trackY - trackH / 2f),
                end         = androidx.compose.ui.geometry.Offset(x, trackY + trackH / 2f + over),
                strokeWidth = 3f
            )
            if (edge) {
                // Левый край — красный (early), правый — синий (late), Alex 2026-08-12:
                // цвет края сразу подсказывает, в какую сторону от "3" он лежит.
                val edgeLabelColor = if (i == 0) resultRedColor else resultBlueColor
                labelPaint.color = edgeLabelColor.toArgb()
                // Зернистость подписи = точность уровня (1/10, 1/100, 1/1000).
                val txt  = String.format(java.util.Locale.US, "%.${decimals}f", sec)
                // Центр числа СТРОГО под риской (поля гарантируют, что влезет).
                drawIntoCanvas {
                    it.nativeCanvas.drawText(txt, x, trackY + trackH / 2f + 17.dp.toPx(), labelPaint)
                }
            }
        }
        // Зелёная метка «3.000» — в РЕАЛЬНОЙ позиции aim (окно может быть
        // несимметричным, центр ≠ 3.000).
        val tick3X = innerLeft + innerW * aimFrac
        drawLine(
            color       = resultGreenColor,
            start       = androidx.compose.ui.geometry.Offset(tick3X, trackY - trackH / 2f),
            end         = androidx.compose.ui.geometry.Offset(tick3X, trackY + trackH / 2f + 6.dp.toPx()),
            strokeWidth = 3f
        )
        run {
            labelPaint.color = resultGreenColor.toArgb()
            // Центр «3.0 / 3.00 / 3.000» — той же зернистости, что и края.
            val txt  = String.format(java.util.Locale.US, "%.${decimals}f", 3.0)
            drawIntoCanvas {
                it.nativeCanvas.drawText(txt, tick3X, trackY + trackH / 2f + 17.dp.toPx(), labelPaint)
            }
        }

        // Головка текущего времени — «клювик» (треугольник) остриём ВНИЗ к
        // полосе; вершина касается верхнего края трека в позиции головки
        // (Alex 2026-07-23). С «3» совпадает только при точных 3.000.
        if (fillW > 0f) {
            val exact3 = kotlin.math.abs(animFrac - aimFrac) < 0.0005f
            var hx = (innerLeft + fillW).coerceIn(innerLeft, innerLeft + innerW)
            if (!exact3 && kotlin.math.abs(hx - tick3X) < 2f) {
                hx = if (animFrac < aimFrac) tick3X - 2f else tick3X + 2f
            }
            val apexY = trackY - trackH / 2f            // остриё — у верха трека
            val halfB = 3.dp.toPx()
            val beakH = 5.dp.toPx()
            val beak = androidx.compose.ui.graphics.Path().apply {
                moveTo(hx, apexY)                       // остриё вниз
                lineTo(hx - halfB, apexY - beakH)
                lineTo(hx + halfB, apexY - beakH)
                close()
            }
            drawPath(beak, fillColor)
        }
    }
}

/**
 * Renders each character in its own fixed-width Canvas cell inside a Row.
 * Total Row width = charCount × cellWidth — a compile-time constant — so the
 * Row's centering offset never changes and there is zero X-axis jitter.
 *
 * Why per-cell: even with a single full-width Canvas at Align.CENTER, the
 * Compose layout engine still positions the Canvas element inside the Column
 * using measured widths that can have sub-pixel rounding differences per frame.
 * Fixed-width cells bypass all layout measurement for the time display.
 */
@Composable
private fun StopwatchTimeText(time: String, color: Color) {
    val density    = LocalDensity.current
    val textSizePx = with(density) { 70.sp.toPx() }

    val paint = remember {
        TextPaint().apply {
            isAntiAlias = true
            textSize    = textSizePx
            typeface    = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
            textAlign   = android.graphics.Paint.Align.CENTER
        }
    }
    paint.color = color.toArgb()

    // Measure once; monospace → all glyphs same advance width
    val fm           = remember(textSizePx) { paint.fontMetrics }
    val cellWidthDp  = with(density) { paint.measureText("0").toDp() }
    val dotWidthDp   = with(density) { paint.measureText(".").toDp() }
    val cellHeightDp = with(density) { (fm.descent - fm.ascent).toDp() }
    val baseline     = -fm.ascent   // distance from canvas top to text baseline

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        time.forEach { ch ->
            Canvas(
                modifier = Modifier
                    .width(if (ch == '.') dotWidthDp else cellWidthDp)
                    .height(cellHeightDp)
            ) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        ch.toString(),
                        size.width / 2f,
                        baseline,
                        paint
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun StopWatchButton(
    modifier: Modifier = Modifier,
    state: StopWatchCycle = StopWatchCycle.ON_START,
    timeState: State<String>,
    onPress: (touchWallMs: Long, touchUptimeMs: Long) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    vibrationService: VibrationService = koinInject(),
    resultType: ButtonResultType = ButtonResultType.NONE,
    showHurray: Boolean = false,
    /** Прогресс-бар секунд под символом кнопки (настройка, Alex 2026-07-23). */
    showProgressBar: Boolean = true,
    /** Числовой отсчёт во время бега; результат после STOP показывается всегда. */
    showNumericRun: Boolean = true,
    /** Масштаб бара: полуокно в мс (край = 3.000 ± это). Настраивается зумом. */
    barHalfWindowMs: Int = 100,
    /** Зернистость подписей бара = точность уровня: 1 (3.0), 2 (3.00), 3 (3.000). */
    barDecimals: Int = 3,
    /** Текущий допуск победы в мс (Alex 2026-08-12): на индульгированных по точности
     *  субквестах (t > 0) закрашивает зону [3.000−t .. 3.000+t] зелёным на баре —
     *  это и есть зона, которая сейчас засчитывается как победа. 0 = не рисовать. */
    barToleranceMs: Int = 0,
    /** Правая зона кнопки, отдаваемая колёсику масштаба: тап по ней НЕ считается
     *  RUN/RESET (но только в покое — во время бега тап везде = STOP). */
    rightDeadZone: Dp = 0.dp,
    /** Геометрия бара (tick3XRoot, headXRoot, trackYRoot) — для анимации капли. */
    onBarGeometry: (Float, Float, Float) -> Unit = { _, _, _ -> },
    /** Явная ширина прогресс-бара (Alex 2026-08-25): когда задана — используется
     *  ВМЕСТО ширины по блоку цифр, чтобы полоса совпадала по ширине с гистограммой
     *  частотного распределения (MainScreen передаёт измеренную ширину её plot-области).
     *  null — прежнее поведение (ширина = блок цифр "0.000"). */
    progressBarWidth: Dp? = null,
    /** Горизонтальный сдвиг прогресс-бара (Alex 2026-09-09): гистограмма и лог
     *  теперь растянуты и сдвинуты вправо (CHART_SC_PAD < CHART_LABEL_WIDTH,
     *  см. DistribChart.kt) — бар подгоняется тем же сдвигом, чтобы остаться
     *  visually привязан к их plot-области. Сама круглая кнопка НЕ сдвигается,
     *  остаётся на истинном центре экрана — только полоса над ней. 0 = как раньше. */
    progressBarOffsetX: Dp = 0.dp,
    /** Режим справки (Alex 2026-09-10, второй заход): в этом режиме тап по
     *  кнопке НЕ должен запускать/останавливать секундомер — вместо этого он
     *  должен показать бабл справки про эту зону. Первая попытка (просто
     *  enabled=false из MainScreen) не сработала — кнопка ловит касание на
     *  СВОЁМ собственном pointerInput(PointerEventPass.Initial), раньше
     *  ЛЮБОГО Main-pass обработчика выше по дереву, так что оттуда справка
     *  вообще не видела тап. Единственный надёжный способ — обработать это
     *  ПРЯМО ЗДЕСЬ, внутри уже существующего gesture-loop'а кнопки. */
    helpMode: Boolean = false,
    /** Вызывается вместо onPress, когда тап по кнопке пришёлся на helpMode=true
     *  И это был именно ТАП (без значимого сдвига пальца) — драг/скролл не
     *  считается. */
    onHelpTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    // Читаем .value ЗДЕСЬ — Compose регистрирует чтение в scope этой composable,
    // а не в scope MainScreen (который передал нам объект State<String>).
    // Тик обновляет timeState.value → рекомпозируется только StopWatchButton
    // (и StopwatchTimeText внутри), а не 720-строчный MainScreen.
    val time: String = timeState.value

    // rememberUpdatedState держит актуальные lambdas без рестарта pointerInput.
    // Ключ pointerInput(Unit) — не пересобираем корутину-обработчик на каждый recompose;
    // иначе теряется первый Down при частых setState (тик × 16 мс).
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentState   by rememberUpdatedState(state)
    val currentHelpMode by rememberUpdatedState(helpMode)
    val currentOnHelpTap by rememberUpdatedState(onHelpTap)

    // Повышенная дискретизация тача (Alex 2026-07-25): просим ОС отдавать
    // касания БЕЗ батчинга (unbuffered) — вместе с Game Mode (appCategory=game
    // в манифесте) это на поддерживающих устройствах поднимает частоту опроса
    // тач-панели с дефолтных ~120 Гц до 480–1000 Гц → метка касания честная,
    // без 8-мс сетки. Запрос действует до ACTION_UP, поэтому пере-арм на каждом
    // жесте (в pointerInput ниже) + при входе на экран.
    val view = LocalView.current
    fun requestHiRateTouch() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching {
                view.requestUnbufferedDispatch(android.view.InputDevice.SOURCE_TOUCHSCREEN)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { requestHiRateTouch() }

    // Однотонная кнопка (Alex 2026-07-26): цвет результата берём НЕПРОЗРАЧНЫМ
    // (compositeOver тёмной базы), а не полупрозрачным 0.22 поверх фона. Раньше
    // сквозь полупрозрачную заливку просвечивала тень elevation — по краю
    // читалась «внутренняя рамка» чуть темнее. Теперь фон плотный, тень не
    // проступает, кнопка ровного тона.
    val buttonBase = Color(0xFF2D2D2D)
    val buttonBackground: Color = when (resultType) {
        ButtonResultType.EARLY      -> resultRedColor.copy(alpha = 0.22f).compositeOver(buttonBase)
        ButtonResultType.LATE       -> resultBlueColor.copy(alpha = 0.22f).compositeOver(buttonBase)
        ButtonResultType.TRUE_THREE -> resultGreenColor.copy(alpha = 0.22f).compositeOver(buttonBase)
        ButtonResultType.NONE       -> buttonBase
    }

    // While running: STOP icon + "STOP" label turn green from 2.0s onward.
    val elapsedSeconds: Double = time.toDoubleOrNull() ?: 0.0

    // ── Полоса прогресса: заморозка ПО КАСАНИЮ STOP ────────────────────────
    // Alex 2026-07-25: «останови отрисовку полосы по тому же событию, что и
    // счётчик». Самый ранний момент, когда мы знаем о стопе — САМО касание,
    // ловим его в pointerInput на PointerEventPass.Initial, ДО всей задержки
    // обработки ввода. В момент касания фиксируем текущую долю заполнения и
    // больше НЕ следуем за тиком: тик за время latency (тем большее, чем
    // сильнее занят main-поток — а он занят сразу после старта короткой
    // попытки, т.е. в «красной зоне») успевал показать время БОЛЬШЕ точки
    // останова, и полоса заезжала вперёд. Теперь заезжать нечему.
    //   • касание STOP (state==IS_RUNNING) → frozenFrac = доля на момент касания;
    //   • касание RUN/RESET или любой переход в ON_START → frozenFrac = null.
    var frozenFrac by remember { mutableStateOf<Float?>(null) }
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state == StopWatchCycle.ON_START) frozenFrac = null
    }
    // Окно бара из масштаба: 3.000 ± half, но зажато в [0..4] с. На широком
    // зуме окно становится несимметричным ([0..4] при half=3000).
    // Симметрично вокруг 3.000 (Alex 2026-07-30): «3» всегда строго по центру,
    // не смещается. Правый край НЕ зажимаем в 4 c — иначе центр уезжал.
    val barLeftSec  = maxOf(0.0, (3000.0 - barHalfWindowMs) / 1000.0)
    val barRightSec = (3000.0 + barHalfWindowMs) / 1000.0
    val barStartSec = barLeftSec
    val barSpanSec  = barRightSec - barLeftSec
    // pointerInput(Unit) не рестартует на рекомпозиции → окно читаем через
    // rememberUpdatedState, иначе заморозка использовала бы старый масштаб.
    val curBarStart by rememberUpdatedState(barStartSec)
    val curBarSpan  by rememberUpdatedState(barSpanSec)
    val liveFrac = (((elapsedSeconds - barStartSec) / barSpanSec).toFloat()).coerceIn(0f, 1f)
    // В покое (ON_STOP) показываем ФИНАЛЬНОЕ значение — совпадает с цифрами.
    // frozenFrac ловил ДИСПЛЕЙ на момент касания, который отстаёт от истинной
    // kernel-метки; из-за этого при 3.002 клювик оказывался СЛЕВА от «3.000».
    // frozenFrac нужен только пока идёт latency-окно (state ещё IS_RUNNING),
    // чтобы полоса не забегала вперёд.
    val fillFrac = if (state == StopWatchCycle.ON_STOP) liveFrac else (frozenFrac ?: liveFrac)
    val iconTint: Color = when {
        state == StopWatchCycle.ON_STOP -> when (resultType) {
            ButtonResultType.EARLY      -> resultRedColor
            ButtonResultType.LATE       -> resultBlueColor
            ButtonResultType.TRUE_THREE -> resultGreenColor
            else                        -> Color.White
        }
        state == StopWatchCycle.IS_RUNNING && elapsedSeconds >= 2.0 -> resultGreenColor
        else -> Color.White
    }

    val timeColor: Color = when {
        state == StopWatchCycle.ON_STOP -> when (resultType) {
            ButtonResultType.EARLY      -> resultRedColor
            ButtonResultType.LATE       -> resultBlueColor
            ButtonResultType.TRUE_THREE -> resultGreenColor
            else                        -> Color.White
        }
        else -> Color.White
    }

    Column(
        modifier = modifier
            .shadow(
                elevation    = 6.dp,
                shape        = ButtonCardShape,
                clip         = true,
                spotColor    = darkGreyColor.copy(alpha = 0.4f),
                ambientColor = darkGreyColor.copy(alpha = 0.2f)
            )
            // Ловим ACTION_DOWN на PointerEventPass.Initial — раньше, чем
            // clickable's detectTapGestures (Main pass). Change.uptimeMillis —
            // kernel-timestamp момента, когда сенсор зарегистрировал касание;
            // задержка Compose-диспетчера сюда уже не входит. Из uptime получаем
            // wall-clock через delta (SystemClock.uptimeMillis не подвержен
            // NTP-коррекциям, поэтому монотонность в паре START/STOP гарантирована).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    // Пере-арм unbuffered/hi-rate на каждый жест (действует до UP).
                    requestHiRateTouch()
                    // Правая зона отдана колёсику масштаба — тап по ней не RUN/RESET.
                    // Только в покое: во время бега тап ВЕЗДЕ = STOP (точность важна).
                    val inWheelZone = currentState != StopWatchCycle.IS_RUNNING &&
                        rightDeadZone.value > 0f &&
                        down.position.x > size.width - rightDeadZone.toPx()
                    // В режиме справки RUN/STOP не должен срабатывать вообще —
                    // см. currentHelpMode-ветку в конце этого же gesture-loop'а,
                    // которая вместо этого покажет бабл справки.
                    if (currentEnabled && !inWheelZone && !currentHelpMode) {
                        // Полоса: заморозка/освобождение в САМЫЙ ранний момент
                        // касания — раньше currentOnPress и любой обработки.
                        if (currentState == StopWatchCycle.IS_RUNNING) {
                            val eAtTouch = timeState.value.toDoubleOrNull() ?: 0.0
                            frozenFrac = (((eAtTouch - curBarStart) / curBarSpan).toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            frozenFrac = null
                        }
                        val touchUptime = down.uptimeMillis
                        val nowUptime = SystemClock.uptimeMillis()
                        val nowWall = System.currentTimeMillis()
                        val touchWall = nowWall - (nowUptime - touchUptime)
                        currentOnPress(touchWall, touchUptime)
                        // Вибро на нажатие — только для RUN / RESET. Для STOP
                        // вибрация уехала в MainViewModel и бьёт В МОМЕНТ
                        // ФИКСАЦИИ числа (Alex 2026-07-15) — тактильное
                        // подтверждение «результат записан», а не «палец коснулся».
                        if (currentState != StopWatchCycle.IS_RUNNING) {
                            vibrationService.heavy()
                        }
                    }
                    // Не consume — clickable ниже получит тот же down и покажет ripple.
                    // Дождёмся up/cancel, попутно СОБИРАЯ таймстампы семплов касания
                    // (базовая частота тапа) для адаптивного дизеринга. Замер идёт
                    // ПОСЛЕ currentOnPress — на длительность попытки не влияет.
                    // Заодно (Alex 2026-09-10) следим за touch-slop — нужно знать,
                    // был ли это чистый тап (для helpMode-ветки ниже), а не драг.
                    val samples = ArrayList<Long>(64)
                    down.historical.forEach { samples.add(it.uptimeMillis) }
                    samples.add(down.uptimeMillis)
                    val downPos = down.position
                    val touchSlopPx = viewConfiguration.touchSlop
                    var passedSlop = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                            ?: ev.changes.firstOrNull() ?: break
                        ch.historical.forEach { samples.add(it.uptimeMillis) }
                        samples.add(ch.uptimeMillis)
                        if (!passedSlop && (ch.position - downPos).getDistance() > touchSlopPx) {
                            passedSlop = true
                        }
                        if (!ch.pressed) break
                        // Движение пальца, начатое на кнопке, кнопка забирает
                        // себе (Alex 2026-10-06): иначе одним движением запускали
                        // секундомер и перелистывали пейджер на соседний экран.
                        // Main-проход идёт от кнопки к пейджеру, поэтому
                        // съеденное здесь пейджер уже не примет за свайп.
                        // Зона колёсика масштаба — со своим жестом, её не трогаем.
                        if (!inWheelZone) ch.consume()
                    }
                    online.playoftime.core.utils.TouchRateEstimator.feed(samples)
                    // Режим справки: RUN/STOP не сработал (гейт выше), но тап
                    // ДОЛЖЕН показать бабл — если это правда был тап, а не драг.
                    if (currentHelpMode && !inWheelZone && !passedSlop) {
                        currentOnHelpTap()
                    }
                }
            }
            .clickable(
                enabled           = enabled,
                interactionSource = interactionSource,
                indication        = LocalIndication.current
            ) {}
            .background(buttonBackground)
            .defaultMinSize(minHeight = Dimens.mainButtonHeight)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Прогресс-бар секунд — НАД цифрами (Alex 2026-07-23), шириной по
        // границам цифрового блока. Виден ВСЕГДА (в т.ч. в ON_START — пустой трек
        // со шкалой). Цвет полосы совпадает с цветом цифр секундомера: белый в
        // покое/беге, цвет результата после STOP (зелёный только за чистую тройку).
        if (showProgressBar && !showHurray) {
            // Ширина бара: по умолчанию — ширина блока цифр (тот же 70sp monospace
            // bold, что у StopwatchTimeText, пусто в ON_START → шаблон «0.000»).
            // Если снаружи передана progressBarWidth (Alex 2026-08-25: должна
            // совпадать с гистограммой частотного распределения) — используем её.
            val density   = LocalDensity.current
            val widthTime = time.ifEmpty { "0.000" }
            val digitBarWidth = remember(widthTime.length) {
                with(density) {
                    val p = TextPaint().apply {
                        textSize = 70.sp.toPx()
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.MONOSPACE,
                            android.graphics.Typeface.BOLD
                        )
                    }
                    val digitW = p.measureText("0")
                    val dotW   = p.measureText(".")
                    widthTime.fold(0f) { acc, ch ->
                        acc + (if (ch == '.') dotW else digitW)
                    }.toDp()
                }
            }
            val barWidth = progressBarWidth ?: digitBarWidth
            // Если время вылетело ЗА правый край окна — красим полосу синим
            // (поздно, «пролетел диапазон»). Иначе — цвет цифр.
            val barFillColor =
                if (elapsedSeconds > barStartSec + barSpanSec) resultBlueColor
                else timeColor
            SecondsProgressBar(
                modifier   = Modifier
                    .width(barWidth)
                    .offset(x = progressBarOffsetX),
                fillFrac   = fillFrac,
                fillColor  = barFillColor,
                startSec   = barStartSec.toFloat(),
                spanSec    = barSpanSec.toFloat(),
                decimals   = barDecimals,
                toleranceMs = barToleranceMs,
                onGeometry = onBarGeometry
            )
            Spacer(Modifier.height(2.dp))
        }

        // Числовой отсчёт можно выключить в настройках — но только для бега:
        // зафиксированный результат (ON_STOP) показываем всегда.
        val numericVisible = time.isNotEmpty() &&
            (state != StopWatchCycle.IS_RUNNING || showNumericRun)
        if (numericVisible) {
            StopwatchTimeText(time = time, color = timeColor)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Hide the run/stop/reset icon while showing the HOORAY! affirmation.
            if (!showHurray) {
                Image(
                    modifier = Modifier.size(28.dp),
                    painter = painterResource(
                        id = when (state) {
                            StopWatchCycle.ON_START   -> R.drawable.ic_run
                            StopWatchCycle.IS_RUNNING -> R.drawable.ic_stop
                            StopWatchCycle.ON_STOP    -> R.drawable.ic_reset
                        }
                    ),
                    contentDescription = "",
                    colorFilter        = ColorFilter.tint(iconTint)
                )
            }

            BasicText(
                text = when {
                    showHurray -> "HOORAY!"
                    else -> when (state) {
                        StopWatchCycle.ON_START   -> context.getString(R.string.run)
                        StopWatchCycle.IS_RUNNING -> context.getString(R.string.stop)
                        StopWatchCycle.ON_STOP    -> context.getString(R.string.reset)
                    }
                },
                style = TextStyle(
                    fontSize   = 26.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    color      = iconTint
                )
            )
        }

    }
}

@Preview
@Composable
fun StopWatchButtonPreview() {
    var state by remember { mutableStateOf(StopWatchCycle.ON_START) }
    val timeState = remember { mutableStateOf("0.00") }
    StopWatchButton(
        state = state,
        timeState = timeState,
        onPress = { _, _ ->
            state = when (state) {
                StopWatchCycle.ON_START   -> StopWatchCycle.IS_RUNNING
                StopWatchCycle.IS_RUNNING -> StopWatchCycle.ON_STOP
                StopWatchCycle.ON_STOP    -> StopWatchCycle.ON_START
            }
        }
    )
}
