package online.playoftime.ui_lib

import android.os.Build
import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Живой измеритель частоты опроса тачскрина (Alex 2026-07-25).
 *
 * Прямого API нет — меряем по таймстампам семплов внутри жеста. Compose отдаёт
 * батч-семплы через change.historical (реальные метки времени панели); считаем
 * частоту = 1000 / медианный интервал.
 *
 * ВАЖНО (Alex): драг меряет частоту при ДВИЖЕНИИ — панель на движении её и
 * поднимает. В игре же одиночный ТАП без движения ловится на БАЗОВОЙ частоте.
 * Поэтому тут два режима:
 *   • ВОДИ пальцем — потолок (что панель умеет на движении);
 *   • ПРИЖМИ и держи почти неподвижно ~1 c — базовая частота, релевантная тапу.
 *
 * Жест ПОГЛОЩАЕТСЯ (change.consume) — экран под метром больше не скроллится.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchRateMeter(modifier: Modifier = Modifier) {
    var medianHz    by remember { mutableStateOf(0) }
    var peakHz      by remember { mutableStateOf(0) }
    var sampleCount by remember { mutableStateOf(0) }
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val times = ArrayList<Long>(512)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (Build.VERSION.SDK_INT >= 33) {
                        runCatching {
                            view.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)
                        }
                    }
                    times.add(down.uptimeMillis)
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull() ?: break
                        ch.historical.forEach { times.add(it.uptimeMillis) }
                        times.add(ch.uptimeMillis)
                        ch.consume()               // не даём скроллу перехватить жест
                        if (times.size > 500) {
                            repeat(times.size - 500) { times.removeAt(0) }
                        }
                        // Интервалы между семплами → частота.
                        val iv = ArrayList<Long>(times.size)
                        for (i in 1 until times.size) {
                            val d = times[i] - times[i - 1]
                            if (d > 0) iv.add(d)
                        }
                        if (iv.size >= 6) {
                            iv.sort()
                            val med  = iv[iv.size / 2]
                            val fast = iv[iv.size / 20]     // 5-й перцентиль = пик
                            if (med  > 0) medianHz = (1000.0 / med).roundToInt()
                            if (fast > 0) peakHz   = (1000.0 / fast).roundToInt()
                            sampleCount = times.size
                        }
                        if (!ch.pressed) break
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text  = if (medianHz > 0) "$medianHz Hz" else "— Hz",
                style = TextStyle(
                    color = Color(0xFF3FA864), fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            )
            BasicText(
                text = if (medianHz > 0)
                    "median · peak $peakHz Hz · $sampleCount samples"
                else
                    "Drag = ceiling (moving) · press & hold still = tap baseline",
                style = TextStyle(
                    color = Color(0xFFAAAAAA), fontSize = 11.sp, textAlign = TextAlign.Center
                )
            )
            if (medianHz > 0) {
                // Секунды, не мс (Alex 2026-09-10: "не 4,0ms, а 0,004s") — 3 знака
                // после запятой, чтобы миллисекундное разрешение не терялось.
                val cellS = 1000.0 / medianHz / 1000.0
                val precS = cellS / 2.0
                BasicText(
                    text = "cell ≈ %.3f s  →  effective precision ±%.3f s"
                        .format(cellS, precS),
                    style = TextStyle(
                        color = Color(0xFF6FA8DC), fontSize = 11.sp, textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}
