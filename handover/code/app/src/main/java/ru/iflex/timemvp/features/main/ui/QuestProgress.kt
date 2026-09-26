package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.resultGreenColor

/**
 * Симвыолическая индикация прогресса квеста: мишени (штрих-«взято» с зелёным
 * бейджем-чеком), пучок стрел (визуальное «неопределённо много попыток»),
 * и текстовая инфо-плашка справа.
 *
 * Компоновка целей:
 *  · IN_N / ONE_ONLY  — мишени с gap 4 dp (толщина внешнего кремового кольца)
 *  · ROW              — мишени перекрываются на 25%
 */
@Composable
fun QuestProgress(
    levelId       : Int,
    taskId        : Int,
    totalTasks    : Int,
    isSubquest    : Boolean,
    aimTimeStr    : String,   // "3.0", "3.00", "3.000"
    toleranceStr  : String,   // "" или "± 0.001"
    victoryCount  : Int,      // 1, 2, 3
    isRow         : Boolean,  // true = ROW-квест (streak), false = IN_N / ONE_ONLY
    progress      : Int,      // сколько уже landed (0..victoryCount)
    windowLimit   : Int?,     // null = без лимита, иначе N=33/66/99
    windowUsed    : Int       // сколько стрел уже потрачено
) {
    val text = MaterialTheme.colorScheme.baseTextColor

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // ── Мишени ─────────────────────────────────────────────────────────
        TargetsRow(
            victoryCount = victoryCount,
            landed       = progress.coerceIn(0, victoryCount),
            isRow        = isRow
        )

        Spacer(Modifier.width(10.dp))

        // ── Пучок стрел ────────────────────────────────────────────────────
        // remaining = сколько попыток ещё в окне (windowLimit - windowUsed).
        // Если лимита нет — рисуем полный символический пучок из 11 стрел.
        // Если 4+ — пучок с масштабированной плотностью (до 11 при 11+).
        // Если 1..3 — стрелы РАЗДЕЛЁННЫЕ, каждая тает по мере расхода попыток.
        val remaining = if (windowLimit != null) (windowLimit - windowUsed).coerceAtLeast(0)
                        else Int.MAX_VALUE
        ArrowsBundle(
            remaining = remaining,
            modifier  = Modifier.size(width = 52.dp, height = 46.dp)
        )

        Spacer(Modifier.width(10.dp))

        // ── Инфо-текст ─────────────────────────────────────────────────────
        // weight(1f) — текст занимает ВСЁ свободное место справа от стрел
        // (Alex 2026-07-15: «расширяй область текста квеста если есть
        // свободное пространство») — меньше принудительных переносов.
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val kindLabel = if (isSubquest) "subquest" else "quest"
            BasicText(
                text  = "Level $levelId · $kindLabel $taskId of $totalTasks",
                style = TextStyle(
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color      = text.copy(alpha = 0.50f),
                    letterSpacing = 0.04.sp
                )
            )
            BasicText(
                text  = buildAnnotatedString {
                    withStyle(SpanStyle(color = text.copy(alpha = 0.55f))) {
                        append("aim: ")
                    }
                    withStyle(SpanStyle(color = Color(0xFFFFD35E), fontWeight = FontWeight.Medium)) {
                        val aim = if (toleranceStr.isNotEmpty()) "$aimTimeStr $toleranceStr" else aimTimeStr
                        append(aim)
                    }
                    // Для ROW квестов явно указываем "in row × N/M", чтобы игрок сразу
                    // видел что нужно подряд, а не просто N попаданий в окне.
                    // Для IN_N и ONE_ONLY — компактный формат "· N/M".
                    if (isRow) {
                        withStyle(SpanStyle(color = text.copy(alpha = 0.55f))) {
                            append(" in row × ")
                        }
                    } else {
                        withStyle(SpanStyle(color = text.copy(alpha = 0.55f))) {
                            append(" · ")
                        }
                    }
                    withStyle(SpanStyle(color = resultGreenColor, fontWeight = FontWeight.Bold)) {
                        append("$progress⁠/⁠$victoryCount")
                    }
                    if (windowLimit != null) {
                        withStyle(SpanStyle(color = text.copy(alpha = 0.55f))) {
                            append(" · left ")
                        }
                        val leftShots = (windowLimit - windowUsed).coerceAtLeast(0)
                        withStyle(SpanStyle(color = resultGreenColor, fontWeight = FontWeight.Bold)) {
                            append("$leftShots")
                        }
                        withStyle(SpanStyle(color = text.copy(alpha = 0.55f))) {
                            append("⁠/⁠$windowLimit")
                        }
                    } else {
                        withStyle(SpanStyle(color = text.copy(alpha = 0.55f))) {
                            append(" · no limit")
                        }
                    }
                },
                style = TextStyle(
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color      = text
                )
            )
        }
    }
}


// ────────────────────────────────────────────────────────────────────────────
//  Targets row — мишени, для ROW перекрываются на 25%
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TargetsRow(
    victoryCount: Int,
    landed      : Int,
    isRow       : Boolean
) {
    // Диаметр мишени фиксирован 32 dp — компактный, читаемый на телефоне.
    val diameterDp = 32.dp
    val gapDp      = if (isRow) (-8).dp else 4.dp  // ROW = overlap 25%, IN_N = gap = внешнее кольцо
    val totalWidth = diameterDp * victoryCount + gapDp * (victoryCount - 1)

    Canvas(modifier = Modifier.size(width = totalWidth, height = diameterDp)) {
        val diameterPx = diameterDp.toPx()
        val gapPx      = gapDp.toPx()
        // ── Сначала рисуем НЕПОРАЖЁННЫЕ мишени (задний план),
        //    потом ПОРАЖЁННЫЕ (передний план). Так их зелёный бейдж не
        //    режется соседями.
        for (i in 0 until victoryCount) {
            if (i >= landed) {
                drawTarget(
                    center = Offset(
                        x = diameterPx / 2f + i * (diameterPx + gapPx),
                        y = diameterPx / 2f
                    ),
                    radius = diameterPx / 2f,
                    hit    = false
                )
            }
        }
        for (i in 0 until victoryCount) {
            if (i < landed) {
                drawTarget(
                    center = Offset(
                        x = diameterPx / 2f + i * (diameterPx + gapPx),
                        y = diameterPx / 2f
                    ),
                    radius = diameterPx / 2f,
                    hit    = true
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTarget(
    center: Offset,
    radius: Float,
    hit: Boolean
) {
    // Классическая мишень: кремовая «бумажка» + чёрный контур + синий + красный + жёлтый центр.
    drawCircle(color = Color(0xFFFFF6E6), radius = radius, center = center)
    drawCircle(color = Color(0xFF2A1E00), radius = radius, center = center,
        style = Stroke(width = 1.dp.toPx()))
    drawCircle(color = Color(0xFF2D2D2D), radius = radius * (15f / 19f), center = center)
    drawCircle(color = Color(0xFF378ADD), radius = radius * (11f / 19f), center = center)
    drawCircle(color = Color(0xFFD14848), radius = radius * (7f  / 19f), center = center)
    drawCircle(color = Color(0xFFFFD500), radius = radius * (2.5f / 19f), center = center)

    if (hit) {
        // Зелёный wash поверх мишени + бейдж с чеком в правом верхнем углу.
        drawCircle(color = Color(0x613FA864), radius = radius, center = center)

        val badgeR = radius * 0.42f
        val badgeCenter = Offset(
            x = center.x + radius * 0.55f,
            y = center.y - radius * 0.55f
        )
        drawCircle(color = Color(0xFF3FA864), radius = badgeR, center = badgeCenter)
        drawCircle(color = Color.White, radius = badgeR, center = badgeCenter,
            style = Stroke(width = 1.3.dp.toPx()))

        // Белый «check».
        val checkPath = Path().apply {
            val cx = badgeCenter.x
            val cy = badgeCenter.y
            moveTo(cx - badgeR * 0.45f, cy + badgeR * 0.05f)
            lineTo(cx - badgeR * 0.10f, cy + badgeR * 0.40f)
            lineTo(cx + badgeR * 0.55f, cy - badgeR * 0.35f)
        }
        drawPath(
            path  = checkPath,
            color = Color.White,
            style = Stroke(
                width = 1.7.dp.toPx(),
                cap   = StrokeCap.Round,
                join  = StrokeJoin.Round
            )
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Arrows bundle — 11 параллельных вертикальных стрел с белой обводкой 1px
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArrowsBundle(
    remaining: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (remaining <= 0) return@Canvas

        // ViewBox 68 × 54 из макета — масштабируем в размер Modifier.size.
        val vw = 68f
        val vh = 54f
        val sx = size.width  / vw
        val sy = size.height / vh

        if (remaining > 3) {
            // Пучок «наваленной кучи» — до 11 стрел, плотность масштабируется по
            // остатку. При remaining ≥ 11 рисуем всё; при 4..10 берём префикс
            // позиций (визуально пачка становится тоньше).
            val positions = listOf(
                0f  to 3f,   9f  to 1f,  18f to 4f,  27f to 2f,
                36f to 4f,   45f to 1f,   5f to 3f,  14f to 2f,
                23f to 3f,   32f to 3f,  41f to 2f
            )
            val visibleCount = minOf(11, remaining)
            positions.take(visibleCount).forEach { (px, py) ->
                drawArrowV(originX = px * sx, originY = py * sy, sx = sx, sy = sy)
            }
        } else {
            // ≤ 3 стрелы — раздельные, равномерно распределены по ширине.
            // Каждая тает по мере расхода: остаётся ровно `remaining` штук.
            // Древко стрелы = 16 (halfWidth 8) в vb-координатах. Центрируем.
            val slotW = vw / 3f
            for (i in 0 until remaining) {
                val slotCenter = slotW * i + slotW * 0.5f
                val originX = slotCenter - 8f   // 8 = половина ширины стрелы
                drawArrowV(originX = originX * sx, originY = 2f * sy, sx = sx, sy = sy)
            }
        }
    }
}

/**
 * Одна вертикальная стрела: наконечник ▲ вверху, коричневое древко, красное
 * оперение внизу, тёмное ушко. Белая обводка 1 px по всем контурам.
 * Работает в собственной системе координат 16 × 50, скалируется через sx/sy.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowV(
    originX: Float,
    originY: Float,
    sx: Float,
    sy: Float
) {
    val strokeW = 1.dp.toPx()

    fun p(x: Float, y: Float) = Offset(originX + x * sx, originY + y * sy)

    // Head — металлический треугольник.
    val head = Path().apply {
        moveTo(p(8f,  0f).x,  p(8f,  0f).y)
        lineTo(p(0f, 10f).x,  p(0f, 10f).y)
        lineTo(p(16f, 10f).x, p(16f, 10f).y)
        close()
    }
    drawPath(head, color = Color(0xFFB0B0B0))
    drawPath(head, color = Color.White, style = Stroke(width = strokeW, join = StrokeJoin.Round))

    // Shaft — деревянное древко.
    val shaftTopLeft = p(5.5f, 9f)
    val shaftSize    = Size(width = 5f * sx, height = 26f * sy)
    drawRect(color = Color(0xFF8A6A38), topLeft = shaftTopLeft, size = shaftSize)
    drawRect(color = Color.White,       topLeft = shaftTopLeft, size = shaftSize,
        style = Stroke(width = strokeW))

    // Fletching — красное оперение (два «крыла» + разрез).
    val fletch = Path().apply {
        moveTo(p( 8f, 32f).x, p( 8f, 32f).y)
        lineTo(p( 0f, 44f).x, p( 0f, 44f).y)
        lineTo(p( 4f, 47f).x, p( 4f, 47f).y)
        lineTo(p( 8f, 40f).x, p( 8f, 40f).y)
        lineTo(p(12f, 47f).x, p(12f, 47f).y)
        lineTo(p(16f, 44f).x, p(16f, 44f).y)
        close()
    }
    drawPath(fletch, color = Color(0xFFD14848))
    drawPath(fletch, color = Color.White, style = Stroke(width = strokeW, join = StrokeJoin.Round))
    drawLine(
        color       = Color.White,
        start       = p(8f, 32f),
        end         = p(8f, 47f),
        strokeWidth = strokeW,
        cap         = StrokeCap.Round
    )

    // Nock — тёмное ушко.
    val nockTopLeft = p(6.5f, 46f)
    val nockSize    = Size(width = 3f * sx, height = 3f * sy)
    drawRect(color = Color(0xFF5A3E1E), topLeft = nockTopLeft, size = nockSize)
    drawRect(color = Color.White,       topLeft = nockTopLeft, size = nockSize,
        style = Stroke(width = 0.8.dp.toPx()))
}
