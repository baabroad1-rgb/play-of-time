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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
private val rivalPurpleText   = Color(0xFFA78BFA)
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
 * Дневник Doppelgänger'а гармошкой (Alex: «а сам дневник сделай тоже
 * гармошкой»): заголовок сворачивает всю карточку, внутри — дни, новые
 * сверху, каждый день сворачивается. Самый свежий день раскрыт по умолчанию:
 * для него членство в [expandedDays] означает «свернуть», для остальных —
 * «раскрыть» (одно множество на оба случая, ViewModel только переключает ключ).
 *
 * [entries] — записи выбранного периода, старые первыми.
 */
@Composable
internal fun RivalDiaryCard(
    entries      : List<RivalEngine.DiaryEntry>,
    questLabel   : String,
    periodLabel  : String,
    expanded     : Boolean,
    expandedDays : Set<String>,
    onToggle     : () -> Unit,
    onToggleDay  : (String) -> Unit,
    llm          : Map<String, RivalEngine.LlmEntry> = emptyMap(),
    writing      : Set<String> = emptySet(),
    failed       : Set<String> = emptySet(),
    tooFew       : Set<String> = emptySet(),
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
            if (entries.isEmpty()) {
                BasicText(
                    text     = "No entries for this period yet. He writes when something happens — " +
                               "a first clean three, a streak, a quest won or lost.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style    = TextStyle(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.6f))
                )
                return@Column
            }
            val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
            val dayFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH)
            val days = entries.groupBy { keyFmt.format(Date(it.time)) }
                .toList()
                .sortedByDescending { it.first }
            val newest = days.first().first
            val todayKey = keyFmt.format(Date())
            var anyWritten = false
            days.forEachIndexed { i, (key, list) ->
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
                        text  = (if (open) "▾ " else "▸ ") + dayFmt.format(Date(list.first().time)),
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                                          color = Color.White.copy(alpha = 0.86f))
                    )
                    BasicText(
                        text  = if (key in llm) "entry · ${list.size} notes" else "${list.size} notes",
                        style = TextStyle(fontSize = 11.5.sp, color = diaryLabelGray)
                    )
                }
                if (open) {
                    // Запись языковой модели — только когда день открыт: человек
                    // зашёл почитать (Alex: «запросы генерятся… только если человек
                    // зашёл и хочет почитать»). Сегодняшнюю движок перепишет сам,
                    // если день с тех пор сильно вырос; прошлые пишутся один раз.
                    val written = llm[key]
                    androidx.compose.runtime.LaunchedEffect(key) {
                        if (written == null || key == todayKey) onRequest(key, false)
                    }
                    when {
                        key in writing && written == null -> DiaryStatus("${RivalEngine.NAME} is writing…", spinner = true)
                        written != null -> BasicText(
                            text     = written.entry,
                            modifier = Modifier.padding(start = 4.dp, end = 2.dp, bottom = 10.dp),
                            style    = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp,
                                                 color = Color.White.copy(alpha = 0.92f))
                        )
                        key in failed -> DiaryStatus("Couldn’t reach his pen just now — tap to try again.") {
                            onRequest(key, true)
                        }
                        else -> Unit                       // too few attempts that day — notes only
                    }
                    if (written != null && key in writing) DiaryStatus("Rewriting today…", spinner = true)
                    if (written != null) anyWritten = true

                    // Заметки-шаблоны движка — под своей гармошкой, свёрнуты.
                    val notesKey = "n:$key"
                    val notesOpen = notesKey in expandedDays || (written == null && key in tooFew)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDay(notesKey) }
                            .padding(start = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text  = (if (notesOpen) "▾ " else "▸ ") + "${list.size} " +
                                    (if (list.size == 1) "note" else "notes") + " from the game",
                            style = TextStyle(fontSize = 11.5.sp, color = diaryLabelGray)
                        )
                    }
                    if (notesOpen) {
                        list.asReversed().forEach { e ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 9.dp)
                            ) {
                                BasicText(
                                    text     = "#${e.n}",
                                    modifier = Modifier.width(44.dp),
                                    style    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                                         color = rivalPurpleText)
                                )
                                BasicText(
                                    text  = buildAnnotatedString {
                                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f))) { append(e.text) }
                                    },
                                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
                                )
                            }
                        }
                    }
                }
            }
            if (anyWritten) {
                BasicText(
                    text     = "Entries are written by a language model from his own numbers; notes come straight from the game.",
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    style    = TextStyle(fontSize = 10.5.sp, color = diaryLabelGray)
                )
            }
        }
    }
}

/** Строка состояния дня: «пишет…» со спиннером или «не вышло — тапни». */
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
