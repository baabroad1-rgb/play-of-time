package online.playoftime.ui_lib

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun Icon(
    modifier: Modifier = Modifier,
    painter: Painter,
    color: Color? = null
) {
    Box(
        modifier = modifier
            .paint(
                painter = painter,
                colorFilter = if (color != null)
                    ColorFilter.tint(
                        color = color
                    ) else null
            )
    )
}