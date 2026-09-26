package ru.iflex.timemvp.features.main.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.ui_lib.AnimatedLogo
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.resultGreenColor

/**
 * Символическая индикация прогресса квеста: цель, мишени, характеристика
 * механики, ряд пипсов окна попыток (Alex 01.09 — область квеста упрощена
 * до этих элементов, сверено с присланными эскизами; "Level N · quest Y of 5",
 * субквест-тег и счётчик "left N/M" убраны — эта информация уже дублируется
 * в cheer-up бабле и заголовке сессии в логе).
 *
 * Мишени: непоражённые — обычная нарисованная мишень; поражённые (Alex
 * 2026-08-23, «мишени по мере выбивания должны превращаться в анимированные
 * логотипы») — заменяются на вращающийся [AnimatedLogo] (тот же бренд-марк,
 * что на About-экране).
 *
 * Компоновка целей:
 *  · IN_N / ONE_ONLY  — мишени с gap 4 dp (толщина внешнего кремового кольца)
 *  · ROW              — мишени перекрываются на 25%
 */
@Composable
fun QuestProgress(
    levelId       : Int,      // возвращено 01.09 — две строки "Level x" / "Quest y.z[.w]" слева
    taskId        : Int,
    /** Ступень индульгенции субквеста (1/2/3); 0 = baseline, не субквест.
     *  Маска квеста x.x.x = level.task.subStep — третий разряд ТОЛЬКО если
     *  есть субквест (Alex 01.09: «если подквеста нет, последний разряд
     *  отсутствует»). */
    subStep       : Int = 0,
    aimTimeStr    : String,   // "3.0", "3.00", "3.000"
    toleranceStr  : String,   // "" или "± 0.001"
    victoryCount  : Int,      // 1, 2, 3
    isRow         : Boolean,  // true = ROW-квест (streak), false = IN_N / ONE_ONLY
    progress      : Int,      // сколько уже landed (0..victoryCount)
    windowLimit   : Int?,     // null = без лимита, иначе N=33/66/99
    /** Цвета сыгранных попыток текущего окна, СТАРЕЙШАЯ ПЕРВОЙ (Alex 25.08:
     *  замена пучка стрел на линейный ряд «пипсов» — по одному на каждую
     *  попытку окна, закрашены цветом результата, остаток окна — пустые
     *  кружки). */
    pipsColors    : List<Color> = emptyList(),
    completed     : Boolean = false, // квест только что пройден (awaitingHurray) —
                                      // рисуем чек справа от условий (Alex 2026-08-14)
    /** Слот иконки "?" (Alex 01.09) — рендерится через MainScreen (там живёт
     *  helpMode/helpQuestionRect/тап-обработчик), но позиционируется ЗДЕСЬ,
     *  в том же Box, что и центрируемый комплекс — на одной высоте с ним, не
     *  поверх ряда пипсов. Пусто по умолчанию (превью/другие вызовы без "?"). */
    helpIcon      : @Composable () -> Unit = {}
) {
    val text = MaterialTheme.colorScheme.baseTextColor

    // Финальный квест игры (L3.5) БЕЗ индульгенции (subStep == 0) — заголовок
    // заменяется на "LAST QUEST" (Alex 2026-09-22). В субквесте (subStep > 0)
    // остаётся обычная "Quest 3.5.N" нотация — там видно, что это ещё не
    // чистый финал. Раз в 3 минуты подпись вспыхивает зелёным и плавно гаснет
    // обратно — привлекает внимание к тому, что игра почти пройдена.
    val isFinalBaseline = levelId == 3 && taskId == 5 && subStep == 0
    val headerText = if (isFinalBaseline) "LAST QUEST"
                      else "Quest $levelId.$taskId" + if (subStep > 0) ".$subStep" else ""
    val pulse = remember { Animatable(0f) } // 0 = обычный цвет подписи, 1 = полностью зелёный
    if (isFinalBaseline) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(3 * 60_000L)
                pulse.snapTo(1f)
                pulse.animateTo(0f, animationSpec = tween(durationMillis = 4000, easing = FastOutSlowInEasing))
            }
        }
    }
    val baseHeaderColor = text.copy(alpha = 0.63f)
    val headerColor = if (isFinalBaseline)
        androidx.compose.ui.graphics.lerp(baseHeaderColor, resultGreenColor, pulse.value)
    else baseHeaderColor

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Заголовок + "?" — верхняя строка панели. Размеры сняты с эскиза
        // Alex 2026-09-21 (1 "px" эскиза = 1 dp): строка 24 dp, "?" 24 dp у
        // правого края, подпись "Quest L.T[.s]" по центру, базовая линия
        // ~18 dp от верха строки (центр текста на ~3 dp ниже центра "?").
        Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            BasicText(
                text     = headerText,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                style    = TextStyle(
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color      = headerColor,
                    textAlign  = TextAlign.Center
                )
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                helpIcon()
            }
        }
        // Базовая линия подписи -> верх пипсов = 18 dp (≈12.5 dp от низа строки).
        Spacer(Modifier.height(12.5.dp))

        // ── Ряд «пипсов» окна попыток. Один кружок на каждую попытку окна:
        // сыгранные — закрашены цветом результата (старейшая слева),
        // несыгранные — маленькие пустые КОЛЕЧКИ (обводка, не заливка).
        val pipsTotal = windowLimit ?: pipsColors.size
        if (pipsTotal > 0) {
            AttemptPipsRow(
                total     = pipsTotal,
                colors    = pipsColors,
                modifier  = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
        }

        // ── Мишени — ЦЕНТРАЛЬНЫЙ элемент строки (Alex 05.09: «строчку с
        // мишенями выравняй по центру, аналогично подписи с уровнем и
        // квестами — центральный элемент это мишени»). Раньше центрировался
        // bounding-box всей Row(aim+targets+label) — при разной ширине aim
        // слева и label справа сама группа мишеней сдвигалась от истинного
        // центра на разницу этих ширин. TargetsCenteredRow — кастомный
        // Layout: targets всегда садится на true-center панели (та же ось,
        // что у Level/Quest выше и у прогресс-бара/гистограммы/лога),
        // aim/label пристыковываются к НЕЙ, а не наоборот.
        TargetsCenteredRow(
            aimContent = {
                // Размер сведён к размеру надписи справа (Alex 01.09) — раньше
                // было крупное 24sp число, теперь тот же 14sp, что у "N× in a row".
                BasicText(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = resultGreenColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                            append(aimTimeStr)
                        }
                        if (toleranceStr.isNotEmpty()) {
                            withStyle(SpanStyle(color = resultGreenColor.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 12.sp)) {
                                append(" $toleranceStr")
                            }
                        }
                    }
                )
            },
            targetsContent = {
                TargetsRow(
                    victoryCount = victoryCount,
                    landed       = progress.coerceIn(0, victoryCount),
                    isRow        = isRow
                )
            },
            trailingContent = {
                // ── Характеристика механики ("2× in a row" / "2× in total") ──
                BasicText(
                    text  = buildAnnotatedString {
                        withStyle(SpanStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                            append("${victoryCount}×")
                        }
                        withStyle(SpanStyle(color = text.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, fontSize = 14.sp)) {
                            append(if (isRow) " in a row" else " in total")
                        }
                    }
                )
                // ── Чек выполненного квеста ──────────────────────────────
                // Справа от условий, только пока completed=true (awaitingHurray —
                // текст условий ещё держит ТОЛЬКО ЧТО пройденный квест, новый
                // ещё не подставлен). Прорисовывается медленно, будто от руки.
                if (completed) {
                    Spacer(Modifier.width(8.dp))
                    HandDrawnCheck()
                }
            }
        )
        // Низ мишеней -> верх карточки секундомера = 26 dp (16 здесь + 10 dp
        // Spacer в MainScreen); от базовой линии цели до карточки выходит 36 dp.
        Spacer(Modifier.height(16.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  TargetsCenteredRow — кастомный Layout (Alex 05.09): мишени всегда садятся
//  на true-center доступной ширины, aim пристыковывается СПРАВА-НАЛЕВО к
//  левому краю мишеней, trailing (label+check) — СЛЕВА-НАПРАВО к правому
//  краю. В отличие от центрирования обычного Row (где центрируется bounding
//  box целиком, и группа мишеней смещается на разницу ширин aim/trailing),
//  здесь центр мишеней = центр панели ВСЕГДА, независимо от того что и как
//  широко написано слева/справа.
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TargetsCenteredRow(
    aimContent      : @Composable () -> Unit,
    targetsContent  : @Composable () -> Unit,
    trailingContent : @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = Modifier.fillMaxWidth(),
        content  = {
            Box { aimContent() }
            Box { targetsContent() }
            Row(verticalAlignment = Alignment.CenterVertically) { trailingContent() }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val aimPlaceable      = measurables[0].measure(loose)
        val targetsPlaceable  = measurables[1].measure(loose)
        val trailingPlaceable = measurables[2].measure(loose)

        val aimGapPx     = 12.dp.roundToPx()
        val trailingGapPx = 12.dp.roundToPx()

        val totalWidth = constraints.maxWidth
        // Эскиз Alex 2026-09-21: мишени на ИСТИННОМ центре экрана (ось
        // "SCREEN CENTER"), сдвиг вправо от 09.09 здесь снят.
        val centerShiftPx = 0
        val targetsX   = (totalWidth - targetsPlaceable.width) / 2 + centerShiftPx
        val aimX       = targetsX - aimGapPx - aimPlaceable.width
        val trailingX  = targetsX + targetsPlaceable.width + trailingGapPx

        val height = maxOf(aimPlaceable.height, targetsPlaceable.height, trailingPlaceable.height)

        layout(totalWidth, height) {
            aimPlaceable.place(aimX, (height - aimPlaceable.height) / 2)
            targetsPlaceable.place(targetsX, (height - targetsPlaceable.height) / 2)
            trailingPlaceable.place(trailingX, (height - trailingPlaceable.height) / 2)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Hand-drawn check — зелёная галочка, прорисовывается постепенно (как будто
//  от руки), появляется справа от условий только что пройденного квеста.
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun HandDrawnCheck(
    modifier: Modifier = Modifier
) {
    // key = Unit — переиспользуем один Animatable, но перезапускаем анимацию
    // при каждом новом монтировании (композиция создаётся заново каждый раз,
    // когда completed переключается false→true, т.к. родительский `if` целиком
    // убирает/добавляет этот узел из дерева).
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 700, delayMillis = 120, easing = FastOutSlowInEasing)
        )
    }
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        // Классическая «галочка»: короткий штрих вниз-вправо, затем длинный вверх-вправо.
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.78f)
            lineTo(w * 0.88f, h * 0.22f)
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val length = measure.length
        val animatedPath = Path()
        measure.getSegment(0f, length * progress.value, animatedPath, true)
        drawPath(
            path  = animatedPath,
            color = resultGreenColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
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
    // Диаметр мишени 30 dp (эскиз 2026-09-21: шаг центров 22.5 dp = 75% диаметра).
    val diameterDp = 30.dp
    val gapDp      = if (isRow) (-7.5).dp else 4.dp  // ROW = overlap 25%, IN_N = gap = внешнее кольцо
    val stepDp     = diameterDp + gapDp
    val totalWidth = diameterDp * victoryCount + gapDp * (victoryCount - 1)

    // Позиционируем через Box + offset (не единый Canvas), т.к. поражённые
    // мишени теперь — живой @Composable (AnimatedLogo), а не рисунок внутри
    // DrawScope (Alex 2026-08-23: «мишени по мере выбивания должны
    // превращаться в анимированные логотипы»). Порядок как раньше:
    // непоражённые сначала (задний план), поражённые поверх — так их бейдж
    // не режется соседями при перекрытии на ROW-квестах.
    Box(modifier = Modifier.size(width = totalWidth, height = diameterDp)) {
        for (i in 0 until victoryCount) {
            if (i >= landed) {
                Canvas(
                    modifier = Modifier
                        .size(diameterDp)
                        .offset(x = stepDp * i)
                ) {
                    drawTarget(center = Offset(size.width / 2f, size.height / 2f), radius = size.width / 2f)
                }
            }
        }
        for (i in 0 until victoryCount) {
            if (i < landed) {
                Box(
                    modifier = Modifier
                        .size(diameterDp)
                        .offset(x = stepDp * i)
                        .clip(CircleShape)
                ) {
                    AnimatedLogo(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTarget(
    center: Offset,
    radius: Float
) {
    // Классическая мишень: кремовая «бумажка» + чёрный контур + синий + красный + жёлтый центр.
    drawCircle(color = Color(0xFFFFF6E6), radius = radius, center = center)
    drawCircle(color = Color(0xFF2A1E00), radius = radius, center = center,
        style = Stroke(width = 1.dp.toPx()))
    drawCircle(color = Color(0xFF2D2D2D), radius = radius * (15f / 19f), center = center)
    drawCircle(color = Color(0xFF378ADD), radius = radius * (11f / 19f), center = center)
    drawCircle(color = Color(0xFFD14848), radius = radius * (7f  / 19f), center = center)
    drawCircle(color = Color(0xFFFFD500), radius = radius * (2.5f / 19f), center = center)
}

// ────────────────────────────────────────────────────────────────────────────
//  Attempt pips — по одному кружку на попытку окна (Alex 25.08, замена
//  пучка стрел). Сыгранные — цвет результата, старейшая слева/сверху;
//  несыгранные — пустые колечки. Окно > 33 (индульгенция 66/99) складируется
//  РЯД НА РЯД по 33 в каждом, ряды растут вниз (первый ряд — первые 33
//  попытки окна, последний — текущие/актуальные), каждый второй ряд смещён
//  на пол-ячейки, чтобы кружок сидел НАД ПРОМЕЖУТКОМ между кружками ряда
//  снизу, а не строго над ними (Alex: «точка над пространством между
//  точками»). Компактный шаг между рядами — 25.08.
// ────────────────────────────────────────────────────────────────────────────

private const val PIPS_COLS = 33

@Composable
private fun AttemptPipsRow(
    total   : Int,
    colors  : List<Color>,
    modifier: Modifier = Modifier
) {
    val emptyColor = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.43f)
    val rows    = if (total <= 0) 0 else ((total - 1) / PIPS_COLS) + 1
    val rowH    = 5.5.dp
    val rowGap  = 2.dp
    Canvas(
        modifier = modifier.height(if (rows <= 0) 0.dp else rowH * rows + rowGap * (rows - 1))
    ) {
        if (total <= 0) return@Canvas
        // Эскиз 2026-09-21: шаг 10 dp (33 пипса = 330 dp), ряд по центру панели;
        // на более узком экране ячейка сжимается до width/33.
        val cell = minOf(10.dp.toPx(), size.width / PIPS_COLS)
        val leftPad = (size.width - cell * PIPS_COLS) / 2f
        // Диаметр кружка — доля ячейки, зажат в разумных пределах: маленький,
        // но не исчезает; ряд не раздувается крупнее видимого пипса.
        val d  = (cell * 0.55f).coerceIn(1.8.dp.toPx(), 5.5.dp.toPx())
        val r  = d / 2f
        val rowHPx   = rowH.toPx()
        val rowGapPx = rowGap.toPx()
        // Несыгранные — маленькие ПУСТЫЕ колечки (обводка, не заливка),
        // Alex 25.08: «сами попытки (не реализованные) должны быть с
        // дырочками внутри». Сыгранные — сплошные кружки цвета результата.
        val ringStroke = (r * 0.45f).coerceAtLeast(0.6.dp.toPx())
        for (row in 0 until rows) {
            val rowStart  = row * PIPS_COLS
            val rowCount  = minOf(PIPS_COLS, total - rowStart)
            val rowOffset = if (row % 2 == 1) cell / 2f else 0f
            val cy = row * (rowHPx + rowGapPx) + rowHPx / 2f
            for (i in 0 until rowCount) {
                val idx = rowStart + i
                val cx  = leftPad + rowOffset + cell * i + cell / 2f
                val played = colors.getOrNull(idx)
                if (played != null) {
                    drawCircle(color = played, radius = r, center = Offset(cx, cy))
                } else {
                    drawCircle(
                        color  = emptyColor,
                        radius = (r - ringStroke / 2f).coerceAtLeast(0.5f),
                        center = Offset(cx, cy),
                        style  = Stroke(width = ringStroke)
                    )
                }
            }
        }
    }
}
