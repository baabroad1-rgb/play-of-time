package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import ru.iflex.timemvp.core.utils.tidyLineBreaks

/**
 * Тап-зоны интерактивной справки главного экрана. Каждая зона регистрирует
 * свой Rect (в root-координатах) через Modifier.helpZone; оверлей хит-тестит
 * тапы по этим прямоугольникам и показывает бабл с описанием.
 */
enum class HelpTopic {
    BALANCE, HIT_RATE, HAND, COINS,
    QUEST_PANEL, STOPWATCH, LOG,
    DISTRIBUTION, GEOM_MEAN,
    // Топики аналитического экрана (StatScreen)
    STAT_PERIOD, STAT_SUMMARY, STAT_CHANCE, STAT_GEOM,
    STAT_RANGE, STAT_PACE, STAT_SESSIONS
}

fun helpText(topic: HelpTopic): String = when (topic) {
    HelpTopic.BALANCE ->
        "Balance. The gauge shows your bias: red side = stopping early, " +
        "blue side = stopping late. The needle at the top means your early and " +
        "late errors cancel out — your inner clock runs true."
    HelpTopic.HIT_RATE ->
        "True three hit rate. The share of your attempts that landed exactly " +
        "on the aim time. The higher it grows, the more often your feel of " +
        "time is spot on."
    HelpTopic.HAND ->
        "Hand. Marks which hand you play with — switch it when you change " +
        "hands. Each hand trains its own feel of time."
    HelpTopic.COINS ->
        "Serenity coins. Earned on every attempt — the closer to the aim " +
        "time, the more, and higher levels pay far more. The green number is " +
        "today's gain. Coins reward steadiness, not luck."
    HelpTopic.QUEST_PANEL ->
        "Quest. One target per hit the quest requires; a check = landed. " +
        "Targets standing apart — hits may come anywhere in the series; " +
        "overlapping — they must come in a row. Arrows are attempts left. " +
        "This is your current mission: land the threes before the arrows " +
        "run out."
    HelpTopic.STOPWATCH ->
        "Stopwatch. Tap RUN, feel the aim time pass, tap STOP. Green result " +
        "= hit, red = stopped early, blue = late; RESET arms the next " +
        "attempt. The face smiles when the attempt was no worse than the " +
        "previous one."
    HelpTopic.LOG ->
        "Attempt log. One row per attempt: number, stopped time, an error " +
        "bar — side shows the direction, length the size — and the coins " +
        "earned. Read it like a diary: watch your errors shrink and change " +
        "side attempt by attempt."
    HelpTopic.DISTRIBUTION ->
        "Distribution. Columns pile up your errors: red left = early stops, " +
        "blue right = late, green center = clean threes. Σtries and ΣSC " +
        "total the attempts shown. A tall green center with a narrow pile " +
        "means mastery."
    HelpTopic.GEOM_MEAN ->
        "Geom. mean. Your typical early error, typical late error, and " +
        "Absolute — the overall bias: negative = leaning early, positive = " +
        "late. Absolute near zero means you have found the serenity point."
    HelpTopic.STAT_PERIOD ->
        "Period. Sets what every card below covers: the current session, " +
        "today, this month, or all time. Switch periods to compare today's " +
        "form with your whole journey."
    HelpTopic.STAT_SUMMARY ->
        "Totals. Your whole journey in one card: Serenity coins earned, pure " +
        "play time, sessions, attempts and clean-three wins, plus your pace " +
        "of play and daily practice."
    HelpTopic.STAT_CHANCE ->
        "Chance of win. Hit rate is the share of attempts that landed a " +
        "clean three; attempts per win says how many tries one three costs " +
        "you. Lucky Index compares your hits with pure chance — above 1 " +
        "means it is skill, not luck."
    HelpTopic.STAT_GEOM ->
        "Geometric mean. Your typical early error, typical late error, and " +
        "Absolute — the overall bias: negative = leaning early, positive = " +
        "late. The % is the spread: the smaller, the steadier your hand."
    HelpTopic.STAT_RANGE ->
        "Range of deviations. The strip spans where 97% of your results " +
        "fall: red = early side, blue = late. The counters split your " +
        "attempts into early / clean / late, and Modes show your most " +
        "repeated miss on each side."
    HelpTopic.STAT_PACE ->
        "Pace of learning. How fast your Serenity income grows per attempt. " +
        "A positive number means every next attempt earns you more than the " +
        "last — you are learning."
    HelpTopic.STAT_SESSIONS ->
        "Sessions. Your history as a tree: year, month, day, session — tap " +
        "any row to unfold it. Columns: lucky (skill vs chance), mean " +
        "(typical early / late error, ms), att4win (attempts per clean " +
        "three)."
}

/** Регистрирует layout-границы элемента как тап-зону справки. */
fun Modifier.helpZone(zones: MutableMap<HelpTopic, Rect>, topic: HelpTopic): Modifier =
    this.onGloballyPositioned { zones[topic] = it.boundsInRoot() }

/**
 * То же, но снимает регистрацию, когда элемент уходит из композиции.
 * Обязательно для зон внутри LazyColumn (аналитика): карточка, уехавшая за
 * экран, диспозится и перестаёт обновлять свой Rect — без снятия регистрации
 * её устаревший прямоугольник ловил бы тапы, предназначенные другим карточкам.
 */
@Composable
fun Modifier.helpZoneDisposable(zones: MutableMap<HelpTopic, Rect>, topic: HelpTopic): Modifier {
    androidx.compose.runtime.DisposableEffect(zones, topic) {
        onDispose { zones.remove(topic) }
    }
    return this.onGloballyPositioned { zones[topic] = it.boundsInRoot() }
}

private val HelpBubbleBg     = Color(0xF2F5C02E)
private val HelpBubbleBorder = Color(0xFFFFD35E)
private val HelpTextColor    = Color(0xFF2A1E00)

/**
 * Полноэкранный оверлей режима подсказок. Перехватывает ВСЕ тапы:
 *  • тап по "?" — выход из режима;
 *  • тап по зарегистрированной зоне — бабл с описанием возле неё;
 *  • тап мимо зон — закрывает текущий бабл.
 * Пока бабл не выбран — сверху баннер-приглашение.
 */
@Composable
fun HelpTapOverlay(
    zones: Map<HelpTopic, Rect>,
    questionRect: Rect?,
    selected: HelpTopic?,
    onSelect: (HelpTopic?) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .onSizeChanged { overlaySize = it }
            .pointerInput(zones, questionRect) {
                detectTapGestures { pos ->
                    val p = pos + origin
                    if (questionRect?.contains(p) == true) {
                        onExit()
                        return@detectTapGestures
                    }
                    // Наименьшая содержащая зона побеждает (на случай вложенности)
                    val hit = zones.entries
                        .filter { it.value.contains(p) }
                        .minByOrNull { it.value.width * it.value.height }
                        ?.key
                    onSelect(hit)
                }
            }
    ) {
        val sel = selected
        val rect = sel?.let { zones[it] }

        if (rect != null) {
            val local = rect.translate(-origin)

            // Жёлтая рамка вокруг тапнутого элемента
            Canvas(modifier = Modifier.matchParentSize()) {
                val pad = 4.dp.toPx()
                drawRoundRect(
                    color        = HelpBubbleBorder,
                    topLeft      = Offset(local.left - pad, local.top - pad),
                    size         = Size(local.width + pad * 2, local.height + pad * 2),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style        = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Бабл: под зоной если она в верхней половине экрана, иначе над ней
            var bubbleH by remember(sel) { mutableStateOf(0) }
            val density = LocalDensity.current
            val gapPx = with(density) { 8.dp.toPx() }
            val below = local.center.y < overlaySize.height / 2f
            val yPx = if (below) local.bottom + gapPx
                      else local.top - gapPx - bubbleH

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, yPx.roundToInt()) }
                    .padding(horizontal = 12.dp)
                    .onSizeChanged { bubbleH = it.height }
                    // Не мигать над зоной, пока высота не измерена
                    .alpha(if (!below && bubbleH == 0) 0f else 1f)
            ) {
                val sidePadPx = with(density) { 12.dp.toPx() }
                if (below) HelpBeak(up = true, anchorX = local.center.x - sidePadPx)
                HelpBubbleCard(
                    text      = helpText(sel),
                    onDismiss = { onSelect(null) }
                )
                if (!below) HelpBeak(up = false, anchorX = local.center.x - sidePadPx)
            }
        } else {
            // Баннер-приглашение
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HelpBubbleBg)
                    .border(2.dp, HelpBubbleBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                BasicText(
                    text  = tidyLineBreaks(
                        "Tap any element to see what it does. Tap ? again to exit."
                    ),
                    style = TextStyle(
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = HelpTextColor
                    )
                )
            }
        }
    }
}

/** Носик бабла — маленький треугольник, указывающий на тапнутый элемент. */
@Composable
private fun HelpBeak(up: Boolean, anchorX: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
    ) {
        val halfW = 7.dp.toPx()
        val cx = anchorX.coerceIn(halfW + 12.dp.toPx(), size.width - halfW - 12.dp.toPx())
        val path = Path().apply {
            if (up) {
                moveTo(cx, 0f)
                lineTo(cx - halfW, size.height)
                lineTo(cx + halfW, size.height)
            } else {
                moveTo(cx, size.height)
                lineTo(cx - halfW, 0f)
                lineTo(cx + halfW, 0f)
            }
            close()
        }
        drawPath(path, HelpBubbleBorder)
    }
}

@Composable
private fun HelpBubbleCard(text: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HelpBubbleBg)
            .border(2.dp, HelpBubbleBorder, RoundedCornerShape(12.dp))
            // Съедаем тапы по телу бабла, чтобы они не проваливались в хит-тест зон
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BasicText(
                text     = tidyLineBreaks(text),
                modifier = Modifier.weight(1f),
                style    = TextStyle(
                    fontSize   = 12.sp,
                    lineHeight = 17.sp,
                    color      = HelpTextColor
                )
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
                    .border(1.dp, HelpTextColor, CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text  = "×",
                    style = TextStyle(
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = HelpTextColor
                    )
                )
            }
        }
    }
}
