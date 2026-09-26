package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
    // Топики главного экрана. Верхняя строка (Balance / Hit rate / Hand / Coins)
    // убрана 2026-07-26: баланс переехал в пиктограмму Analytics нижнего меню,
    // hit rate и coins — на экран Analytics, рука — в Settings.
    QUEST_PANEL, STOPWATCH, LOG,
    DISTRIBUTION, GEOM_MEAN,
    // Топики аналитического экрана (StatScreen)
    STAT_PERIOD, STAT_SUMMARY, STAT_CHANCE, STAT_GEOM,
    STAT_RANGE, STAT_PACE, STAT_SESSIONS,
    // Топики экрана Ranks (LeaderboardScreen), Alex 2026-09-10.
    RANKS_NAME, RANKS_TABLE
}

fun helpText(topic: HelpTopic): String = when (topic) {
    HelpTopic.QUEST_PANEL ->
        "Quest. One target per hit the quest requires; a check = landed. " +
        "Targets standing apart — hits may come anywhere in the series; " +
        "overlapping — they must come in a row. Arrows are attempts left. " +
        "This is your current mission: land the threes before the arrows " +
        "run out."
    HelpTopic.STOPWATCH ->
        "Stopwatch. Tap RUN, feel the aim time pass, tap STOP. Green result " +
        "= hit, red = stopped early, blue = late; a win shows HOORAY!, then " +
        "RESET arms the next attempt. The bar above the digits zooms on the " +
        "aim (scale set in Settings). The face smiles when the attempt was no " +
        "worse than the previous one."
    HelpTopic.LOG ->
        "Attempt log. One row per attempt: number, stopped time, an error " +
        "bar — side shows the direction, length the size — and the coins " +
        "earned. A green hand-drawn oval rings two attempts with the same " +
        "miss (twins); a yellow one, misses one step apart. Read it like a " +
        "diary: watch your errors shrink and change side attempt by attempt."
    HelpTopic.DISTRIBUTION ->
        "Distribution. Columns pile up your errors: red left = early stops, " +
        "blue right = late. The single green center column is your clean " +
        "threes — only exactly 3.000, even in eased subquests. Σtries counts " +
        "the attempts shown and the coin total sums their Serenity. A tall " +
        "green center with a narrow pile means mastery."
    HelpTopic.GEOM_MEAN ->
        "Geom. mean. Three markers on the scale: your typical early error " +
        "(red, left), typical late error (blue, right), and the overall bias " +
        "(white) — left of centre = leaning early, right = late. The bias " +
        "near zero means you have found the serenity point."
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
        "Geometric mean of your timing errors, with the extreme 3% trimmed on " +
        "each side. Early = your typical early miss, Late = typical late miss, " +
        "Absolute = the overall bias (negative = leaning early, positive = " +
        "late). The spread factor works both ways: a typical attempt lands " +
        "between mean ÷ factor and mean × factor — the smaller the factor, the " +
        "steadier your hand."
    HelpTopic.STAT_RANGE ->
        "Range of deviations. The strip spans where 97% of your results " +
        "fall: red = early side, blue = late. The counters split your " +
        "attempts into early / clean / late, and Top-frequency error shows " +
        "your most repeated miss on each side."
    HelpTopic.STAT_PACE ->
        "Pace of learning. How fast your Serenity income grows per attempt. " +
        "A positive number means every next attempt earns you more than the " +
        "last — you are learning."
    HelpTopic.STAT_SESSIONS ->
        "Sessions. Your history as a tree: year, month, day, session — tap " +
        "any row to unfold it. Columns: lucky (skill vs chance, «no luck» if " +
        "no clean three yet), mean (typical early / late error, ms), att4win " +
        "(attempts per clean three). A thumb marks success: on a session — " +
        "the quest was taken (even an eased one); on a day — most sessions " +
        "that day succeeded."
    HelpTopic.RANKS_NAME ->
        "Your name. Shown next to your rank in the table below. Leave it " +
        "blank and keep the auto-assigned name, or type your own and tap " +
        "Save."
    HelpTopic.RANKS_TABLE ->
        "Leaderboard. Every player, ranked. Tap a column header to sort by " +
        "it — Lvl (quest progress), the coin (Serenity Coins), Acc% (hit " +
        "rate), or Luck (your hits compared with pure chance — above 1 " +
        "means it's skill, not luck). The highlighted column is the current " +
        "sort; your own row is picked out in orange."
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

/**
 * Как [helpZoneDisposable], но для зоны, растянутой на НЕСКОЛЬКО отдельных
 * composable'ов — например дерево сессий в аналитике, где каждая строка
 * (год/месяц/неделя/день/сессия) свой собственный LazyColumn item, а не
 * один общий Box (Alex 2026-09-10: "все нижнее дерево нужно сделать
 * кликабельным, а не только заголовок" — раньше зона STAT_SESSIONS
 * регистрировалась ТОЛЬКО на строке заголовка "SESSIONS LOG", и тап по
 * любой строке дерева НИЖЕ неё не попадал ни в одну зону вообще).
 *
 * Каждый вызов регистрирует СВОЙ кусок под уникальным [partKey] в общей
 * карте [parts]; итоговый Rect зоны в [zones] — объединение (union) всех
 * текущих кусков. Уход куска из композиции (строка свернулась / уехала за
 * экран LazyColumn'а) корректно пересчитывает union по оставшимся, а не
 * просто стирает всю зону целиком.
 */
@Composable
fun Modifier.helpZonePartDisposable(
    zones: MutableMap<HelpTopic, Rect>,
    topic: HelpTopic,
    parts: MutableMap<Any, Rect>,
    partKey: Any
): Modifier {
    fun recompute() {
        val rects = parts.values
        if (rects.isEmpty()) {
            zones.remove(topic)
        } else {
            // Ручной bounding-box вместо Rect.union(...) — не завязываемся на
            // точное имя/наличие API объединения прямоугольников.
            var l = Float.POSITIVE_INFINITY
            var t = Float.POSITIVE_INFINITY
            var r = Float.NEGATIVE_INFINITY
            var b = Float.NEGATIVE_INFINITY
            for (rect in rects) {
                if (rect.left   < l) l = rect.left
                if (rect.top    < t) t = rect.top
                if (rect.right  > r) r = rect.right
                if (rect.bottom > b) b = rect.bottom
            }
            zones[topic] = Rect(l, t, r, b)
        }
    }
    androidx.compose.runtime.DisposableEffect(parts, partKey) {
        onDispose {
            parts.remove(partKey)
            recompute()
        }
    }
    return this.onGloballyPositioned {
        parts[partKey] = it.boundsInRoot()
        recompute()
    }
}

private val HelpBubbleBg     = Color(0xF2F5C02E)
private val HelpBubbleBorder = Color(0xFFFFD35E)
private val HelpTextColor    = Color(0xFF2A1E00)

/**
 * Ловит тапы режима справки: тап по "?" выходит, тап по зарегистрированной
 * зоне выбирает её, тап мимо — закрывает текущий бабл. Any drag past touch
 * slop is treated as "not a tap" and never consumed.
 *
 * ВАЖНО (Alex 2026-09-10, второй заход — первая версия этого фикса НЕ
 * заработала на Analytics): touch-slop-проверка сама по себе недостаточна,
 * если этот modifier висит на ОТДЕЛЬНОМ Box-е, который является СОСЕДОМ
 * (а не предком) скроллящегося LazyColumn — тогда это два независимых
 * pointerInput-обработчика на параллельных ветках дерева, и Compose не
 * гарантирует, что дочерний LazyColumn вообще увидит те же raw-события
 * (или что наш `isConsumed`-чек увидит его consume в том же раунде).
 * Единственный НАДЁЖНЫЙ способ — повесить этот modifier на тот САМЫЙ Box,
 * который уже и так оборачивает scroll-контент как настоящий ПРЕДОК
 * (ancestor), а не как параллельный сосед. Тогда Compose's pass-порядок
 * (Main: изнутри-наружу) гарантирует, что LazyColumn — как потомок —
 * обработает Main-pass и, если решит скроллить, consume'ит его РАНЬШЕ,
 * чем очередь дойдёт до этого modifier'а на предке. Именно поэтому это
 * теперь `Modifier`-расширение, которое вызывающий код (MainScreen.kt,
 * StatScreen.kt) добавляет НА СВОЙ УЖЕ СУЩЕСТВУЮЩИЙ внешний Box — вместо
 * того чтобы жить внутри отдельного HelpTapOverlay-композабла.
 */
fun Modifier.helpTapGestures(
    zones: Map<HelpTopic, Rect>,
    questionRect: Rect?,
    origin: () -> Offset,
    onSelect: (HelpTopic?) -> Unit,
    onExit: () -> Unit
): Modifier = this.pointerInput(zones, questionRect) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPos = down.position
        var passedSlop = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!passedSlop &&
                (change.position - downPos).getDistance() > touchSlop
            ) {
                passedSlop = true
            }
            // Кто-то ниже (скролл) уже забрал жест — не мешаем.
            if (event.changes.any { it.isConsumed }) break
            if (change.changedToUpIgnoreConsumed()) {
                if (!passedSlop) {
                    val p = change.position + origin()
                    if (questionRect?.contains(p) == true) {
                        onExit()
                    } else {
                        // Наименьшая содержащая зона побеждает (вложенность)
                        val hit = zones.entries
                            .filter { it.value.contains(p) }
                            .minByOrNull { it.value.width * it.value.height }
                            ?.key
                        onSelect(hit)
                    }
                    change.consume()
                }
                break
            }
        }
    }
}

/**
 * Чисто визуальный слой режима подсказок — рамка вокруг выбранной зоны +
 * бабл с описанием (или баннер-приглашение, пока ничего не выбрано). БЕЗ
 * собственного pointerInput — хит-тестинг живёт в [helpTapGestures],
 * повешенном на внешний Box вызывающего экрана (см. его doc-comment).
 * `origin`/`overlaySize` — координаты того самого внешнего Box, измеренные
 * ОДИН РАЗ вызывающим кодом и переданные сюда.
 */
@Composable
fun HelpTapOverlayVisual(
    zones: Map<HelpTopic, Rect>,
    questionRect: Rect?,
    selected: HelpTopic?,
    onSelect: (HelpTopic?) -> Unit,
    origin: Offset,
    overlaySize: Size,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
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
        } else if (questionRect != null) {
            // Баннер-приглашение — привязан к иконке "?" с клювиком (Alex 2026-08-14).
            // Раньше сидел прибитым к левому верхнему углу оверлея и перекрывал
            // текст задания квеста, который начинается прямо под "?". Теперь
            // позиционируется ПОД иконкой, как обычный бабл выбранной зоны.
            val local = questionRect.translate(-origin)
            val density = LocalDensity.current
            val gapPx = with(density) { 8.dp.toPx() }
            val yPx = local.bottom + gapPx

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, yPx.roundToInt()) }
                    .padding(horizontal = 12.dp)
            ) {
                val sidePadPx = with(density) { 12.dp.toPx() }
                HelpBeak(up = true, anchorX = local.center.x - sidePadPx)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
