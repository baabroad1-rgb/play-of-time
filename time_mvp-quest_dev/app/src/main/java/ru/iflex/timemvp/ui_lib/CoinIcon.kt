package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Мини-монетка (Alex 2026-07-26, изначально в MainScreen.kt как приватный
 * composable вместо подписи «ΣSC») — золотой кружок с бликом. Вынесена сюда
 * (Alex 2026-09-10) чтобы переиспользовать на экране Ranks вместо текстовой
 * подписи "SC" в заголовке колонки.
 */
@Composable
fun CoinIcon(modifier: Modifier = Modifier) {
    val gold     = Color(0xFFE0B341)
    val goldEdge = Color(0xFFB8901F)
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(gold, r, c)
        drawCircle(goldEdge, r, c, style = Stroke(width = 0.8.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.35f, Offset(c.x - r * 0.28f, c.y - r * 0.28f))
    }
}
