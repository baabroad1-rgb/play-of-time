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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
internal fun WhoToggle(rival: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .height(22.dp)
            .clip(shape)
            .background(toggleBg)
            .border(1.dp, toggleBorder, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhoSegment("You", on = !rival, onColor = MaterialTheme.colorScheme.accentColor, twin = false) { onSelect(false) }
        WhoSegment("AI", on = rival, onColor = rivalPurple, twin = true) { onSelect(true) }
    }
}

@Composable
private fun WhoSegment(label: String, on: Boolean, onColor: Color, twin: Boolean, onClick: () -> Unit) {
    val fg = if (on) Color.White else Color.White.copy(alpha = 0.45f)
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

        // Кто он такой (Alex 08.11: «надо как-то прокомментировать, что за
        // доппельгангер, откуда взялся, указать автора модели»). Свёрнуто;
        // по образцу подписи к мордам «Based on Chernoff faces (1973)».
        val aboutOpen = ABOUT_KEY in expandedDays
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleDay(ABOUT_KEY) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            BasicText(
                text  = (if (aboutOpen) "▾ " else "▸ ") + "Who is ${RivalEngine.NAME}?",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = rivalPurpleLight)
            )
        }
        if (aboutOpen) {
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
                    ABOUT_CREDITS + (if (llm.isNotEmpty())
                        " Diary entries: ${modelCredit(llm.values.maxByOrNull { it.at }?.model.orEmpty())}." else ""),
                    style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = diaryLabelGray)
                )
            }
        }
    }
}

private const val ABOUT_KEY = "about"

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
    "Idea: Alex Belyshkin. Learning scheme after the mushroom body of Drosophila (Aso et al., eLife 2014) " +
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
