package online.playoftime.features.main.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import online.playoftime.ui_theme.helpYellowColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import online.playoftime.core.utils.tidyLineBreaks

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

// Тексты справки (Alex 2026-10-06): сначала смысловая сверка с кодом — что
// реально показывает и как считается каждая зона, — потом текст. Правило:
// справка говорит то, что считает программа; если расходятся, чинится
// программа. Разметка: каждая строка списка — страница бабла (листается
// «‹ 1/2 ›»), \n — новый абзац, **…** — жирное название элемента.
fun helpPages(topic: HelpTopic): List<String> = when (topic) {
    HelpTopic.QUEST_PANEL -> listOf(
        "**Quest** — your mission for this session.\n**Targets** — how many hits it needs. A target you hit turns into the spinning logo.\n**Overlapping targets** — the hits must come in a row, and a miss resets the streak. **Targets apart** — the hits may come anywhere in the session.",
        "**Circles** — your attempts: green = hit, red = early, blue = late. Empty circles are the attempts you have left.\n**Goal** (3.00) — the precision of the level. After a struggle the quest is eased and a tolerance appears: 3.00 ± 0.01.\n**Check mark** — the quest is taken."
    )
    HelpTopic.STOPWATCH -> listOf(
        "**Stopwatch.** Tap RUN, feel three seconds pass, tap STOP. The button reacts to the touch, not the release.\n**Result colour:** green — on the goal (in an eased quest, within the tolerance), red — stopped early, blue — late.",
        "**Bar above the digits** — a magnifying glass around 3.0: red edge early, blue edge late, green zone the tolerance. On auto scale it zooms in as you get closer.\n**Face** — smiles when the attempt was no worse than the previous one.",
        "**HOORAY!** — the quest is taken. **RESET** arms the next attempt.\n**Hint line** under the stopwatch names a pattern in your recent attempts — for example, stopping early again and again — and suggests what to adjust."
    )
    HelpTopic.LOG -> listOf(
        "**Attempt log** — today's attempts, grouped by session.\nEach row: the **number**, the **stopped time** and the **error bar** — its side is the direction (red early, blue late), its length the size. A hit shows a green dot instead.",
        "**Last column** — the Serenity the attempt earned. The newest row shows «SC»: its coins are counted when the next attempt lands.\n**Ovals** mark repeats: green — the same miss twice in a row, on either side; yellow — misses one step of precision apart. Switch them off in Settings.\n**Thumb up** on a session — its quest was taken."
    )
    HelpTopic.DISTRIBUTION -> listOf(
        "**Distribution** — where today's errors pile up.\nEach **column** counts the attempts with that error: red to the left = early, blue to the right = late. The further from the centre, the bigger the miss.\n**Green centre column** — the exact aim at this level's precision: 3.0, 3.00 or 3.000.",
        "**Dashed lines** — your typical early and late misses.\n**Total tries** — today's attempts. **Coin** — the Serenity they earned.\nA narrow pile around a tall centre means control; a pile shifted to one side shows your lean."
    )
    HelpTopic.GEOM_MEAN -> listOf(
        "**Geom. Mean** — your typical miss today.\n**Red** — the typical early error. **Blue** — the typical late error. **Middle number** — your overall lean: minus = you tend to stop early, plus = late.\nThe same three lines run through the distribution and the attempt log.",
        "It is a geometric mean, so a few wild misses sway it less than an ordinary average.\nRed and blue close to the centre mean a tight hand. The middle number near zero means you have found the serenity point."
    )
    HelpTopic.STAT_PERIOD -> listOf(
        "**Period** — what the cards below are counted for: the current **Session**, a **Day**, a calendar **Week**, a **Month** or **All time**.\nThe line under the buttons names it; the **‹ ›** arrows step to the previous or next one. A session that crossed midnight counts whole.",
        "**Gauge** on the left — today's balance of early and late stops.\n**Not filtered:** the sessions log at the bottom always shows your whole history, and **Practice** in the top card is counted over all time."
    )
    HelpTopic.STAT_SUMMARY -> listOf(
        "**Totals** for the selected period.\n**Play time** — time spent on the Play screen. **Attempts**, **Sessions** — how many. **Serenity coins** — earned in the period.\n**Wins/precise** — wins, eased quests included; after the slash, exact hits with no tolerance.",
        "**Pace of play** — attempts per minute of play.\n**Practice** — your average play time per day you played, over all time.\n**Face** on the right sums up the period. Tap it for the full report; it needs at least 30 attempts."
    )
    HelpTopic.STAT_CHANCE -> listOf(
        "**Chance of win** for the period.\n**Hit rate** — the share of attempts that won, eased quests included.\n**Attempts per win** — what one win costs you on average.",
        "**Lucky Index** — how much more often you hit exactly 3.000 than blind chance would. Chance here is one millisecond out of the spread of your results.\n1 = no better than chance, above 1 = skill. It appears after your first exact 3.000."
    )
    HelpTopic.STAT_GEOM -> listOf(
        "**Geometric mean ÷ spread**, with the extreme 3% of results cut off at each end.\n**Early** — your typical early miss. **Late** — your typical late miss. **Absolute** — your overall lean: minus = early, plus = late.\nA geometric mean is swayed less by a few wild misses than an ordinary average.",
        "**Factor after ÷** — your spread: a typical attempt lands between the mean ÷ factor and the mean × factor.\nThe closer it is to 1, the steadier your hand."
    )
    HelpTopic.STAT_RANGE -> listOf(
        "**Range of deviations.**\n**Top strip** — the period's attempts split into early (red), wins (green) and late (blue). The **beak** marks the serenity point, exactly 3.0.",
        "**Lower strip** — the range of your results, 97% on both sides: the extreme 3% is cut off at each end.\nIts **ends** are your furthest usual misses; the **colour boundary** shows how the range divides between early and late.",
        "**White ticks** — your most frequent miss on each side, written below as «0.020 × 6»: that miss, repeated 6 times.\nOnly misses repeated at least twice count."
    )
    HelpTopic.STAT_PACE -> listOf(
        "**Chart** — how your play changed over the period. Pick what it shows in the **list** at the top: Mean error, Faces, Lucky index, Hit rate or Pace of learning.\n**One bar:** session — one attempt as it is: hit or miss, an exact 3.000, its own error, its face; today — a block of attempts in a row; week — one sitting (play with no break over 10 minutes); month — a day; all time — an hour, a day or a month, depending on how long you have played.\n**Tap and hold** a bar to see its value.",
        "**Mean error** — the geometric mean of your early (red) and late (blue) errors, ms, the same as Geometric Mean below.\n**Faces** — how many happy (green) and sad (grey) faces you got: after each attempt the face smiles when you came closer than the attempt before or stayed as close, and turns sad when you went further. In a session: up — happy, down — sad, in the order of attempts.",
        "**Pace of learning** — how good your attempts were: the average share of the maximum Serenity an attempt can earn at its level, so levels compare fairly. **SC/att** — the average Serenity per attempt.\n**Hit rate** — the share of attempts that won.\n**Lucky index** — how often you hit exactly 3.000, compared with chance."
    )
    HelpTopic.STAT_SESSIONS -> listOf(
        "**Sessions log** — your whole history, whatever period is selected: year, month, week, day, session. Tap a row to unfold it.\n**lucky** — the Lucky Index of the row («no luck» = no exact 3.000 in the session). **mean** — typical early / late miss, ms. **att4win** — attempts per win.",
        "**Under each row:** play time · win %. Above the day, both are averages over the days you played.\n**Thumb up:** session — its quest was taken (eased too); day — at least one quest was taken; week, month, year — at least half of the days played are up.\n**👑** — a session whose quest was taken."
    )
    HelpTopic.RANKS_NAME -> listOf(
        "**Your name** in the leaderboard.\nType a name (up to 24 characters) and tap **Save**. Until you save, the automatic name is used; saving an empty field gives you a new automatic name."
    )
    HelpTopic.RANKS_TABLE -> listOf(
        "**Leaderboard** — every player who has played.\n**Lvl** — the quest reached (level.task). **Coin** — all Serenity earned. **Acc%** — the share of winning attempts. **Luck** — how much more often than chance they hit exactly 3.000; above 1 = skill.",
        "Tap a **column header** to sort by it, best first; tap it again to reverse. The highlighted column is the current sort.\nYour own row is **orange**. Tap **another player's row** to compare yourself with them."
    )
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

// Заливка бабла — общий helpYellowColor с прежней прозрачностью 0xF2.
// Раньше цвет был записан здесь числом, и крестик выхода из режима красился
// оранжевым акцентом, то есть справка состояла из двух разных цветов.
private val HelpBubbleBg     = helpYellowColor.copy(alpha = 0xF2 / 255f)
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
                    pages     = helpPages(sel),
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

/** «**жирное**» → жирный span; остальное как есть. */
private fun helpMarkup(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        else append(part)
    }
}

/**
 * Бабл справки: абзацы с жирными названиями элементов, длинный текст —
 * страницами с переключателем «‹ 1/2 ›» (Alex 2026-10-06: «сделай текст
 * более читабельным с выделениями, слишком большой разбивай на куски»).
 */
@Composable
private fun HelpBubbleCard(pages: List<String>, onDismiss: () -> Unit) {
    var page by remember(pages) { mutableStateOf(0) }
    val bodyStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = HelpTextColor)
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
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pages[page].split("\n").forEach { paragraph ->
                        BasicText(text = helpMarkup(tidyLineBreaks(paragraph)), style = bodyStyle)
                    }
                }
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
            if (pages.size > 1) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    HelpPageArrow("‹", enabled = page > 0) { page-- }
                    BasicText(
                        text     = "${page + 1} / ${pages.size}",
                        modifier = Modifier.padding(horizontal = 10.dp),
                        style    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HelpTextColor)
                    )
                    HelpPageArrow("›", enabled = page < pages.size - 1) { page++ }
                }
            }
        }
    }
}

@Composable
private fun HelpPageArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, HelpTextColor.copy(alpha = if (enabled) 1f else 0.25f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text  = symbol,
            style = TextStyle(
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = HelpTextColor.copy(alpha = if (enabled) 1f else 0.25f)
            )
        )
    }
}
