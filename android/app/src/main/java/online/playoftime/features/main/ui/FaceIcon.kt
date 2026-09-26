package online.playoftime.features.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FaceKind { READY, NICE, SAD }

/**
 * Маленький face-пиктограмм под кнопкой «?» на главном экране.
 * Тот же графический язык, что в визуализации (тонированный круг,
 * брови, глаза с бликом, рот). Размер по умолчанию совпадает с «?» (26dp).
 */
@Composable
fun FaceIcon(
    kind: FaceKind,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    /** Если задан — рисует ВСЁ лицо (контур, глаза, рот, брови) этим цветом БЕЗ заливки. */
    tint: Color? = null,
    /** 0..1 — правый глаз плавно превращается в горизонтальную линию (подмигивание). Для NICE. */
    winkFactor: Float = 0f,
    /** 0..1 — рот дополнительно изгибается «вниз/наружу»: для SAD усиливает грустную дугу
     *  (изгибает линию рта, как в расстройстве); для NICE слегка растягивает улыбку. */
    mouthStretch: Float = 0f
) {
    val stroke = tint ?: when (kind) {
        FaceKind.READY -> Color(0xFFD9A22A)
        FaceKind.NICE  -> Color(0xFF3FA864)
        FaceKind.SAD   -> Color(0xFFB8B8B8)
    }
    val fill = if (tint != null) Color.Transparent else when (kind) {
        FaceKind.READY -> Color(0xFFFFF7E0)
        FaceKind.NICE  -> Color(0xFFEAF8EF)
        FaceKind.SAD   -> Color(0xFFF2F2F2)
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r  = (minOf(w, h) / 2f) - 1.dp.toPx()

        val faceStroke = 1.4.dp.toPx()
        val featStroke = 1.2.dp.toPx()

        // Face
        drawCircle(color = fill,   radius = r, center = Offset(cx, cy))
        drawCircle(color = stroke, radius = r, center = Offset(cx, cy),
            style = Stroke(width = faceStroke))

        // Brows (slightly raised arches)
        val browY = cy - r * 0.45f
        val browDx = r * 0.55f
        val browDy = r * 0.12f
        val leftBrow = Path().apply {
            moveTo(cx - browDx, browY)
            quadraticBezierTo(cx - browDx * 0.5f, browY - browDy, cx - browDx * 0.18f, browY)
        }
        val rightBrow = Path().apply {
            moveTo(cx + browDx * 0.18f, browY)
            quadraticBezierTo(cx + browDx * 0.5f, browY - browDy, cx + browDx, browY)
        }
        drawPath(leftBrow,  stroke, style = Stroke(width = featStroke))
        drawPath(rightBrow, stroke, style = Stroke(width = featStroke))

        // Eyes
        val eyeY = cy - r * 0.1f
        val eyeDx = r * 0.35f
        val eyeR = r * 0.09f
        drawCircle(stroke, eyeR, Offset(cx - eyeDx, eyeY))
        // Right eye — если winkFactor > 0 плавно превращается в горизонтальную «черту».
        val wink = winkFactor.coerceIn(0f, 1f)
        if (wink < 0.05f) {
            drawCircle(stroke, eyeR, Offset(cx + eyeDx, eyeY))
        } else {
            val lineHalfW = eyeR * (1f + 0.6f * wink)
            drawLine(
                color = stroke,
                start = Offset(cx + eyeDx - lineHalfW, eyeY),
                end   = Offset(cx + eyeDx + lineHalfW, eyeY),
                strokeWidth = featStroke * (1f + 0.4f * wink),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        // Highlight (alertness — strongest on READY, subtle on others)
        if (kind == FaceKind.READY) {
            val hR = eyeR * 0.32f
            drawCircle(fill, hR, Offset(cx - eyeDx + hR * 0.4f, eyeY - hR * 0.6f))
            drawCircle(fill, hR, Offset(cx + eyeDx + hR * 0.4f, eyeY - hR * 0.6f))
        }

        // Mouth
        val stretch = mouthStretch.coerceIn(0f, 1f)
        val mouthDx = r * 0.45f
        val mouthY  = cy + r * 0.42f
        val mouthPath = Path()
        when (kind) {
            FaceKind.NICE -> {
                // stretch слегка расширяет улыбку и опускает контрольную точку ниже.
                val dxN  = mouthDx * (1f + 0.10f * stretch)
                val endY = mouthY - r * 0.05f
                val ctrlY = mouthY + r * (0.40f + 0.10f * stretch)
                mouthPath.moveTo(cx - dxN, endY)
                mouthPath.quadraticBezierTo(cx, ctrlY, cx + dxN, endY)
            }
            FaceKind.SAD -> {
                // stretch тянет уголки рта вниз и изгибает контрольную точку ЕЩЁ выше
                // (обратная дуга — «расстроенно изгибает линию рта»).
                val endY  = mouthY + r * (0.20f + 0.18f * stretch)
                val ctrlY = mouthY - r * (0.20f + 0.35f * stretch)
                mouthPath.moveTo(cx - mouthDx, endY)
                mouthPath.quadraticBezierTo(cx, ctrlY, cx + mouthDx, endY)
            }
            FaceKind.READY -> {
                // calm slight curl (neutral readiness)
                mouthPath.moveTo(cx - mouthDx * 0.85f, mouthY)
                mouthPath.quadraticBezierTo(cx, mouthY + r * 0.10f, cx + mouthDx * 0.85f, mouthY)
            }
        }
        drawPath(mouthPath, stroke, style = Stroke(width = featStroke + 0.4f))
    }
}
