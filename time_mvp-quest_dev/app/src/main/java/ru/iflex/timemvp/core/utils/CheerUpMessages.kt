package ru.iflex.timemvp.core.utils

/**
 * Plain-English description of a quest phase — aim time, tolerance (if any), and
 * win condition. Feeds the "Now: hit …" / "Next quest …: hit …" fragment of the
 * cheer-up bubble.
 *
 *   describePhase(L2.4, N=33, t=10 ms) → "3.00 ± 0.01, twice in a row"
 *   describePhase(L1.2, N=66, t=0)     → "3.0, twice in 66 attempts"
 *   describePhase(L1.1, N=null, t=0)   → "3.0, once"
 */
fun describePhase(
    quest: Quest,
    effectiveN: Int?,
    effectiveTMs: Int
): String {
    // Правило переносов (Alex 2026-07-12, после бага «in row × 0 | /2»):
    // НЕ переклеивать всё подряд — сплошной неразрывный ран длиннее строки
    // Compose рвёт в СЛУЧАЙНОМ месте (внутри значения). Клеим только атомы
    // «значение»: aim±tolerance, "N attempts". Слова goal-фразы и запятая —
    // обычные пробелы, там перенос корректен.
    val nb = ' '
    val aim = when (quest.accuracy) {
        Accuracy.SECONDS      -> "3"
        Accuracy.DECISECONDS  -> "3.0"
        Accuracy.CENTISECONDS -> "3.00"
        Accuracy.MILLISECONDS -> "3.000"
    }
    val tolStr = if (effectiveTMs > 0) {
        val t = when (quest.accuracy) {
            Accuracy.SECONDS      -> "%.0f".format(effectiveTMs / 1000.0)
            Accuracy.DECISECONDS  -> "%.1f".format(effectiveTMs / 1000.0)
            Accuracy.CENTISECONDS -> "%.2f".format(effectiveTMs / 1000.0)
            Accuracy.MILLISECONDS -> "%.3f".format(effectiveTMs / 1000.0)
        }
        // "±" не отделяется от значения ни слева, ни справа.
        "$nb±$nb$t"
    } else ""
    val target = "$aim$tolStr"
    val goal = when (quest.victory.victoryCondition) {
        VictoryCondition.SESSION -> when (quest.victory.victoryCount) {
            1    -> "once"
            2    -> "twice"
            else -> "three times"
        }
        VictoryCondition.ROW -> when (quest.victory.victoryCount) {
            2    -> "twice in a row"
            else -> "three times in a row"
        }
        VictoryCondition.IN_N -> {
            val n = effectiveN ?: quest.maxAttemptsDefault ?: 33
            // "N attempts" всегда вместе — value и unit не разрывать.
            when (quest.victory.victoryCount) {
                1    -> "once in $n${nb}attempts"
                2    -> "twice in $n${nb}attempts"
                else -> "three times in $n${nb}attempts"
            }
        }
    }
    return "$target, $goal"
}

/**
 * Универсальная типографская подготовка текста к переносу строк:
 *   • пробел ПОСЛЕ короткого служебного слова (артикль / предлог / союз)
 *     заменяется на NBSP — слово не повисает в конце строки;
 *   • NBSP ставится ПЕРЕД одиночными "—" и "·" — они не оказываются
 *     в начале строки.
 * Уже расставленные вручную NBSP (describePhase, QuestProgress) не трогаются —
 * split идёт только по обычному пробелу U+0020.
 * Применять в UI на финальном тексте (CheerUpBubble, GreetingView), чтобы
 * покрыть и асинхронные суффиксы (enrichment, сравнение со средним).
 */
fun tidyLineBreaks(text: String): String {
    val nbsp = ' '
    val glueAfter = setOf(
        "a", "an", "the", "and", "or", "but", "of", "in", "on", "at",
        "to", "for", "per", "as", "by", "is", "are", "was", "it", "no"
    )
    val tokens = text.split(' ')
    if (tokens.size < 2) return text
    val sb = StringBuilder(tokens[0])
    for (i in 1 until tokens.size) {
        val prev = tokens[i - 1]
        val curr = tokens[i]
        // После конца предложения не клеим ("…on it. When you…" — "it." и "When"
        // могут разойтись по строкам, это корректно).
        val prevEndsSentence = prev.endsWith('.') || prev.endsWith('!') ||
            prev.endsWith('?') || prev.endsWith(':')
        val gluePrev = !prevEndsSentence && prev.lowercase().trim(',', ';') in glueAfter
        val glueCurr = curr == "—" || curr == "·"
        sb.append(if (gluePrev || glueCurr) nbsp else ' ').append(curr)
    }
    return sb.toString()
}

object CheerUpMessages {

    /**
     * Full bubble text with all placeholders substituted.
     *
     * @param nextQuestLabel   Fully-formed quest tag, e.g. "Quest #2.3" or, for an
     *                         indulged subquest, "SubQuest#2.4(1)" (number in parens
     *                         = indulgence step). Built by composeBubbleText from
     *                         quest()/isInSubquest()/subquestStep() — always supplied
     *                         (Alex 2026-08-14/2026-08-15), not just for advance triggers.
     * @param phaseDescription "3.00 ± 0.01, twice in a row" — the CURRENT phase
     *                         after any N/t change or advance (what the player
     *                         should aim for now).
     * @param completedLevelId Level just cleared — AFTER_LEVEL only.
     * @param nextPrecision    "hundredths" / "thousandths" — AFTER_LEVEL only.
     * @param attempts         Aggregated numbers filled by async DB enrichment
     * @param trueThrees       for AFTER_LEVEL / AFTER_GAME.
     * @param totalTimeStr
     * @param arrivalLevelId / arrivalTaskId — квест, НА который игрок только что
     *        прибыл (для AFTER_QUEST): по нему выбирается вводная фраза
     *        (таблица согласована с Alex 2026-07-07, cheer-ups quests_new.docx).
     * @param inNGapNote       true → квест был IN_N-индульгированным, но все победы
     *        легли достаточно близко друг к другу, чтобы формально уложиться в
     *        baseline-окно (Alex 2026-08-23, gap-based Rule 2 в onQuestWindowSuccess:
     *        «уровень определяется количеством попыток МЕЖДУ успешными
     *        выбиваниями»). Добавляет отдельный абзац об этом — игрок мог не
     *        заметить, что вообще был в индульгенции.
     *
     * Формат (Alex 2026-08-14): «Next:» / «Now:» убраны — квест-задачу предваряет
     * «Quest #{L.T}:» (или «SubQuest#{L.T}({step}):» для индульгированного субквеста,
     * Alex 2026-08-15), и сам текст задачи ВСЕГДА начинается с новой строки со
     * словом «hit» перед описанием фазы. Фоновая подсветка (`**...**`) убрана —
     * CheerUpBubble рендерит plain-текст крупным шрифтом, выделение не нужно.
     */
    fun specificText(
        trigger         : CheerUpTrigger,
        nextQuestLabel  : String = "",
        phaseDescription: String = "",
        completedLevelId: Int    = 0,
        nextPrecision   : String = "",
        attempts        : Int    = 0,
        trueThrees      : Int    = 0,
        totalTimeStr    : String = "",
        arrivalLevelId  : Int    = 0,
        arrivalTaskId   : Int    = 0,
        /**
         * Индекс для ротации recap-opener’ов (Alex 2026-07-13). Один и тот же
         * триггер два раза подряд получит РАЗНЫЙ вводный кивок — «одинаковая
         * фраза три раза кряду читается как спам». Считывается из
         * LocalStorage в composeBubbleText, каждый вызов ++mod длины массива.
         */
        phraseIndex     : Int    = 0,
        inNGapNote      : Boolean = false
    ): String {
        // Опенер = recap: короткий кивок в прошлое или reframe (для fail).
        // Опенеры хранятся в per-trigger массивах ниже, ротируются phraseIndex.
        val opener = pickOpener(trigger, phraseIndex)
        // «{tag}: hit {phase}.» — единый формат, на ОДНОЙ строке (Alex 2026-08-15:
        // «текст квеста, по отношению к названию не надо сносить на следующую
        // строку»). tag уже содержит "Quest #" / "SubQuest#...(step)" —
        // см. composeBubbleText. CheerUpBubble рендерит всё начиная с tag крупнее.
        val questBlock = "$nextQuestLabel: hit $phaseDescription."
        val base = when (trigger) {
            CheerUpTrigger.NONE,
            CheerUpTrigger.AFTER_SESSION -> ""

            CheerUpTrigger.AFTER_QUEST ->
                "$opener\n$questBlock"

            CheerUpTrigger.AFTER_LEVEL ->
                "$opener\n$questBlock"

            CheerUpTrigger.AFTER_GAME ->
                "$opener All 15 quests done: ${fmt(attempts)} attempts, " +
                    "${fmt(trueThrees)} clean threes, $totalTimeStr. Take a bow! " +
                    "Export and share your Excel (anonymised) at playoftime@gmail.com · " +
                    "fresh news at www.playoftime.online"

            CheerUpTrigger.AFTER_SUBQUEST_ENTER ->
                "$opener\n$questBlock Beat this and the original quest returns."

            CheerUpTrigger.AFTER_SUBQUEST_DEEPER ->
                "$opener\n$questBlock"

            CheerUpTrigger.AFTER_SUBQUEST_CAPPED ->
                "$opener\n$questBlock"

            CheerUpTrigger.AFTER_SUBQUEST_STALLED ->
                "$opener When you come back:\n$questBlock"

            CheerUpTrigger.AFTER_SUBQUEST_STEPBACK ->
                "$opener\n$questBlock"

            CheerUpTrigger.AFTER_SUBQUEST_TO_BASELINE ->
                "$opener\n$questBlock"

            CheerUpTrigger.AFTER_SIMPLIFIED_QUEST_DONE ->
                "$opener\n$questBlock"
        }
        // Отдельный абзац (Alex 2026-08-23): квест был индульгирован по количеству
        // попыток, но игрок уложил все победы в промежуток не шире исходного окна —
        // формально прошёл оригинальный квест, просто не заметив индульгенции.
        // Никогда не сочетается с AFTER_SESSION/NONE (inNGapNote выставляется только
        // на реальном advance, см. composeBubbleText) — base.isNotEmpty() на всякий
        // случай, чтобы не приклеить абзац к пустой строке.
        val gapNote = if (inNGapNote && base.isNotEmpty())
            "\n\nWorth noting: the hits landed close enough together to count on " +
                "the original pace anyway — cleared it without even needing the extra room."
        else ""
        return base + gapNote
    }

    /**
     * Recap-opener’ы — короткие «человеческие» вводные фразы, размеченные
     * под каждый триггер. Все ротируются phraseIndex’ом (см. specificText).
     * Принципы (Alex 2026-07-13):
     *   • Recap = отсылка к тому, что ТОЛЬКО ЧТО произошло (короткий кивок).
     *   • На fail — безоценочно + reframe как помощь («мы вместе»,
     *     «то, что наработано, никуда не денется»). НИКАКОГО «not enough wins».
     *   • Один триггер = 3–4 варианта, чтобы одинаковый триггер два раза
     *     подряд не звучал одинаково.
     *   • Никаких «keep it up» / «you got this» — конкретика > шаблон.
     *   • Короче (Alex 2026-08-17): ≤6 слов, одно предложение. Раньше
     *     openers были развёрнутой прозой (иногда два предложения) и
     *     читались как несогласованные по ритму с задачей ниже — теперь
     *     это просто короткий кивок, а вся «суть» — в строке задачи
     *     (CheerUpBubble выделяет её весом/цветом, не размером шрифта).
     */
    private fun pickOpener(trigger: CheerUpTrigger, index: Int): String {
        val bank: List<String> = when (trigger) {
            CheerUpTrigger.NONE,
            CheerUpTrigger.AFTER_SESSION -> return ""

            CheerUpTrigger.AFTER_QUEST -> listOf(
                "Quest cleared.",
                "Quest done — nailed it.",
                "That’s the quest, cleared.",
                "Quest done — good control."
            )

            CheerUpTrigger.AFTER_LEVEL -> listOf(
                "Level cleared.",
                "That’s a level down.",
                "Level done — bar moves up."
            )

            CheerUpTrigger.AFTER_GAME -> listOf(
                "All levels cleared.",
                "Full game — done."
            )

            CheerUpTrigger.AFTER_SUBQUEST_TO_BASELINE -> listOf(
                "Clean threes — ladder skipped.",
                "Landed clean — quest counts as done.",
                "Eased version, cracked clean."
            )

            CheerUpTrigger.AFTER_SIMPLIFIED_QUEST_DONE -> listOf(
                "Back at the original — and it fell.",
                "Full loop closed.",
                "Climb paid off — you’re there."
            )

            CheerUpTrigger.AFTER_SUBQUEST_ENTER -> listOf(
                "Window missed — easing one step.",
                "Not quite — one step easier.",
                "Short this round — widening the mark."
            )

            CheerUpTrigger.AFTER_SUBQUEST_DEEPER -> listOf(
                "Still scattering — easing once more.",
                "One more notch of ease.",
                "Loosening the mark again."
            )

            CheerUpTrigger.AFTER_SUBQUEST_CAPPED -> listOf(
                "Full ease reached.",
                "As easy as it gets.",
                "Widest the mark goes."
            )

            CheerUpTrigger.AFTER_SUBQUEST_STALLED -> listOf(
                "Floor reached — rest up.",
                "Take a break — that’s fatigue, not skill.",
                "Nothing left to soften — step away."
            )

            CheerUpTrigger.AFTER_SUBQUEST_STEPBACK -> listOf(
                "Good pass — tightening one step.",
                "Nice hits — one notch tighter.",
                "Well done — mark snugs in."
            )
        }
        if (bank.isEmpty()) return ""
        return bank[((index % bank.size) + bank.size) % bank.size]
    }

        fun praiseFor(arrivalLevelId: Int, arrivalTaskId: Int): String {
        // Expanded 3–4 variants per quest (Alex 2026-07-13, «карточка —
        // церемония, не шаблон»). Всё берётся детерминированно от
        // (levelId, taskId, System.currentTimeMillis()/10_000) — в пределах
        // одной сессии одна фраза, между сессиями меняется.
        val bank = when (arrivalLevelId to arrivalTaskId) {
            1 to 2 -> listOf(
                "Nice start!",
                "Good opening pass.",
                "Off the mark cleanly — keep going."
            )
            1 to 3 -> listOf(
                "Excellent!",
                "Well played.",
                "Coming together.",
                "That was a clean pass."
            )
            1 to 4 -> listOf(
                "You’re on a roll!",
                "Rhythm found.",
                "Fluent now — nice."
            )
            1 to 5 -> listOf(
                "That was impressive.",
                "Level 1 owned.",
                "Last one of the tier — clean."
            )
            2 to 1 -> listOf(
                "First level done — the stopwatch tightens now.",
                "Precision moved up. You’re in the hundredths league.",
                "The stopwatch has just grown sharper. You’re ready for it."
            )
            2 to 2 -> listOf(
                "Quest done — keep it up!",
                "That’s another one done.",
                "Steady work. Next."
            )
            2 to 3 -> listOf(
                "And done! Keep it rolling.",
                "Solid pass. On we go.",
                "Ticked off. Next up."
            )
            2 to 4 -> listOf(
                "Nicely done! Time to raise the bar.",
                "Well earned. The next mark tightens.",
                "Neatly landed — stepping up."
            )
            2 to 5 -> listOf(
                "Two in a row? No sweat for you. But can you do three?",
                "Level 2 nearly closed — one final push.",
                "Almost through the tier. One quest to go."
            )
            3 to 1 -> listOf(
                "Level cleared — welcome to the top league.",
                "Second level done. Precision goes to the thousandth now.",
                "You’re into the sharpest tier. Enjoy it."
            )
            3 to 2 -> listOf(
                "Welcome to the millisecond league.",
                "First thousandth-level quest, done.",
                "The smallest units — and you landed them."
            )
            3 to 3 -> listOf(
                "You make it look easy.",
                "Smooth pass. Rare precision.",
                "Cleanly done at the sharpest scale."
            )
            3 to 4 -> listOf(
                "Steady hands! Now the real test.",
                "Three quests deep at the top tier. Two to go.",
                "Poise under pressure. Final stretch ahead."
            )
            3 to 5 -> listOf(
                "One step from the crown.",
                "Last quest before the finish line.",
                "Everything you’ve built — one quest away from complete."
            )
            else -> listOf("Quest done!")
        }
        val idx = (System.currentTimeMillis() / 10_000L).toInt()
        return bank[((idx % bank.size) + bank.size) % bank.size]
    }

    /** Retained for source compatibility; the general/rotating tip layer is gone. */
    fun hasGeneralPart(trigger: CheerUpTrigger): Boolean = false

    /** Thin-space thousands separator (1543 → "1 543"). */
    private fun fmt(n: Int): String =
        if (n >= 1000) "%,d".format(n).replace(',', ' ') else n.toString()

    /** Retained so existing call sites still compile. */
    val generalTexts: List<String> = emptyList()
}
