package online.playoftime.features.rival

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Недельная запись Doppelgänger'а — размышление в духе ВПФ, а не пересказ цифр
 * (Alex 11.11.2026: «пересказ численных показателей без всякой рефлексии…
 * сделай еженедельным… размышления человека в концепции ВПФ — какие
 * инструменты мне придумать, чтобы эффективнее собой управлять»).
 * Перенос tools/micro_player/weekly_reflection.js: линия орудия из недели в
 * неделю (заметил → придумал → костыль → вросло / бросил и другое), настоящая
 * перемена его привычки поправляться, главная трудность — по перекосу,
 * корреляции соседних промахов (r1), усталости к концу сессии.
 */
object WeeklyReflection {

    data class Week(
        val index: Int, val attempts: Int, val exact: Int, val early: Int, val late: Int,
        val meanMs: Double, val sdMs: Double, val r1: Double, val fatigue: Double,
        val questsDone: List<String>, val windowsFailed: Int, val levelUp: String?,
        val habitStart: Map<String, Int>?, val habitEnd: Map<String, Int>?,
        // Конкретика недели (Alex 12.11.2026: «дневник по-прежнему тупой» —
        // в настоящих дневниках тренировок есть день, момент, причина с
        // сомнением, сверка с прошлой неделей и один проверяемый план).
        val bestDay: String? = null, val bestDayExact: Int = 0, val bestDayAttempts: Int = 0,
        val worstDay: String? = null, val worstDayExact: Int = 0, val worstDayAttempts: Int = 0,
        val worstDayTired: Boolean = false, val worstDayLongest: Boolean = false,
        val longestRun: Int = 0, val longestRunDay: String? = null,
        /** Квест → день недели и номер попытки в сессии, которой он взят. */
        val questMoments: List<Triple<String, String, Int>> = emptyList(),
        val deepestStep: Int = 0, val sessions: Int = 0,
        /** Промахи недели со знаком, мс (без точных) — для орудия «угадать до цифры». */
        val errors: List<Double> = emptyList(),
    )

    /**
     * Линия выращивания ВПФ (Alex 12.11.2026: «задача дневника — показать пример
     * выращивания ВПФ. Где там ВПФ?»). Выготский: натуральная функция
     * («тройка со мной случается») становится произвольной через знак-орудие,
     * которое сначала снаружи, потом вращивается. Шаги вращивания — по
     * Гальперину: 1 — придумал орудие и правило (ориентировка); 2 —
     * материализованное (рука, предмет, метка); 3 — громкая речь; 4 — речь про
     * себя; 5 — свёрнутое: орудия не видно, функция стала своей.
     * Шаг вперёд — когда трудность не выросла; шаг назад — когда после шага
     * стало хуже («отпустил рано»); три недели без толку на внешних шагах —
     * орудие не то, бросаю. [mastered] — что уже стало своим.
     */
    data class Arc(
        val issue: String, val stage: Int, val weeks: Int, val start: Double, val lastStage: Double,
        val mastered: List<String> = emptyList(), val failed: List<String> = emptyList(),
        // Первое орудие у всех — по Геллерштейну: до цифры сказать себе
        // «длинно»/«коротко» и сверить (Alex 12.11.2026: «поищи пример
        // образования ВПФ… и по образцу»). [guessTheta] — с какого промаха (мс)
        // он уже чувствует сторону: меньше — угадывает как монетка; порог
        // сжимается с каждой неделей практики.
        val guessTheta: Double = 0.0, val guessWeek: Int = 0, val guessDone: Boolean = false,
        val guessPrev: Double = 0.0,
    )

    private class Tool(
        val noticed: (Week) -> String,
        /** Что придумал и зачем — шаг 1. */
        val idea: String,
        /** Шаги 2–5: внешнее средство, вслух, про себя, свёрнутое. */
        val material: String, val loud: String, val inner: String, val folded: String,
        val name: String,
        val metric: (Week) -> Double, val phrase: (Double, Double) -> String,
    )

    private fun pct(a: Int, b: Int) = "${(100.0 * a / maxOf(1, b)).roundToInt()}%"

    private val TOOLS = mapOf(
        "rush" to Tool(
            { w -> "My three runs short: ${pct(w.early, w.early + w.late)} of my misses this week were early. The feeling of “three seconds” simply happens to me — I don’t control it, it controls me." },
            "If I can’t trust the feeling, I need something outside it that I can trust: a rhythm. One-and, two-and, three.",
            "tapping the rhythm with a finger on the table — the beat is in my hand, not in my head yet",
            "counting out loud, one-and, two-and, three",
            "counting silently, lips closed",
            "only the “three” is left — I don’t count to it any more, I hear where it is",
            "the rhythm",
            { w -> abs(w.meanMs) }, { a, b -> "early by ${b.roundToInt()} ms on average, it was ${a.roundToInt()}" }),
        "drag" to Tool(
            { w -> "I hold on too long, as if waiting for permission — ${pct(w.late, w.early + w.late)} of my misses were late. The stop comes when it comes; I don’t give it." },
            "I need to give the stop an order from outside: a word, “now”, decided before I start.",
            "a finger raised before the start that drops on “now” — the order is in my hand",
            "saying “now” out loud on three",
            "saying “now” to myself",
            "no word any more — the release is simply decided before I start",
            "the word “now”",
            { w -> abs(w.meanMs) }, { a, b -> "late by ${b.roundToInt()} ms on average, it was ${a.roundToInt()}" }),
        "overshoot" to Tool(
            { _ -> "I chase every miss: one late, and the next one flies early, further than I was. The correction is a reflex — it does itself." },
            "A reflex is beaten by a rule: correct by half the miss, never by all of it.",
            "the rule written on a note next to the phone — “½” — and I look at it after each miss",
            "saying “half” out loud after a miss",
            "saying “half” to myself",
            "the note is gone and so is the word; the corrections just came out smaller",
            "the rule of half",
            { w -> -w.r1 * 100 }, { a, b -> if (b < a) "I jump across less often" else "I jump across as often as before" }),
        "stuck" to Tool(
            { _ -> "I get stuck on one side: three, four misses the same way before I move. I don’t notice the series — it notices me." },
            "I need a sign that interrupts the series from outside: after the second miss on one side, change something on purpose.",
            "a mark — I touch the other edge of the screen after a second miss on the same side",
            "saying “switch” out loud after the second miss",
            "saying “switch” to myself",
            "no mark, no word — I just don’t stay on one side for long",
            "the “switch” sign",
            { w -> w.r1 * 100 }, { a, b -> if (b < a) "the runs on one side are shorter" else "the runs on one side are as long as before" }),
        "tire" to Tool(
            { _ -> "My last attempts in a session are worse than my first — the hand goes soft before I notice. Tiredness decides when I stop, not me." },
            "Then the decision to stop has to be taken out of the tired moment: a number of attempts fixed before the session.",
            "the number written down before the session, and every attempt ticked off",
            "saying out loud how many are left",
            "keeping the count to myself",
            "no count — I feel the moment to stop before the hand goes soft",
            "the stop count",
            { w -> w.fatigue * 100 }, { a, b -> if (b < a) "the end of a session is closer to its start" else "the end of a session is still worse" }),
        "spread" to Tool(
            { _ -> "There is hardly any lean left; what holds me back is how differently each three comes out. Each start is different, and I don’t choose it." },
            "If the start is the same every time, the end can be too. So the start needs a form I can repeat: a small ritual.",
            "the ritual done with the body — hand flat on the table, breath out, then the press",
            "saying “breathe — go” out loud",
            "saying “breathe — go” to myself",
            "the ritual folded into one breath; nobody watching would see it",
            "the starting ritual",
            { w -> w.sdMs }, { a, b -> "the spread is ${b.roundToInt()} ms, it was ${a.roundToInt()}" }),
    )

    fun issues(w: Week): List<String> {
        val misses = w.early + w.late
        val out = mutableListOf<String>()
        if (misses >= 20 && (w.early >= 2 * w.late || w.meanMs < -0.5 * w.sdMs)) out += "rush"
        if (misses >= 20 && (w.late >= 2 * w.early || w.meanMs > 0.5 * w.sdMs)) out += "drag"
        if (w.r1 < -0.2) out += "overshoot"
        if (w.r1 > 0.25) out += "stuck"
        if (w.fatigue > 0.15) out += "tire"
        out += "spread"
        return out
    }

    private fun cap(x: String) = x.replaceFirstChar { it.uppercase() }
    private fun ord(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
    private fun oneIn(exact: Int, att: Int) = if (exact == 0) "none" else "one in ${(att.toDouble() / exact).roundToInt()}"

    private fun form(t: Tool, stage: Int) = when (stage) { 2 -> t.material; 3 -> t.loud; 4 -> t.inner; else -> t.folded }

    private val STAGE_NOTE = mapOf(
        2 to "At first it gets in the way — attention goes on the prop, not the press. That is the price; for now it has to live outside me before it can live inside.",
        3 to "My own voice now does what my hand did. Less of a prop, still a prop.",
        4 to "Nobody would see it from outside any more; the help has moved in, but I still have to switch it on.",
        5 to "This is the part I wanted: not a trick I remember, but how I now am. Doing it the old way, from outside, would only throw me now.",
    )

    /** Пропущенное звено, найденное в медленной развёрнутой форме. */
    private val LINKS = listOf(
        "I stop listening after “two” — the last second goes by on its own.",
        "I decide the stop before the press, and then don’t hear the stop itself.",
        "on a miss I look at the number before I’ve said my guess — the guess comes after, so it isn’t one.",
    )

    fun entry(w: Week, arcIn: Arc?, prev: Week? = null): Pair<String, Arc?> {
        if (w.attempts < 20) return "I hardly played this week — too little to learn anything about myself from." to arcIn
        val s = mutableListOf<String>()

        // 1. Момент недели — один конкретный факт.
        val qm = w.questMoments
        s += when {
            w.levelUp != null -> "The week the counter switched to ${w.levelUp} — everything I thought I knew about my three got ten times finer."
            qm.size >= 2 -> "Quests ${qm.first().first} to ${qm.last().first} fell this week; the last one on ${qm.last().second}, ${qm.last().third} attempts into the session."
            qm.size == 1 -> "Quest ${qm[0].first} fell on ${qm[0].second}, on the ${ord(qm[0].third)} attempt of the session."
            w.longestRun >= 3 && w.longestRunDay != null -> "Best moment: on ${w.longestRunDay}, ${w.longestRun} exact threes in a row."
            w.bestDay != null && w.bestDayExact > 0 -> "${w.bestDay} was my best day: ${w.bestDayExact} exact out of ${w.bestDayAttempts}."
            w.exact == 0 -> "${w.attempts} attempts and not one exact three."
            else -> "About one exact three in ${(w.attempts.toDouble() / w.exact).roundToInt()} this week."
        }
        if (prev != null && prev.attempts >= 20) {
            val now = w.exact.toDouble() / w.attempts
            val was = prev.exact.toDouble() / prev.attempts
            if (now > was * 1.2) s += "Exact threes: ${oneIn(w.exact, w.attempts)} now, ${oneIn(prev.exact, prev.attempts)} last week."
            else if (now < was * 0.8) s += "Exact threes: ${oneIn(w.exact, w.attempts)} now against ${oneIn(prev.exact, prev.attempts)} last week — a step back."
        }

        // 2. Натуральное против культурного: то, что выросло само, без орудия.
        val names = mapOf("repeat" to "after a second miss on the same side",
            "switched" to "after jumping to the other side", "near" to "when I’m within a step of three")
        val hs = w.habitStart; val he = w.habitEnd
        if (hs != null && he != null) {
            names.keys.mapNotNull { k ->
                val a = hs[k]; val b = he[k]
                if (a != null && b != null && abs(b - a) >= 12) Triple(k, a, b) else null
            }.maxByOrNull { abs(it.third - it.second) }?.let { (k, a, b) ->
                s += "Something also changed by itself: ${names[k]} I now correct by about $b% of the miss, not $a%. " +
                    "I didn’t decide that — it grew the way habits grow, from repetition. That is the difference from my tools: those I build on purpose, and they stay mine to use."
            }
        }

        // 3а. Орудие Геллерштейна — угадать сторону до цифры. Параллелограмм
        // Леонтьева: сначала мешает, потом помогает, вросшее — снаружи сбивает.
        // Развалилось — назад к медленной развёрнутой форме (Гальперин).
        val g = arcIn
        if (g == null || !g.guessDone) {
            val theta = if (g == null) 1.2 * w.sdMs else g.guessTheta * 0.72
            fun rate(e: List<Double>) = if (e.isEmpty()) 0.5 else (e.count { abs(it) > theta } + 0.5 * e.count { abs(it) <= theta }) / e.size
            val r = rate(w.errors)
            val rs = rate(w.errors.filter { it < 0 }); val rl = rate(w.errors.filter { it > 0 })
            fun of20(x: Double) = "${(x * 20).roundToInt()} of 20"
            val week = (g?.guessWeek ?: -1) + 1
            when {
                g == null -> {
                    s += "The three happens to me: I press and find out from the number, not from inside. So, an experiment — before looking, I tell myself “long” or “short”."
                    s += "Right ${of20(r)}; a coin would do about as well. " + if (prev != null && w.exact < prev.exact) "And fewer exact threes: half my attention goes on guessing. Fine — that is how a new tool starts." else "And it eats attention — the press itself gets sloppier. Fine — that is how a new tool starts."
                }
                r < g.guessPrev - 0.05 -> {
                    val link = LINKS[w.index % LINKS.size]
                    s += "Guessing fell apart${w.worstDay?.let { " on $it" } ?: ""}: ${of20(r)}, it was ${of20(g.guessPrev)}. Trying harder won’t help, so back to slow: five attempts with a long pause, and on each I say what I feel in the last second. Found it: $link"
                }
                r < 0.8 -> {
                    val (worse, better) = if (rl < rs) "“long”" to "“short”" else "“short”" to "“long”"
                    s += "Guessing ${of20(r)}. Where I go wrong is the interesting part: $better I know at once, $worse I mix up with “exact”."
                    s += if (rl < rs) "So what I need is not a general rhythm but a mark for the end." else "So it’s the start that I don’t feel, not the end."
                    if (week >= 2) s += "Am I really feeling it, or just learning the odds? I tried a few with eyes off the number for a while afterwards — the guess holds, so it’s not only the odds."
                }
                else -> {
                    s += "I hardly guess any more — I know which it was before the number; no words needed. I tried saying it out loud on purpose — it threw me, two misses in a row. A support that has grown in only gets in the way from outside."
                    s += "So that is what the three is now: not something that happens to me but something I hear."
                }
            }
            val done = g != null && r >= 0.8
            val nextArc = Arc(g?.issue ?: "", 0, 0, 0.0, 0.0, g?.mastered.orEmpty(), g?.failed.orEmpty(),
                guessTheta = theta, guessWeek = week, guessDone = done, guessPrev = r)
            if (!done) return s.joinToString(" ") to nextArc
            // Вросло — дальше трудность, которую теперь слышно.
            val first = issues(w)[0]
            val t = TOOLS.getValue(first)
            s += "What next? " + t.noticed(w) + " " + t.idea + " This week: " + t.material + "."
            return s.joinToString(" ") to nextArc.copy(issue = first, stage = 2, start = t.metric(w), lastStage = t.metric(w),
                mastered = nextArc.mastered + "hearing the three")
        }

        // 3. Линия орудия: шаг вращивания и его доказательство.
        val list = issues(w)
        var arc = arcIn
        fun startNew(k: String, lead: String) {
            val t = TOOLS.getValue(k)
            s += lead + t.noticed(w)
            s += t.idea + " This week: " + t.material + "."
            arc = (arcIn ?: Arc(k, 2, 0, 0.0, 0.0)).copy(issue = k, stage = 2, weeks = 0, start = t.metric(w), lastStage = t.metric(w))
        }
        val a = arc
        if (a == null) {
            startNew(list[0], "")
        } else {
            val t = TOOLS.getValue(a.issue)
            val now = t.metric(w)
            when {
                a.stage >= 5 -> {
                    // Своё. Следующая трудность — на его основе.
                    val mastered = a.mastered + t.name
                    val next = list.firstOrNull { it != a.issue && TOOLS.getValue(it).name !in mastered && TOOLS.getValue(it).name !in a.failed }
                    if (next == null) {
                        s += "${cap(t.name)} is mine and stays. No new tool this week — I watch how it goes and try not to fiddle with what works."
                        arc = a.copy(weeks = a.weeks + 1)
                    } else {
                        s += "${cap(t.name)} is mine now — I can lean on it without thinking, and that frees attention for the next thing."
                        arc = a.copy(mastered = mastered)
                        startNew(next, "")
                        arc = arc!!.copy(mastered = mastered)
                    }
                }
                now > a.lastStage * 1.1 && a.stage > 2 -> {
                    // Отпустил рано — шаг назад к более внешней форме.
                    s += "Last week I tried ${form(t, a.stage)}, and it got worse: ${t.phrase(a.lastStage, now)}. I let go too early. Back to ${form(t, a.stage - 1)}, and slowly — a few attempts with a long pause, to find which link I dropped. What I can do with help today, I’ll do alone later, not now."
                    arc = a.copy(stage = a.stage - 1, weeks = 0, lastStage = now)
                }
                now > a.start * 0.97 && a.weeks >= 2 && a.stage <= 3 -> {
                    // Орудие не работает — бросаю, беру другую трудность.
                    s += "Three weeks of ${t.name}, and ${t.phrase(a.start, now)}. It isn’t the right tool for me; I’m dropping it rather than pretending."
                    val failed = a.failed + t.name
                    val next = list.firstOrNull { it != a.issue && TOOLS.getValue(it).name !in failed && TOOLS.getValue(it).name !in a.mastered }
                    if (next != null) { startNew(next, "Instead: "); arc = arc!!.copy(failed = failed) }
                    else { s += "Nothing else to try for now; maybe this one only gives way to time."; arc = a.copy(weeks = 0, failed = failed, stage = 5) }
                }
                now <= a.lastStage * 1.05 -> {
                    // Держится — делаю шаг внутрь.
                    val st = a.stage + 1
                    s += "Last week: ${form(t, a.stage)}. Result — ${t.phrase(a.start, now)}. It holds, so I can take one more step inward."
                    s += if (st >= 5) "This week I drop it altogether: ${form(t, 5)}." else "This week: ${form(t, st)}."
                    STAGE_NOTE[st]?.let { s += it }
                    arc = a.copy(stage = st, weeks = 0, lastStage = now)
                }
                else -> {
                    s += "Still ${form(t, a.stage)}, and still effort — I forget it exactly when it matters (${t.phrase(a.start, now)}). Staying on this step another week."
                    arc = a.copy(weeks = a.weeks + 1)
                }
            }
        }

        // 4. Худший день — коротко, если есть что сказать.
        if (w.worstDay != null && w.worstDay != w.bestDay && w.worstDayAttempts >= 20 && w.worstDayTired)
            s += "${w.worstDay} went badly (${w.worstDayExact} exact out of ${w.worstDayAttempts}); the end of that session was worse than its start — I played on past being fresh, the tool goes first when I’m tired."
        if (w.deepestStep > 0) s += "I had to take eased terms, down to .${w.deepestStep}. Better to lose a step and keep playing than stand still."
        return s.joinToString(" ") to arc
    }
}
