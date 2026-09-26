package ru.iflex.timemvp.ui_lib.stop_watch

import android.text.TextPaint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.services.VibrationService
import ru.iflex.timemvp.core.utils.ButtonResultType
import ru.iflex.timemvp.core.utils.StopWatchCycle
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.ui_theme.darkGreyColor
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultGreenColor
import ru.iflex.timemvp.ui_theme.resultRedColor

private val ButtonCardShape = RoundedCornerShape(24.dp)

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

@Composable
fun StopWatchButton(
    modifier: Modifier = Modifier,
    state: StopWatchCycle = StopWatchCycle.ON_START,
    timeState: State<String>,
    onPress: (touchWallMs: Long, touchUptimeMs: Long) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    vibrationService: VibrationService = koinInject(),
    resultType: ButtonResultType = ButtonResultType.NONE,
    showHurray: Boolean = false
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

    val buttonBackground: Color = when (resultType) {
        ButtonResultType.EARLY      -> resultRedColor.copy(alpha = 0.22f)
        ButtonResultType.LATE       -> resultBlueColor.copy(alpha = 0.22f)
        ButtonResultType.TRUE_THREE -> resultGreenColor.copy(alpha = 0.22f)
        ButtonResultType.NONE       -> Color(0xFF2D2D2D)
    }

    // While running: STOP icon + "STOP" label turn green from 2.0s onward.
    val elapsedSeconds: Double = time.toDoubleOrNull() ?: 0.0
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
                    if (currentEnabled) {
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
                    // Дождёмся up/cancel, чтобы awaitEachGesture правильно завершила цикл
                    // (иначе следующий Down не будет замечен).
                    waitForUpOrCancellation()
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
        if (time.isNotEmpty()) {
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
