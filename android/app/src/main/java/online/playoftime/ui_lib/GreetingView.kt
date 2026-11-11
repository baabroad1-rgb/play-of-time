package online.playoftime.ui_lib

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.playoftime.features.main.domain.QuestCompletionStats
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.baseTextStyle
import online.playoftime.ui_theme.greyBackgroundColor
import online.playoftime.ui_theme.resultGreenColor
import online.playoftime.ui_theme.resultRedColor
import online.playoftime.ui_theme.resultBlueColor
import kotlin.math.roundToInt

/**
 * Congratulation animation -- the two-sided firework salute (2026-08-16,
 * replaces the old pulsing ic_win icon). Isolated composable so
 * recompositions never touch the text above/below.
 */
@Composable
private fun SaluteIcon() {
    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(0.94f)
            .widthIn(max = 420.dp)
    ) {
        SaluteCelebration(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun GreetingView(
    greetingText: String = "",
    questStats: QuestCompletionStats? = null,
    onClick: () -> Unit = {}
) {
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
                .padding(horizontal = Dimens.horizontalPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SaluteIcon()

            // Минимальный режим для уровня 1 (Alex 2026-07-09): до второго уровня
            // показываем только анимацию, «какой квест пройден», время и таблицу.
            // Монеты, поздравление и назидательный closing-текст на L1 — шум.
            val minimal = questStats != null && questStats.levelId < 2

            // Congratulation text
            if (greetingText.isNotEmpty() && !minimal) {
                BasicText(
                    text = greetingText,
                    modifier = Modifier.fillMaxWidth(),
                    style = baseTextStyle.copy(
                        fontSize  = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(Modifier.height(16.dp))
            }

            if (questStats != null) {
                // Quest badge
                BasicText(
                    text = "Level ${questStats.levelId} · Quest ${questStats.taskId} — Complete!",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = resultGreenColor,
                        textAlign  = TextAlign.Center
                    )
                )
                Spacer(Modifier.height(20.dp))

                // ── Золотой блок перехода уровня ────────────────────────────
                // Показывается когда взят ПОСЛЕДНИЙ квест уровня — в т.ч. на L1,
                // несмотря на minimal-режим: переход уровня важнее правила
                // «на первом уровне без шума» (Alex 2026-07-12).
                if (questStats.isLevelComplete) {
                    val gold = androidx.compose.ui.graphics.Color(0xFFFFD700)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(gold.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BasicText(
                            text  = "★ LEVEL ${questStats.levelId} COMPLETE",
                            style = TextStyle(
                                fontSize   = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color      = gold,
                                textAlign  = TextAlign.Center
                            )
                        )
                        val secondLine = if (questStats.isGameComplete)
                            "All 15 quests done — game finished!"
                        else
                            "Precision moves to ${questStats.nextPrecisionLabel}"
                        BasicText(
                            text  = online.playoftime.core.utils.tidyLineBreaks(secondLine),
                            style = baseTextStyle.copy(
                                fontSize  = 13.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (questStats.levelAttempts > 0) {
                            // Clean threes = точное попадание 3.000 (endTime−startTime == aimTime).
                            // Осмысленно только на L3 (тысячные). На L1 (десятые) и L2 (сотые)
                            // отображаемая точность win-зоны шире, поэтому большинство
                            // «побед» показываются как 0 clean threes — путает игрока.
                            val showThrees = questStats.levelId >= 3
                            val text = if (showThrees)
                                "5/5 quests · ${formatCount(questStats.levelAttempts)} attempts · " +
                                    "${formatCount(questStats.levelTrueThrees)} clean threes"
                            else
                                "5/5 quests · ${formatCount(questStats.levelAttempts)} attempts"
                            BasicText(
                                text = text,
                                style = baseTextStyle.copy(
                                    fontSize  = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.83f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (questStats.hasQuestStats) {
                    // ── Part 1: Time spent ──────────────────────────────────
                    // Реальное время: questPureTimeMinutes = сумма activeTimeMs сессий
                    // квеста (только пока MainScreen был активен). Дни упоминаем только
                    // если квест реально играли больше одного календарного дня —
                    // «You spent 1 day and 4 min» для 4-минутного квеста звучало глупо.
                    val timeLabel = formatPureTime(questStats.questPureTimeMinutes)
                    val timeSpentText = if (questStats.questDays > 1)
                        "You spent $timeLabel of pure play time on this quest across ${questStats.questDays} days."
                    else
                        "You spent $timeLabel of pure play time on this quest."
                    BasicText(
                        text = online.playoftime.core.utils.tidyLineBreaks(timeSpentText),
                        modifier = Modifier.fillMaxWidth(),
                        style = baseTextStyle.copy(textAlign = TextAlign.Center, fontSize = 14.sp)
                    )
                    Spacer(Modifier.height(8.dp))

                    // ── Part 2: Serenity Coins (скрыто на уровне 1) ─────────
                    // «Your coins», а не «You earned» (Alex 2026-07-12): игрок
                    // сделал мир уравновешеннее, коины — расписка, которую мир
                    // возвращает ему, а не заработок.
                    if (!minimal) {
                        val scmText = formatScm(questStats.scmEarned)
                        BasicText(
                            text = "Your coins: $scmText Serenity.",
                            modifier = Modifier.fillMaxWidth(),
                            style = baseTextStyle.copy(
                                textAlign  = TextAlign.Center,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    // Карточка «this quest vs your overall average» удалена
                    // (Alex 2026-07-12) — остаётся только «session vs history»
                    // с интерпретацией.

                    // ── Part 3b: «Победная сессия vs история» + Interpretation ──
                    // Числовой блок и его словесная расшифровка (той же пятёркой
                    // параметров, в том же порядке). Открывается с L2.2.
                    if (questStats.interpretationUnlocked) {
                        // Псевдослучайное зерно для выбора варианта фразы —
                        // стабильно в пределах одного показа карточки (не «прыгает»
                        // на рекомпозициях), но разное между разными квестами.
                        val phraseSeed = (
                            questStats.sessionAttempts * 131 +
                            questStats.questPureTimeMinutes * 17 +
                            questStats.taskId * 7 +
                            questStats.levelId
                        )
                        val interpRows = buildList {
                            interpRow(
                                label = "Precision",
                                sessText = questStats.sessPrecisionMs.takeIf { it > 0 }
                                    ?.let { "${it.roundToInt()} ms" },
                                histText = questStats.histPrecisionMs.takeIf { it > 0 }
                                    ?.let { "${it.roundToInt()} ms" },
                                verdict = verdictOf(questStats.sessPrecisionMs,
                                    questStats.histPrecisionMs, lowerBetter = true),
                                seed  = phraseSeed,
                                better = listOf(
                                    " was sharper than usual — your errors ran smaller.",
                                    " tightened — you were closer to the mark than your average.",
                                    " gained an edge — smaller errors than your typical run."
                                ),
                                worse  = listOf(
                                    " slipped — your errors ran bigger than usual.",
                                    " loosened — the misses landed farther out than your average.",
                                    " drifted wider — errors were larger than you usually manage."
                                ),
                                onPar  = listOf(
                                    " held right at your usual level.",
                                    " sat right where your average sits.",
                                    " kept exactly your usual range."
                                )
                            )?.let { add(it) }
                            // Порядок строк = значимость параметров (Alex 2026-07-23):
                            // Precision → Luck → Excitation → Inhibition → Focus →
                            // Consistency → Pace. Текст-интерпретация идёт в том же
                            // порядке.
                            interpRow(
                                label = "Luck",
                                sessText = questStats.sessLucky.takeIf { it >= 0 }
                                    ?.let { "%.2f".format(it) },
                                histText = questStats.histLucky.takeIf { it > 0 }
                                    ?.let { "%.2f".format(it) },
                                verdict = verdictOf(questStats.sessLucky,
                                    questStats.histLucky, lowerBetter = false),
                                seed  = phraseSeed,
                                better = listOf(
                                    " was on your side — more clean hits than chance predicts.",
                                    " ran hot — more zero-error landings than you usually catch.",
                                    " favoured you — clean threes above your typical rate."
                                ),
                                worse  = listOf(
                                    " stepped back — fewer clean hits than usual.",
                                    " ran cool — clean landings under your typical share.",
                                    " dipped — the exact-mark hits didn’t come as easy."
                                ),
                                onPar  = listOf(
                                    " stayed at its usual level.",
                                    " held to your baseline rate.",
                                    " kept its usual share of exacts."
                                )
                            )?.let { add(it) }
                            // Возбуждение/торможение (Alex 2026-07-15) — павловская
                            // пара: Excitation = типичная РАННЯЯ ошибка (перевозбуждён,
                            // жмёт раньше), Inhibition = типичная ПОЗДНЯЯ (заторможен).
                            // Меньше = лучше для обеих.
                            interpRow(
                                label = "Excitation",
                                sessText = questStats.sessExcitationMs.takeIf { it > 0 }
                                    ?.let { "${it.roundToInt()} ms" },
                                histText = questStats.histExcitationMs.takeIf { it > 0 }
                                    ?.let { "${it.roundToInt()} ms" },
                                verdict = verdictOf(questStats.sessExcitationMs,
                                    questStats.histExcitationMs, lowerBetter = true),
                                seed  = phraseSeed,
                                better = listOf(
                                    " eased — your early stops came closer to the mark.",
                                    " settled — less rushing ahead of the aim than usual.",
                                    " calmed — the jumps ahead of time ran smaller."
                                ),
                                worse  = listOf(
                                    " ran high — you kept firing ahead of the mark.",
                                    " spiked — early stops came in wider than your usual.",
                                    " pushed you — more rushing before the aim than normal."
                                ),
                                onPar  = listOf(
                                    " stayed at its usual level.",
                                    " matched your typical early lean.",
                                    " kept its usual measure."
                                )
                            )?.let { add(it) }
                            interpRow(
                                label = "Inhibition",
                                sessText = questStats.sessInhibitionMs.takeIf { it > 0 }
                                    ?.let { "${it.roundToInt()} ms" },
                                histText = questStats.histInhibitionMs.takeIf { it > 0 }
                                    ?.let { "${it.roundToInt()} ms" },
                                verdict = verdictOf(questStats.sessInhibitionMs,
                                    questStats.histInhibitionMs, lowerBetter = true),
                                seed  = phraseSeed,
                                better = listOf(
                                    " lifted — your late stops came closer to the mark.",
                                    " loosened its grip — less lag behind the aim than usual.",
                                    " thinned — the overshoots past the mark ran smaller."
                                ),
                                worse  = listOf(
                                    " weighed on you — stops kept landing past the mark.",
                                    " deepened — late stops came in wider than your usual.",
                                    " held you back — more lag behind the aim than normal."
                                ),
                                onPar  = listOf(
                                    " stayed at its usual level.",
                                    " matched your typical late lean.",
                                    " kept its usual measure."
                                )
                            )?.let { add(it) }
                            interpRow(
                                label = "Focus",
                                sessText = questStats.sessFocusPct.takeIf { it >= 0 }
                                    ?.let { "$it%" },
                                histText = questStats.histFocusPct.takeIf { it > 0 }
                                    ?.let { "$it%" },
                                verdict = verdictOf(questStats.sessFocusPct.toDouble(),
                                    questStats.histFocusPct.toDouble(), lowerBetter = false),
                                seed  = phraseSeed,
                                better = listOf(
                                    " was sharper — your misses balanced evenly around the aim.",
                                    " read cleaner — early and late errors kept swapping sides.",
                                    " showed poise — no lean toward early or late this session."
                                ),
                                worse  = listOf(
                                    " wavered — misses kept leaning to one side.",
                                    " slipped — the errors clustered on one side of the mark.",
                                    " ran one-sided — most misses fell in the same direction."
                                ),
                                onPar  = listOf(
                                    " held steady at its usual level.",
                                    " matched your usual balance.",
                                    " kept its typical spread of early and late."
                                )
                            )?.let { add(it) }
                            interpRow(
                                label = "Consistency",
                                sessText = questStats.sessConsistPct.takeIf { it >= 0 }
                                    ?.let { "$it%" },
                                histText = questStats.histConsistPct.takeIf { it > 0 }
                                    ?.let { "$it%" },
                                verdict = verdictOf(questStats.sessConsistPct.toDouble(),
                                    questStats.histConsistPct.toDouble(), lowerBetter = false),
                                seed  = phraseSeed,
                                better = listOf(
                                    " grew — error after error kept shrinking.",
                                    " tightened attempt by attempt — a proper build.",
                                    " climbed — each miss came in smaller than the one before."
                                ),
                                worse  = listOf(
                                    " sagged — errors grew back instead of shrinking.",
                                    " reversed — the misses got bigger as the session ran on.",
                                    " gave ground — attempts drifted wider, not tighter."
                                ),
                                onPar  = listOf(
                                    " stayed at its usual level.",
                                    " held to your baseline curve.",
                                    " matched your typical build."
                                )
                            )?.let { add(it) }
                            interpRow(
                                label = "Pace",
                                sessText = questStats.sessPauseMin.takeIf { it >= 0 }
                                    ?.let { "$it min" },
                                histText = questStats.histPauseMin.takeIf { it > 0 }
                                    ?.let { "$it min" },
                                verdict = verdictOf(questStats.sessPauseMin.toDouble(),
                                    questStats.histPauseMin.toDouble(), lowerBetter = true),
                                seed  = phraseSeed,
                                better = listOf(
                                    " picked up — you came back to play sooner than usual.",
                                    " sharpened — a shorter pause than your typical gap.",
                                    " warmed — the return between sessions came quicker."
                                ),
                                worse  = listOf(
                                    " cooled — the pause before this session was longer than usual.",
                                    " slowed — a bigger gap than your usual before returning.",
                                    " stretched — more time off than your typical rhythm."
                                ),
                                onPar  = listOf(
                                    " kept your usual rhythm.",
                                    " held to your typical gap.",
                                    " matched your usual pace between sessions."
                                )
                            )?.let { add(it) }
                        }

                        if (interpRows.isNotEmpty()) {
                            // Подсветка строки таблицы по тапу на слово-параметр
                            // в тексте интерпретации ниже.
                            var highlightedParam by remember {
                                androidx.compose.runtime.mutableStateOf<String?>(null)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.06f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Подписи колонок данных (Alex 2026-07-23) — та же
                                // сетка ширин, что у CompareRow: значения выровнены
                                // по правому краю своих колонок.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val capStyle = TextStyle(
                                        fontSize  = 11.sp,
                                        color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.53f),
                                        textAlign = TextAlign.End
                                    )
                                    Spacer(Modifier.weight(1f))
                                    BasicText(
                                        text     = "session",
                                        modifier = Modifier.width(CMP_COL_W),
                                        style    = capStyle
                                    )
                                    Spacer(Modifier.width(CMP_VS_W))
                                    BasicText(
                                        text     = "history",
                                        modifier = Modifier.width(CMP_COL_W),
                                        style    = capStyle
                                    )
                                }
                                interpRows.forEach { row ->
                                    CompareRow(
                                        label       = row.label,
                                        questText   = row.sessText,
                                        overallText = row.histText,
                                        improved    = row.verdict > 0,
                                        worsened    = row.verdict < 0,
                                        highlighted = highlightedParam == row.label
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            // ── Интерпретация + вывод — единый живой текст ────
                            // Каждое предложение начинается со слова-параметра
                            // (жирное, тапабельное — подсвечивает свою строку в
                            // таблице выше); в конце — общий вывод.
                            val worse  = interpRows.filter { it.verdict < 0 }
                            val better = interpRows.filter { it.verdict > 0 }
                            val conclusion = when {
                                worse.isEmpty() && better.isNotEmpty() ->
                                    "All in all — a strong session. Keep doing what you're doing."
                                worse.isEmpty() ->
                                    "All in all — steady as usual, right on your track."
                                worse.size == interpRows.size ->
                                    "All in all — a tough session, but you pushed through. " +
                                        "Next quest, slow down and rebuild your rhythm."
                                else ->
                                    "All in all — next quest, give a little extra care to " +
                                        worse.joinToString(", ") { it.label.lowercase() } +
                                        "; the rest is already working for you."
                            }

                            // Абзацы (Alex 2026-07-23): по одному на параметр, в том
                            // же порядке значимости, что и таблица; выравнивание по
                            // левому краю; «All in all» — отдельным абзацем.
                            val highlightBg = androidx.compose.ui.graphics.Color(0x59F5C02E)
                            val paraStyle = baseTextStyle.copy(
                                textAlign  = TextAlign.Start,
                                fontSize   = 13.sp,
                                lineHeight = 18.sp,
                                color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.90f)
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                interpRows.forEach { row ->
                                    val phrase = when {
                                        row.verdict > 0  -> row.better
                                        row.verdict < 0  -> row.worse
                                        else             -> row.onPar
                                    }
                                    val para = buildAnnotatedString {
                                        pushStringAnnotation(tag = "param", annotation = row.label)
                                        withStyle(SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            background = if (highlightedParam == row.label)
                                                highlightBg
                                            else androidx.compose.ui.graphics.Color.Unspecified
                                        )) {
                                            append(row.label)
                                        }
                                        pop()
                                        append(phrase)
                                    }
                                    androidx.compose.foundation.text.ClickableText(
                                        text = para,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = paraStyle,
                                        onClick = { offset ->
                                            val hit = para
                                                .getStringAnnotations("param", offset, offset)
                                                .firstOrNull()?.item
                                            highlightedParam =
                                                if (hit == highlightedParam) null else hit
                                        }
                                    )
                                }
                                BasicText(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("All in all")
                                        }
                                        append(conclusion.removePrefix("All in all"))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 5.dp),
                                    style = paraStyle
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    // Stats not yet loaded — show basic session chips while DB query runs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip("Attempts", "${questStats.sessionAttempts}")
                        StatChip("Wins",     "${questStats.sessionWins}")
                        StatChip("Hit Rate", "${questStats.winRatePct}%")
                    }
                    Spacer(Modifier.height(28.dp))
                }

                // ── Игривая похвала (бывшая вводная AFTER_QUEST-бабла) ───
                // Переехала на карточку (Alex 2026-07-12): карточка = итоги +
                // похвала, бабл после TAKE NEXT = только требование квеста.
                if (questStats.arrivalLevelId > 0) {
                    BasicText(
                        text = online.playoftime.core.utils.CheerUpMessages.praiseFor(
                            questStats.arrivalLevelId, questStats.arrivalTaskId
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        style = baseTextStyle.copy(
                            textAlign  = TextAlign.Center,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // ── Part 5: Take Quest button ───────────────────────────
                // Закрытие ТОЛЬКО через эту кнопку (Alex 2026-07-13).
                // Раньше выйти можно было тапом по любому месту карточки —
                // случайные тапы во время чтения статистики закрывали
                // экран не по намерению. Теперь весь Box кликабелен, но
                // outer Box — нет.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.accentColor)
                        .clickable(onClick = { onClick() })
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "TAKE NEXT QUEST",
                        style = TextStyle(
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = androidx.compose.ui.graphics.Color.Black,
                            textAlign  = TextAlign.Center
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Строка сравнения «этот квест vs накопленное среднее». Значение квеста —
 * жирное и цветное (зелёное = лучше среднего, красное = хуже), справа через
 * «vs» — накопленное среднее обычным цветом.
 */
@Composable
private fun CompareRow(
    label: String,
    questText: String,
    overallText: String,
    improved: Boolean,
    worsened: Boolean,
    /** Мягкая подсветка строки — по тапу на слово-параметр в интерпретации. */
    highlighted: Boolean = false
) {
    val questColor = when {
        improved -> resultGreenColor
        worsened -> resultRedColor
        else     -> MaterialTheme.colorScheme.baseTextColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.background(
                    androidx.compose.ui.graphics.Color(0x33F5C02E),
                    RoundedCornerShape(6.dp)
                ) else Modifier
            )
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            modifier = Modifier.weight(1f),
            text  = label,
            style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.78f))
        )
        // Фиксированные колонки (Alex 2026-07-23): значения session и history
        // выровнены по правому краю своих колонок, «vs» — по своей узкой колонке.
        BasicText(
            modifier = Modifier.width(CMP_COL_W),
            text  = questText,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = questColor, textAlign = TextAlign.End)
        )
        BasicText(
            modifier = Modifier.width(CMP_VS_W),
            text  = "vs",
            style = TextStyle(fontSize = 11.sp,
                color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.53f),
                textAlign = TextAlign.Center)
        )
        BasicText(
            modifier = Modifier.width(CMP_COL_W),
            text  = overallText,
            style = TextStyle(fontSize = 13.sp,
                color = MaterialTheme.colorScheme.baseTextColor, textAlign = TextAlign.End)
        )
    }
}

/** Ширины колонок таблицы «session vs history» — общие для заголовка и строк. */
private val CMP_COL_W = 58.dp
private val CMP_VS_W  = 26.dp

private fun formatCount(n: Int): String = "%,d".format(n).replace(',', ' ')

// ── Интерпретация «сессия vs история» ──────────────────────────────────────

/** Строка интерпретационного блока: числа + словесные варианты. */
private data class InterpRowData(
    val label: String,
    val sessText: String,
    val histText: String,
    val verdict: Int,      // 1 лучше, -1 хуже, 0 на уровне
    val better: String,
    val worse: String,
    val onPar: String
)

/** null — если данных для строки нет (она целиком скрывается). */
private fun interpRow(
    label: String,
    sessText: String?,
    histText: String?,
    verdict: Int?,
    /**
     * Псевдослучайное зерно для выбора варианта фразы. Одинаковое зерно
     * для всех рядов одной карточки — фраза стабильна в пределах показа,
     * но row.label подмешивается ниже, чтобы разные параметры получали
     * независимые выборы вариантов.
     */
    seed: Int,
    better: List<String>,
    worse: List<String>,
    onPar: List<String>
): InterpRowData? {
    if (sessText == null || histText == null || verdict == null) return null
    fun pick(list: List<String>): String {
        if (list.isEmpty()) return ""
        val h = seed * 31 + label.hashCode()
        val idx = ((h % list.size) + list.size) % list.size
        return list[idx]
    }
    return InterpRowData(label, sessText, histText, verdict,
        pick(better), pick(worse), pick(onPar))
}

/**
 * Вердикт сравнения сессии с историей: 1 лучше, -1 хуже, 0 — «на уровне»
 * (разница ≤ 10% от накопленного значения). null — данных нет.
 */
private fun verdictOf(sess: Double, hist: Double, lowerBetter: Boolean): Int? {
    if (sess < 0 || hist <= 0) return null
    val diff = sess - hist
    if (kotlin.math.abs(diff) <= 0.10 * hist) return 0
    val better = if (lowerBetter) diff < 0 else diff > 0
    return if (better) 1 else -1
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text  = online.playoftime.features.stats.ui.groupDigits(value),
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.baseTextColor)
        )
        BasicText(
            text  = label,
            style = TextStyle(fontSize = 12.sp,
                color = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.68f))
        )
    }
}

private fun formatPureTime(minutes: Int): String = when {
    minutes < 1  -> "less than a minute"
    minutes < 60 -> "$minutes min"
    else         -> "${minutes / 60} h ${minutes % 60} min"
}

private fun formatScm(scm: Double): String = when {
    scm < 10    -> "%.2f".format(scm)
    scm < 1000  -> "%.1f".format(scm)
    else        -> "%,d".format(scm.toLong())
}
