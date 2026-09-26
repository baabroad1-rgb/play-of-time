package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.core.utils.CheerUpTrigger
import ru.iflex.timemvp.core.utils.tidyLineBreaks
import ru.iflex.timemvp.features.main.domain.FailCardData
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.baseTextStyle
import ru.iflex.timemvp.ui_theme.greyBackgroundColor

/**
 * Fail-карточка (Alex 2026-07-13). Симметричный ответ на GreetingView для
 * fail-пути: показывается после провала окна, ДО активации бабла с новой
 * фазой. Логика ролей:
 *
 *   • GreetingView (успех) = церемония, салют, «TAKE NEXT».
 *   • FailCard      (fail) = тихая поддержка, без салюта, «GOT IT».
 *
 * Тон подобран по маркетинговым принципам (Alex 2026-07-13):
 *   • Никакого «not enough wins» / «you lost».
 *   • Reframe: то, что задача упрощается — это ПОМОЩЬ, а не наказание.
 *   • Loss aversion — акцент на «что сохраняется», не «что потеряно».
 *   • Пауза между стата-блоком и forward-фразой даёт «выдохнуть».
 *
 * Закрывается ТОЛЬКО тапом на «GOT IT» — тап по фону/тексту не закрывает
 * (правило от Alex 2026-07-13, применённое ко всем posted-quest карточкам).
 */
@Composable
fun FailCard(
    data: FailCardData,
    onDismiss: () -> Unit = {}
) {
    val (title, opener, coach) = failCopy(data.trigger)

    // Диаметр мордочки = ширина куска подписи под ней "от начала слова more
    // до конца слова of" (Alex 2026-09-22) — например в "One more notch of
    // ease" это "more notch of". Меряется реальным TextMeasurer по тому же
    // стилю, каким ниже рисуется title, а не подбирается на глаз — у каждого
    // триггера title свой, у большинства нет обоих слов, тогда остаётся
    // прежний фиксированный размер.
    val density      = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = TextStyle(
        fontSize   = 18.sp,
        fontWeight = FontWeight.Bold
    )
    val faceDiameter: Dp = remember(title) {
        val span = Regex("""\bmore\b[\s\S]*?\bof\b""").find(title)?.value
        if (span != null) {
            val widthPx = textMeasurer.measure(text = span, style = titleStyle).size.width
            with(density) { widthPx.toDp() }
        } else {
            80.dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.greyBackgroundColor),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Мордочка этой сессии (Alex 2026-09-22) — заменила прежнее
            // приглушённое кольцо-заглушку. Тот же report.html?mode=face
            // движок, что и карточка отчёта на Analytics; см. SessionFaceView.
            // Заливка-«пятно» под мордочкой УБРАНА, а сама мордочка растянута
            // на весь размер контейнера (была faceDiameter*0.725, оставляя
            // видимое кольцо фона вокруг неё) — Alex 2026-09-22: "морда должна
            // быть размером с это чёрное пятно... пятно нахуй убрать". Сам
            // размер — вдвое больше измеренного faceDiameter (Alex 2026-09-22,
            // следующим сообщением: "сделай морду... в два раза больше").
            val faceDisplayDiameter = faceDiameter * 2f
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 20.dp)
                    .size(faceDisplayDiameter)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SessionFaceView(fromMs = data.fromMs, toMs = data.toMs, size = faceDisplayDiameter)
            }

            // Заголовок — нейтральное описание того, что произошло. Не «FAIL».
            BasicText(
                text = title,
                style = TextStyle(
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.baseTextColor,
                    textAlign  = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            // Числовой блок — что реально было в окне. Loss-aversion friendly:
            // акцент на «сколько попыток игрок сделал» (а не «сколько провалил»).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.06f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChipMini("Attempts", "${data.attempts}")
                    StatChipMini("Wins",     "${data.wins}")
                }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = tidyLineBreaks("Task: ${data.prevPhaseDescription}"),
                    style = baseTextStyle.copy(
                        fontSize  = 12.sp,
                        color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))

            // Reframe — как это стало помощью, а не наказанием.
            BasicText(
                text = tidyLineBreaks(opener),
                style = baseTextStyle.copy(
                    fontSize   = 14.sp,
                    lineHeight = 20.sp,
                    textAlign  = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Тренерский комментарий — 1-2 предложения о том, что делать дальше.
            // Здесь «коуч на плече» самое место: не назидание, а инсайт.
            BasicText(
                text = tidyLineBreaks(coach),
                style = baseTextStyle.copy(
                    fontSize   = 13.sp,
                    lineHeight = 19.sp,
                    color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.90f),
                    textAlign  = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            // Новая фаза — что теперь будет.
            BasicText(
                text = tidyLineBreaks("Now: ${data.newPhaseDescription}."),
                style = TextStyle(
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.baseTextColor,
                    textAlign  = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            // Единственная точка выхода — кнопка. Тап по фону НЕ закрывает.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.accentColor)
                    .clickable(onClick = { onDismiss() })
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = if (data.trigger == CheerUpTrigger.AFTER_SUBQUEST_STALLED)
                             "REST — I'LL BE BACK"
                           else "GOT IT",
                    style = TextStyle(
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.Black,
                        textAlign  = TextAlign.Center
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatChipMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text  = value,
            style = TextStyle(
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.baseTextColor
            )
        )
        BasicText(
            text  = label,
            style = TextStyle(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.63f)
            )
        )
    }
}

/**
 * Копирайт fail-карточки, разбитый по триггерам. Возвращает
 * (title, opener, coach). Принципы (Alex 2026-07-13):
 *   • Title — нейтральное описание фазы («The mark widens», не «You failed»).
 *   • Opener — reframe как помощь.
 *   • Coach — актионный совет («slow down», «rest», «check breathing»).
 */
private fun failCopy(trigger: CheerUpTrigger): Triple<String, String, String> = when (trigger) {
    CheerUpTrigger.AFTER_SUBQUEST_ENTER -> Triple(
        "The mark widens one step",
        "The window ran out this pass, so the mark eases a notch. The aim stays the " +
            "same — you just have more room to land it.",
        "Everything you've built so far is still yours. Beat this easier setting cleanly " +
            "and the original quest counts as done."
    )
    CheerUpTrigger.AFTER_SUBQUEST_DEEPER -> Triple(
        "One more notch of ease",
        "The threes still scattered. We're loosening the mark once more — no rush, we're " +
            "not going anywhere.",
        "Try slowing the tempo between attempts. Fatigue and rush cost more accuracy than " +
            "the extra tolerance can give back."
    )
    CheerUpTrigger.AFTER_SUBQUEST_CAPPED -> Triple(
        "As easy as it gets",
        "The mark is at its widest now. From here on there's nothing left to loosen — " +
            "it's just you and the rhythm.",
        "Slow your breath before each Run. Let the fingers rest between attempts. Precision " +
            "comes from calm, not from effort."
    )
    CheerUpTrigger.AFTER_SUBQUEST_STALLED -> Triple(
        "Time to step away",
        "Full ease and the threes still scatter — this is a fatigue signal, not a skill signal. " +
            "The window will be here fresh whenever you come back.",
        "Take a real break. Sleep on it. When you return, the same 99 attempts wait — and the " +
            "clarity that comes with rest will carry you through them."
    )
    else -> Triple(
        "Task paused",
        "The window closed — we're easing the conditions to keep you moving.",
        "Come back with fresh focus."
    )
}
