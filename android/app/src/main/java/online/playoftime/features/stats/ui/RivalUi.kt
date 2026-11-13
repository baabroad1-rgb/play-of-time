package online.playoftime.features.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import online.playoftime.features.stats.domain.entity.AnalyticsData
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.playoftime.features.rival.RivalEngine
import online.playoftime.ui_theme.accentColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ──────────────────────────────────────────────────────────────────────────────
//  Doppelgänger на экране аналитики (Alex 2026-11-08): переключатель You | AI
//  слева в строке даты и дневник соперника гармошкой. Макет —
//  tools/micro_player/mock_rival_screen.js (оранжевый «You», фиолетовый «AI»).
//  Строку гонки Алекс убрал: «строка гонки — не нужна, сделай просто более
//  явным переключение you — AI и не забудь про дневник».
// ──────────────────────────────────────────────────────────────────────────────

internal val rivalPurple      = Color(0xFF7C5CFF)
internal val rivalPurpleLight = Color(0xFFC4B5FD)
private val toggleBg          = Color(0xFF1A1A1A)
private val toggleBorder      = Color(0xFF333333)
private val diaryLine         = Color(0xFF2A2A2A)
private val diaryLabelGray    = Color(0xFF7F7F7F)

/**
 * Тонкий переключатель «You | AI» — 22 dp в высоту, ниже 28-dp клювиков
 * листания, поэтому строка даты не раздувается (Alex: «сделай поуже в высоту
 * саму кнопку, так чтобы не распидарасило расстояние для строки»).
 */
@Composable
internal fun WhoToggle(rival: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier, youName: String = "You") {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .height(22.dp)
            .clip(shape)
            .background(toggleBg)
            .border(1.dp, toggleBorder, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhoSegment(youName, on = !rival, onColor = youColor, twin = false, onText = Color(0xFF2A1E00)) { onSelect(false) }
        WhoSegment("AI", on = rival, onColor = rivalPurple, twin = true) { onSelect(true) }
    }
}

@Composable
private fun WhoSegment(label: String, on: Boolean, onColor: Color, twin: Boolean, onText: Color = Color.White, onClick: () -> Unit) {
    val fg = if (on) onText else Color.White.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) onColor else Color.Transparent)
            .clickable(enabled = !on) { onClick() }
            .padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PersonIcon(twin = twin, color = fg, modifier = Modifier.size(13.dp))
        BasicText(
            text  = label,
            style = TextStyle(color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/** Значок: человечек (You) или два — двойник (AI). */
@Composable
private fun PersonIcon(twin: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        fun person(cx: Float, c: Color, scale: Float) {
            val w = size.width * scale
            val h = size.height
            drawCircle(c, radius = w * 0.19f, center = Offset(cx, h * 0.32f))
            val body = Path().apply {
                moveTo(cx - w * 0.36f, h * 0.96f)
                cubicTo(cx - w * 0.34f, h * 0.56f, cx + w * 0.34f, h * 0.56f, cx + w * 0.36f, h * 0.96f)
                close()
            }
            drawPath(body, c)
        }
        if (twin) {
            person(size.width * 0.34f, color.copy(alpha = color.alpha * 0.55f), 0.8f)
            person(size.width * 0.66f, color, 0.8f)
        } else {
            person(size.width / 2f, color, 1f)
        }
    }
}

/**
 * Дневник Doppelgänger'а гармошкой по дням (Alex: «а сам дневник сделай тоже
 * гармошкой»): заголовок сворачивает всю карточку, внутри дни, новые сверху.
 * На день — ОДНА запись по итогам дня (Alex 08.11: «дневник не должен быть
 * слишком подробным. Не надо каждую попытку комментировать — максимум одна по
 * результатам дня»): запись языковой модели, если она есть, иначе запись,
 * которую движок собрал сам ([RivalEngine.DayDigest.text]). Самый свежий
 * день раскрыт по умолчанию: для него членство в [expandedDays] значит
 * «свернуть», для остальных — «раскрыть».
 *
 * [days] — дни выбранного периода, старые первыми.
 */
@Composable
internal fun RivalDiaryCard(
    days         : List<RivalEngine.DayDigest>,
    questLabel   : String,
    periodLabel  : String,
    expanded     : Boolean,
    expandedDays : Set<String>,
    onToggle     : () -> Unit,
    onToggleDay  : (String) -> Unit,
    llm          : Map<String, RivalEngine.LlmEntry> = emptyMap(),
    writing      : Set<String> = emptySet(),
    onRequest    : (String, Boolean) -> Unit = { _, _ -> },
) {
    val titleStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = rivalPurpleLight)
    val grayStyle  = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = diaryLabelGray)
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BasicText(text = (if (expanded) "▾ " else "▸ ") + "${RivalEngine.NAME}’s diary", style = titleStyle)
            BasicText(
                text  = (if (questLabel.isNotEmpty()) "quest $questLabel  " else "") + periodLabel,
                style = grayStyle
            )
        }
        if (!expanded) return@Column

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            if (days.isEmpty()) {
                BasicText(
                    text     = "Nothing for this period yet. He writes one entry for each day he plays.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style    = TextStyle(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.6f))
                )
                return@Column
            }
            val dayFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH)
            val todayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val ordered = days.sortedByDescending { it.key }
            val newest = ordered.first().key
            var anyWritten = false
            ordered.forEachIndexed { i, d ->
                val key = d.key
                val open = (key in expandedDays) != (key == newest)
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(diaryLine))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDay(key) }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    BasicText(
                        text  = (if (open) "▾ " else "▸ ") + dayFmt.format(Date(d.startMs)),
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                                          color = Color.White.copy(alpha = 0.86f))
                    )
                    BasicText(
                        text  = "${d.attempts} " + if (d.attempts == 1) "attempt" else "attempts",
                        style = TextStyle(fontSize = 11.5.sp, color = diaryLabelGray)
                    )
                }
                if (open) {
                    // Запись языковой модели просится, только когда день открыт —
                    // человек зашёл почитать. Пока её нет (или сервер модели не
                    // отвечает — до переезда хостинга так и будет), стоит запись
                    // движка: одна на день, по итогам дня.
                    val written = llm[key]
                    if (d.attempts >= RivalEngine.MIN_ATTEMPTS_FOR_ENTRY) {
                        androidx.compose.runtime.LaunchedEffect(key) {
                            if (written == null || key == todayKey) onRequest(key, false)
                        }
                    }
                    if (written != null) anyWritten = true
                    BasicText(
                        text     = written?.entry ?: d.text,
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp, bottom = 10.dp),
                        style    = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp,
                                             color = Color.White.copy(alpha = 0.92f))
                    )
                    if (key in writing) DelayedWriting(
                        if (written == null) "${RivalEngine.NAME} is writing…" else "Rewriting today…")
                }
            }
            if (anyWritten) {
                val model = llm.values.maxByOrNull { it.at }?.model.orEmpty()
                BasicText(
                    text     = "Entries are written by ${modelCredit(model)} from his own numbers.",
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    style    = TextStyle(fontSize = 10.5.sp, color = diaryLabelGray)
                )
            }
        }
    }
}

/**
 * Кто он такой — в самом верху экрана в режиме AI, свёрнуто (Alex 08.11:
 * «надо как-то прокомментировать, что за доппельгангер, откуда взялся,
 * указать автора модели… не надо про мою идею писать, дай только на других
 * ссылки. А текст про него в самый верх перенеси»). По образцу подписи к
 * мордам «Based on Chernoff faces (Herman Chernoff, 1973)».
 */
@Composable
internal fun RivalAboutCard(open: Boolean, onToggle: () -> Unit, diaryModel: String?) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text     = (if (open) "▾ " else "▸ ") + "Who is ${RivalEngine.NAME}?",
                modifier = Modifier.clickable { onToggle() }.padding(vertical = 4.dp),
                style    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = rivalPurpleLight)
            )
        }
        if (open) {
            val body = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.85f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(ABOUT_WHO, style = body)
                BasicText(ABOUT_HOW, style = body)
                BasicText(
                    ABOUT_CREDITS + (diaryModel?.let { " Diary entries: ${modelCredit(it)}." } ?: ""),
                    style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = diaryLabelGray)
                )
            }
        }
    }
}

internal const val RIVAL_ABOUT_KEY = "about"

private const val ABOUT_WHO =
    "Doppelgänger (German for “double-goer”) is an artificial player — your double in this game. " +
    "He started knowing nothing and caught up with you attempt for attempt: for every attempt you make, " +
    "he makes one of his own, and he goes through the quests at his own pace."

private const val ABOUT_HOW =
    "He doesn’t see his time — only the counter and its colour, like you. His hand has its own bias and " +
    "spread and gets steadier with practice. His head learns how much to correct after a miss the way a " +
    "fruit fly learns: a small copy of the fly’s learning centre, the mushroom body — two thousand Kenyon " +
    "cells and a dopamine signal that rewards a good correction. Nobody tells him the right answer."

private const val ABOUT_CREDITS =
    "Learning scheme after the mushroom body of Drosophila (Aso et al., eLife 2014) " +
    "and the fly brain wiring map FlyWire (Dorkenwald et al., Nature 2024)."

/** "anthropic/claude-haiku-5.5" → "Claude Haiku (Anthropic)"; otherwise the id as is. */
private fun modelCredit(id: String): String = when {
    id.contains("claude-haiku") -> "Claude Haiku (Anthropic)"
    id.contains("claude-sonnet") -> "Claude Sonnet (Anthropic)"
    id.contains("deepseek") -> "DeepSeek"
    id.isBlank() -> "a language model"
    else -> id
}

/** «Пишет…» — только если ответ идёт дольше 1,5 с. */
@Composable
private fun DelayedWriting(text: String) {
    var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        show = true
    }
    if (show) DiaryStatus(text, spinner = true)
}

/** Строка состояния дня: «пишет…» со спиннером. */
@Composable
private fun DiaryStatus(text: String, spinner: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(start = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (spinner) androidx.compose.material3.CircularProgressIndicator(
            modifier    = Modifier.size(12.dp),
            color       = rivalPurpleLight,
            strokeWidth = 1.5.dp
        )
        BasicText(text = text, style = TextStyle(fontSize = 12.5.sp, color = rivalPurpleLight))
    }
}

/**
 * Призыв сравнить (Alex 08.11: «может быть добавить колл ту экшн типа „сравни,
 * кто лучше“»). Без цифр: строку гонки Алекс убрал раньше («строка гонки не
 * нужна») — кнопка только переключает экран, сравнивает игрок сам. В режиме
 * You — плашка наверху (тап → AI). Кнопку в строке «Who is Doppelgänger?» Алекс
 * убрал 11.11 («убери кнопку сравнить в строчке доппельгангер»).
 */
@Composable
internal fun RivalCompareBanner(onClick: () -> Unit, youName: String? = null) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, rivalPurple.copy(alpha = 0.7f), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PersonIcon(twin = true, color = rivalPurpleLight, modifier = Modifier.size(16.dp))
        BasicText(
            text     = if (youName != null) "$youName, your double plays one attempt for each of yours — who’s better?"
                       else "Your double plays one attempt for each of yours — who’s better?",
            modifier = Modifier.weight(1f),
            style    = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Color.White.copy(alpha = 0.85f))
        )
        BasicText(
            text  = "Compare ⇄",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = rivalPurpleLight)
        )
    }
}


// ──────────────────────────────────────────────────────────────────────────────
//  Экран AI — только сравнения (Alex 11.11.2026: «на экране AI выводить только
//  сравнения you/ai; в явном виде указать текущий уровень; вместо всех графиков
//  — сравнительный график скорости обучения пользователя и „мухи“… сделай
//  контент почти такой, как в аналитикс, только со сравнением»).
// ──────────────────────────────────────────────────────────────────────────────

// «You» — жёлтый фона About, не оранжевый: тот слишком нагружен (Alex 11.11).
private val youColor = Color(0xFFF5C02E)
private val aiColor  = Color(0xFFA78BFA)
private val compLabel = Color(0xFF9A9A9A)

@Composable
private fun CompTitle(title: String, period: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(title, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = diaryLabelGray))
        if (period.isNotEmpty()) BasicText(period, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = diaryLabelGray))
    }
}

@Composable
private fun CompCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
private fun CompHeader(you: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(Modifier.weight(1.3f))
        BasicText(you, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = youColor, textAlign = androidx.compose.ui.text.style.TextAlign.End))
        BasicText(RivalEngine.NAME, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = aiColor, textAlign = androidx.compose.ui.text.style.TextAlign.End))
    }
}

/** better: +1 — у игрока лучше, −1 — у соперника, 0 — не сравниваем. */
@Composable
private fun CompRow(label: String, you: String, ai: String, better: Int = 0) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF262626)))
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, modifier = Modifier.weight(1.3f), style = TextStyle(fontSize = 12.sp, color = compLabel))
        fun st(b: Boolean) = TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontWeight = if (b) FontWeight.Bold else FontWeight.Normal,
            color = if (b) Color.White else Color.White.copy(alpha = 0.72f))
        BasicText(groupDigits(you), modifier = Modifier.weight(1f), style = st(better > 0))
        BasicText(groupDigits(ai), modifier = Modifier.weight(1f), style = st(better < 0))
    }
}

private fun cmpHigher(a: Double, b: Double) = if (a > b) 1 else if (b > a) -1 else 0
private fun cmpLower(a: Double, b: Double) = if (a < b) 1 else if (b < a) -1 else 0
private fun ms(v: Double) = if (v == 0.0) "—" else "${kotlin.math.round(v).toLong()} ms"
private fun dur(msTotal: Long): String {
    val m = msTotal / 60_000
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
private fun precisionOf(level: Char) = when (level) { '1' -> "tenths"; '2' -> "hundredths"; '3' -> "thousandths"; else -> "" }

@Composable
internal fun RivalCompareSection(
    you: AnalyticsData?, ai: AnalyticsData, youName: String,
    playerQuest: String, rivalQuest: String,
    playerBadge: Pair<String, String> = "" to "", rivalBadge: Pair<String, String> = "" to "",
    curveYou: List<Pair<Long, Float>>, curveAi: List<Pair<Long, Float>>, periodLabel: String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ── уровень — в явном виде ────────────────────────────────────────
        CompTitle("Level now")
        CompCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                // Крупно — квест с точностью до индульгенции и его условие;
                // уровень — мелко под ним (Alex 11.11.2026).
                listOf(Triple(youName, playerQuest to playerBadge, youColor),
                       Triple(RivalEngine.NAME, rivalQuest to rivalBadge, aiColor)).forEach { (n, qb, c) ->
                    val (q, badge) = qb
                    Column(Modifier.weight(1f)) {
                        BasicText(n, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c))
                        BasicText(badge.first.ifEmpty { "Quest $q" }, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        if (badge.second.isNotEmpty()) BasicText(badge.second, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f)))
                        BasicText("Level ${q.firstOrNull() ?: '—'} · ${precisionOf(q.firstOrNull() ?: ' ')}",
                            modifier = Modifier.padding(top = 2.dp),
                            style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)))
                    }
                }
            }
        }

        // ── скорость обучения: лесенка индульгенций по попыткам ──────────
        // Alex 12.11.2026: «по вертикали откладывать уровни с точностью до
        // индульгенции… самая верхняя линия win». Pace-график не прижился.
        CompTitle("Speed of learning", periodLabel)
        CompCard {
            LevelTimeChart(curveYou, curveAi, youName)
            BasicText(
                "Up — quest and its sub-quests: 2.3 is the quest itself, 2.3.1, 2.3.2… below it are its eased steps. Across — time of the selected period.",
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.8f))
            )
        }

        if (you == null) return@Column
        // ── как в аналитике, только два столбца ──────────────────────────
        CompTitle("Summary", periodLabel)
        CompCard {
            CompHeader(youName)
            CompRow("Attempts", "${you.periodAttempts}", "${ai.periodAttempts}")
            CompRow("Sessions", "${you.periodSessionsCount}", "${ai.periodSessionsCount}")
            CompRow("Play time", dur(you.periodPlayTimeMs), dur(ai.periodPlayTimeMs))
            CompRow("Serenity coins", "%.2f".format(you.periodScm), "%.2f".format(ai.periodScm), cmpHigher(you.periodScm, ai.periodScm))
            CompRow("Wins / precise", "${you.periodWins} / ${you.periodPrecise}", "${ai.periodWins} / ${ai.periodPrecise}",
                cmpHigher(you.periodPrecise.toDouble(), ai.periodPrecise.toDouble()))
        }
        CompTitle("Chance of Win", periodLabel)
        CompCard {
            CompHeader(youName)
            CompRow("Hit rate", "%.1f%%".format(you.trueThreeHitRatePct), "%.1f%%".format(ai.trueThreeHitRatePct),
                cmpHigher(you.trueThreeHitRatePct, ai.trueThreeHitRatePct))
            CompRow("Attempts per win",
                if (you.attemptsPerWin > 0) "%.1f".format(you.attemptsPerWin) else "—",
                if (ai.attemptsPerWin > 0) "%.1f".format(ai.attemptsPerWin) else "—",
                if (you.attemptsPerWin > 0 && ai.attemptsPerWin > 0) cmpLower(you.attemptsPerWin, ai.attemptsPerWin) else 0)
            CompRow("Lucky Index",
                if (you.luckyIndex > 0) "%.2f".format(you.luckyIndex) else "—",
                if (ai.luckyIndex > 0) "%.2f".format(ai.luckyIndex) else "—",
                cmpHigher(you.luckyIndex, ai.luckyIndex))
        }
        CompTitle("Geometric Mean ÷ Spread", periodLabel)
        CompCard {
            CompHeader(youName)
            CompRow("Early", ms(-you.deviationEarly.medianMs), ms(-ai.deviationEarly.medianMs),
                cmpLower(kotlin.math.abs(you.deviationEarly.medianMs), kotlin.math.abs(ai.deviationEarly.medianMs)))
            CompRow("Absolute", ms(you.deviationAll.medianMs), ms(ai.deviationAll.medianMs),
                cmpLower(you.deviationAll.medianMs, ai.deviationAll.medianMs))
            CompRow("Late", ms(you.deviationLate.medianMs), ms(ai.deviationLate.medianMs),
                cmpLower(kotlin.math.abs(you.deviationLate.medianMs), kotlin.math.abs(ai.deviationLate.medianMs)))
        }
        CompTitle("Range of deviations", periodLabel)
        CompCard {
            CompHeader(youName)
            CompRow("Early edge", ms(you.rangeEarlyMs), ms(ai.rangeEarlyMs),
                cmpLower(kotlin.math.abs(you.rangeEarlyMs), kotlin.math.abs(ai.rangeEarlyMs)))
            CompRow("Late edge", ms(you.rangeLateMs), ms(ai.rangeLateMs),
                cmpLower(kotlin.math.abs(you.rangeLateMs), kotlin.math.abs(ai.rangeLateMs)))
            CompRow("Early / late misses", "${you.earlyCount} / ${you.lateCount}", "${ai.earlyCount} / ${ai.lateCount}")
        }
    }
}

/**
 * Один график (Alex 12.11.2026: «1. один; 2. перемещение по уровням включая
 * подуровни; 4. по вертикали уровень, по горизонтали время, в зависимости от
 * фильтра, однозначно читается»). Y — квест: 1.1 … 3.5 по порядку, ступени
 * индульгенций — тонкие линии под своим квестом (−0.15 за ступень). X — время
 * попыток выбранного периода; подписи — часы, дни или месяцы по длине периода.
 * Линия ступенчатая: уровень держится до следующей попытки.
 */
@Composable
private fun LevelTimeChart(you: List<Pair<Long, Float>>, ai: List<Pair<Long, Float>>, youName: String) {
    val all = you + ai
    if (all.size < 2) {
        BasicText("No attempts in this period.", style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        BasicText("■ $youName", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = youColor))
        BasicText("■ ${RivalEngine.NAME}", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = aiColor))
    }
    val t0 = all.minOf { it.first }; val t1 = all.maxOf { it.first }.coerceAtLeast(t0 + 60_000)
    // Строки по вертикали — каждый подквест отдельно, снизу вверх:
    // 1.1.2, 1.1.1, 1.1, 1.2.1, 1.2 … (Alex 12.11.2026: «по вертикали
    // детальность с подквестами, подпиши цифрой каждый подквест»).
    val qs = online.playoftime.core.utils.questList
    val codes = all.map { it.second.toInt() }
    val qMin = codes.minOf { it / 100 }; val qMax = codes.maxOf { it / 100 }
    val maxSub = codes.groupBy { it / 100 }.mapValues { (_, l) -> l.maxOf { it % 100 } }
    val rowsList = (qMin..qMax).flatMap { q -> ((maxSub[q] ?: 0) downTo 0).map { sub -> q * 100 + sub } }
    val rowOf = rowsList.withIndex().associate { it.value to it.index }
    fun label(code: Int): String {
        val q = qs.getOrNull(code / 100) ?: return ""
        return "${q.levelId}.${q.taskId}" + if (code % 100 > 0) ".${code % 100}" else ""
    }
    val n = rowsList.size
    val span = t1 - t0
    val fmt = java.text.SimpleDateFormat(when {
        span < 36L * 3600_000 -> "HH:mm"
        span < 75L * 86400_000 -> "d MMM"
        else -> "MMM yy"
    }, java.util.Locale.ENGLISH)
    val paint = remember {
        android.graphics.Paint().apply { color = android.graphics.Color.rgb(235, 235, 235); textSize = 30f; isFakeBoldText = true; isAntiAlias = true }
    }
    val subPaint = remember {
        android.graphics.Paint().apply { color = android.graphics.Color.rgb(170, 170, 170); textSize = 26f; isAntiAlias = true }
    }
    val rowDp = if (n <= 6) 34 else if (n <= 14) 24 else 18
    Canvas(Modifier.fillMaxWidth().height(((n - 1).coerceAtLeast(1) * rowDp + 70).dp)) {
        val left = 110f; val top = 18f
        val bottom = size.height - 46f
        val w = size.width - left - 12f
        fun x(t: Long) = left + w * (t - t0).toFloat() / (t1 - t0)
        fun y(code: Float) = bottom - (bottom - top) * (rowOf[code.toInt()] ?: 0) / (n - 1).coerceAtLeast(1)
        rowsList.forEach { code ->
            val yy = y(code.toFloat())
            val base = code % 100 == 0
            drawLine(if (base) Color(0xFF7A7A7A) else Color(0xFF353535), Offset(left, yy), Offset(left + w, yy), strokeWidth = if (base) 2.5f else 1.2f)
            drawContext.canvas.nativeCanvas.drawText(label(code), 4f, yy + 10f, if (base) paint else subPaint)
        }
        for (i in 0..3) {
            val t = t0 + (t1 - t0) * i / 3
            val lab = fmt.format(java.util.Date(t))
            val tw = paint.measureText(lab)
            val px = (x(t) - tw / 2).coerceIn(left - 20f, left + w - tw)
            drawLine(Color(0xFF444444), Offset(x(t), bottom), Offset(x(t), bottom + 8f), strokeWidth = 2f)
            drawContext.canvas.nativeCanvas.drawText(lab, px, size.height - 6f, paint)
        }
        // Полупрозрачные столбики (Alex 13.11.2026): период делится на равные
        // отрезки времени, высота столбика — подквест, где игрок был в конце
        // отрезка (без попыток — держится прежний). Оба поверх друг друга,
        // перекрытие видно по смешению цветов.
        val buckets = 30
        val bw = w / buckets
        fun bars(pts: List<Pair<Long, Float>>): List<Float?> {
            var last: Float? = null
            var j = 0
            return List(buckets) { b ->
                val end = t0 + (t1 - t0) * (b + 1) / buckets
                while (j < pts.size && pts[j].first <= end) { last = pts[j].second; j++ }
                last
            }
        }
        listOf(you to youColor, ai to aiColor).forEach { (pts, c) ->
            bars(pts).forEachIndexed { b, v ->
                if (v == null) return@forEachIndexed
                val yy = y(v)
                drawRect(c.copy(alpha = 0.45f), topLeft = Offset(left + b * bw + 2f, yy),
                    size = androidx.compose.ui.geometry.Size(bw - 4f, (bottom - yy).coerceAtLeast(3f)))
            }
        }
    }
}

/** Ступенчатые линии «квест к попытке» для обоих, полосы уровней (не используется с 12.11.2026). */
@Composable
private fun LearningChart(you: List<Int>, ai: List<Int>) {
    val n = maxOf(you.size, ai.size)
    if (n < 2) {
        BasicText("Not enough attempts yet.", style = TextStyle(fontSize = 12.sp, color = diaryLabelGray))
        return
    }
    val labelPaint = remember {
        android.graphics.Paint().apply { color = android.graphics.Color.rgb(110, 110, 110); textSize = 26f; isAntiAlias = true }
    }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val left = 52f
        val bottom = size.height - 30f
        val top = 8f
        val w = size.width - left - 8f
        fun x(i: Int, len: Int) = left + w * i / (len - 1).coerceAtLeast(1)
        fun y(k: Float) = top + (bottom - top) * (1f - k / 14f)
        listOf(Triple(0, 4, "L1"), Triple(5, 9, "L2"), Triple(10, 14, "L3")).forEachIndexed { j, (a, b, lab) ->
            drawRect(if (j % 2 == 0) Color(0xFF202020) else Color(0xFF262626),
                topLeft = Offset(left, y(b + 0.5f)), size = androidx.compose.ui.geometry.Size(w, y(a - 0.5f) - y(b + 0.5f)))
            drawContext.canvas.nativeCanvas.drawText(lab, 8f, (y(b + 0.5f) + y(a - 0.5f)) / 2 + 9f, labelPaint)
        }
        drawContext.canvas.nativeCanvas.drawText("0", left, size.height - 4f, labelPaint)
        val endLabel = "$n"
        drawContext.canvas.nativeCanvas.drawText("attempts →", left + w / 2 - 60f, size.height - 4f, labelPaint)
        drawContext.canvas.nativeCanvas.drawText(endLabel, left + w - labelPaint.measureText(endLabel), size.height - 4f, labelPaint)
        listOf(you to youColor, ai to aiColor).forEach { (pts, c) ->
            if (pts.size < 2) return@forEach
            val path = Path()
            pts.forEachIndexed { i, k ->
                val px = x(i, pts.size); val py = y(k.toFloat())
                if (i == 0) path.moveTo(px, py) else { path.lineTo(px, y(pts[i - 1].toFloat())); path.lineTo(px, py) }
            }
            drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
            drawCircle(c, 7f, Offset(x(pts.size - 1, pts.size), y(pts.last().toFloat())))
        }
    }
}

/** Недельная запись соперника внутри недели дерева сессий — свёрнута до тапа. */
@Composable
internal fun RivalWeekNote(text: String, expanded: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText(if (expanded) "▾" else "▸", style = TextStyle(fontSize = 12.sp, color = rivalPurpleLight))
            PersonIcon(twin = true, color = rivalPurpleLight, modifier = Modifier.size(13.dp))
            BasicText("${RivalEngine.NAME}’s week", style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = rivalPurpleLight))
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp)) {
                Box(Modifier.width(2.dp).height(1.dp))
                BasicText(
                    text = text,
                    modifier = Modifier.background(Color(0xFF17151F), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.9f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                )
            }
        }
    }
}


/**
 * Дневник на экране AI (Alex 11.11.2026: «дневник перенеси в экран ИИ; дай где-нибудь
 * в конце последние три записи с возможностью подгрузить ещё»). Записи недельные,
 * новые сверху; «Show 3 earlier weeks» открывает следующие три.
 * [notes] — подпись недели → запись, старые первыми.
 */
@Composable
internal fun RivalWeeklyDiary(notes: Map<String, String>) {
    var shown by androidx.compose.runtime.remember(notes.size) { androidx.compose.runtime.mutableStateOf(3) }
    val list = notes.entries.toList().asReversed()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompTitle("${RivalEngine.NAME}’s diary", "by week")
        CompCard {
            if (list.isEmpty()) {
                BasicText("Nothing yet — he writes once a week.", style = TextStyle(fontSize = 12.5.sp, color = diaryLabelGray))
            }
            list.take(shown).forEachIndexed { i, (label, text) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF262626)))
                BasicText(groupDigits(label), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = rivalPurpleLight))
                BasicText(groupDigits(text), modifier = Modifier.padding(bottom = 8.dp),
                    style = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.9f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif))
            }
            if (list.size > shown) {
                BasicText("Show ${minOf(3, list.size - shown)} earlier ${if (list.size - shown == 1) "week" else "weeks"} ▾",
                    modifier = Modifier.fillMaxWidth().clickable { shown += 3 }.padding(vertical = 10.dp),
                    style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = rivalPurpleLight,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}


/** Pace of learning обоих по одним отрезкам периода: эффективность 0..1, null — пусто. */
@Composable
private fun PaceCompareChart(you: List<Float?>, ai: List<Float?>, labels: List<String>) {
    val n = maxOf(you.size, ai.size)
    if (n < 2) {
        BasicText("Not enough attempts in this period yet.", style = TextStyle(fontSize = 12.sp, color = diaryLabelGray))
        return
    }
    val top = (you + ai).filterNotNull().maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
    val paint = remember {
        android.graphics.Paint().apply { color = android.graphics.Color.rgb(110, 110, 110); textSize = 26f; isAntiAlias = true }
    }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val left = 64f
        val bottom = size.height - 30f
        val topY = 10f
        val w = size.width - left - 8f
        fun x(i: Int) = left + w * i / (n - 1)
        fun y(v: Float) = bottom - (bottom - topY) * (v / top)
        drawRect(Color(0xFF202020), topLeft = Offset(left, topY), size = androidx.compose.ui.geometry.Size(w, bottom - topY))
        drawContext.canvas.nativeCanvas.drawText("%.0f%%".format(top * 100), 4f, topY + 20f, paint)
        drawContext.canvas.nativeCanvas.drawText("0%", 4f, bottom, paint)
        labels.firstOrNull()?.let { drawContext.canvas.nativeCanvas.drawText(it, left, size.height - 4f, paint) }
        labels.lastOrNull()?.let { drawContext.canvas.nativeCanvas.drawText(it, left + w - paint.measureText(it), size.height - 4f, paint) }
        listOf(you to youColor, ai to aiColor).forEach { (vals, c) ->
            var prev: Offset? = null
            vals.forEachIndexed { i, v ->
                if (v == null) { prev = null; return@forEachIndexed }
                val pt = Offset(x(i), y(v))
                prev?.let { drawLine(c, it, pt, strokeWidth = 5f) }
                drawCircle(c, 5f, pt)
                prev = pt
            }
        }
    }
}
