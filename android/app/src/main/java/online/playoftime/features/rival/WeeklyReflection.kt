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
    )

    data class Arc(
        val issue: String, val alt: Boolean, val weeks: Int, val start: Double,
        val settled: Boolean = false, val exhausted: Boolean = false,
    )

    private class Tool(
        val noticed: (Week) -> String, val tool: String, val alt: String,
        val short: String, val altShort: String,
        val metric: (Week) -> Double, val phrase: (Double, Double) -> String,
    )

    private fun pct(a: Int, b: Int) = "${(100.0 * a / maxOf(1, b)).roundToInt()}%"

    private val TOOLS = mapOf(
        "rush" to Tool(
            { w -> "I keep stopping early — my “three” runs short; ${pct(w.early, w.early + w.late)} of my misses this week were early." },
            "I can’t trust the feeling alone yet, so I need something outside it to lean on: a counted rhythm, one-and, two-and, three — until the count turns into my own sense of time.",
            "Counting didn’t take. Instead, a picture: three steps across a room, and the stop lands on the third step, not before it.",
            "the counted rhythm", "the three steps",
            { w -> abs(w.meanMs) }, { a, b -> "on average I’m early by ${b.roundToInt()} ms now, it was ${a.roundToInt()}" }),
        "drag" to Tool(
            { w -> "I hold on too long, as if waiting for permission — ${pct(w.late, w.early + w.late)} of my misses were late." },
            "A tool for that: decide the moment of release before I start, and give it a word — “now” — that I say to myself on three.",
            "The word didn’t help. A rule instead: if I notice I’m waiting, I’m already late — let go at once.",
            "the word “now”", "the waiting rule",
            { w -> abs(w.meanMs) }, { a, b -> "on average I’m late by ${b.roundToInt()} ms now, it was ${a.roundToInt()}" }),
        "overshoot" to Tool(
            { _ -> "I chase every miss: one late, and the next one flies early, further than I was." },
            "What I need is a rule rather than a feeling: never correct by the whole miss, and never after a single result — a small step, then wait and see.",
            "A rule about size didn’t hold. A rule about time, then: change nothing until two results agree.",
            "the rule of small steps", "waiting for two results",
            { w -> -w.r1 * 100 }, { a, b -> if (b < a) "I jump across less often" else "I jump across as often as before" }),
        "stuck" to Tool(
            { _ -> "I get stuck on one side: three, four misses the same way before I move." },
            "I need a sign that interrupts me — after the second miss on the same side, change something on purpose, like a knot tied on a handkerchief.",
            "The knot didn’t work — I kept forgetting it. Decide before the attempt instead: if the last one was early, this one I hold a little longer, whatever I feel.",
            "the knot after the second miss", "deciding before the attempt",
            { w -> w.r1 * 100 }, { a, b -> if (b < a) "the runs on one side are shorter" else "the runs on one side are as long as before" }),
        "tire" to Tool(
            { _ -> "My last attempts in a session are worse than my first — the hand goes soft before I notice." },
            "A tool against that is a stop rule decided in advance: end the session while it still goes well, not after it stops going.",
            "The stop rule I kept breaking. Shorter sessions with a real pause between them, then.",
            "the stop rule", "short sessions",
            { w -> w.fatigue * 100 }, { a, b -> if (b < a) "the end of a session is closer to its start" else "the end of a session is still worse" }),
        "spread" to Tool(
            { _ -> "There is hardly any lean left; what holds me back is how differently each three comes out." },
            "I need the beginning to be the same every time — one breath, the same posture, the same first beat — a small ritual, so that if the start is identical, the end can be too.",
            "The start wasn’t it. Maybe the middle: I lose the beat around the second second — count that one out loud inside.",
            "the ritual for the start", "the counted middle",
            { w -> w.sdMs }, { a, b -> "the spread is ${b.roundToInt()} ms, it was ${a.roundToInt()}" }),
    )

    private val WEEK2 = listOf(
        "It still takes effort — I have to remind myself every time, and I forget exactly when it matters.",
        "It’s still a crutch I lean on on purpose. Fine — that’s how a tool starts, outside of me.",
        "Some days it works, some days I forget it altogether.",
    )
    private val CALM = listOf(
        "No new tool this week. I watch how it goes and try not to fiddle with what works.",
        "I left myself alone this week — sometimes the best tool is not to reach for one.",
        "Nothing new to try. The hand is learning on its own; my job is not to get in its way.",
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

    fun entry(w: Week, arcIn: Arc?, prev: Week? = null): Pair<String, Arc?> {
        if (w.attempts < 20) return "I hardly played this week — too little to learn anything about myself from." to arcIn
        var arc = arcIn
        val s = mutableListOf<String>()
        fun short(a: Arc) = if (a.alt) TOOLS.getValue(a.issue).altShort else TOOLS.getValue(a.issue).short

        // 1. Момент недели — конкретный день, а не среднее.
        val qm = w.questMoments
        s += when {
            w.levelUp != null -> "The week the counter switched to ${w.levelUp} — everything I thought I knew about my three got ten times finer."
            qm.size >= 2 -> "Quests ${qm.first().first} to ${qm.last().first} fell this week; the last one on ${qm.last().second}, ${qm.last().third} attempts into the session — I nearly gave up on it before that."
            qm.size == 1 -> "Quest ${qm[0].first} fell on ${qm[0].second}, on the ${ord(qm[0].third)} attempt of the session." +
                if (qm[0].third > 40) " It took patience; for a long while nothing was coming." else " It came quicker than I expected."
            w.longestRun >= 3 && w.longestRunDay != null -> "Best moment of the week: on ${w.longestRunDay}, ${w.longestRun} exact threes in a row. I tried to notice what that felt like — quiet, no hurry, the stop just happened."
            w.bestDay != null && w.bestDayExact > 0 -> "${w.bestDay} was my best day: ${w.bestDayExact} exact out of ${w.bestDayAttempts}."
            w.windowsFailed >= 2 -> "A hard week — ${w.windowsFailed} times the quest window closed on me before I made it."
            w.exact == 0 -> "${w.attempts} attempts and not one exact three."
            else -> "About one exact three in ${(w.attempts.toDouble() / w.exact).roundToInt()} this week."
        }

        // 2. Сверка с прошлой неделей — одна цифра и честная оценка.
        if (prev != null && prev.attempts >= 20) {
            val now = w.exact.toDouble() / w.attempts
            val was = prev.exact.toDouble() / prev.attempts
            s += when {
                now > was * 1.2 -> "Exact threes: ${oneIn(w.exact, w.attempts)} now, ${oneIn(prev.exact, prev.attempts)} last week — better, though one good week proves little."
                now < was * 0.8 -> "Exact threes: ${oneIn(w.exact, w.attempts)} now against ${oneIn(prev.exact, prev.attempts)} last week. A step back; I want to understand it rather than shrug."
                else -> "Exact threes about as often as last week, ${oneIn(w.exact, w.attempts)}."
            }
        }

        // 3. Неудачный день — с догадкой о причине и сомнением в ней.
        if (w.worstDay != null && w.worstDay != w.bestDay && w.worstDayAttempts >= 20) {
            s += "${w.worstDay} went badly — ${w.worstDayExact} exact out of ${w.worstDayAttempts}. " + when {
                w.worstDayTired -> "The end of that session was clearly worse than its start, so I think I simply played on past the point where I was fresh."
                w.worstDayLongest -> "It was my longest session of the week; maybe that's the reason, maybe not."
                else -> "I don't see a reason in it — some days the hand doesn't listen, and I'd rather not invent one."
            }
            if (w.deepestStep > 0) s += "I had to take the eased terms, down to .${w.deepestStep}. I don't mind: better to lose a step and keep playing than to stand still."
        }

        val names = mapOf("repeat" to "after a second miss on the same side",
            "switched" to "after jumping to the other side", "near" to "when I’m within a step of three")
        val hs = w.habitStart
        val he = w.habitEnd
        if (hs != null && he != null) {
            names.keys.mapNotNull { k ->
                val a = hs[k]; val b = he[k]
                if (a != null && b != null && abs(b - a) >= 12) Triple(k, a, b) else null
            }.maxByOrNull { abs(it.third - it.second) }?.let { (k, a, b) ->
                s += if (b < a) "Without quite deciding to, I made myself a brake: ${names[k]} I now correct by about $b% of the miss, not $a%."
                     else "I’ve grown bolder ${names[k]}: about $b% of the miss now, where it was $a%."
            }
        }

        val list = issues(w)
        val main = list[0]
        fun fresh(k: String, lead: String = ""): Arc {
            val t = TOOLS.getValue(k)
            s += lead + t.noticed(w)
            s += "For next week: " + t.tool.replaceFirstChar { it.lowercase() }
            return Arc(k, false, 0, t.metric(w))
        }
        val a = arc
        when {
            a == null || (a.issue != main && a.issue !in list) -> {
                if (a != null) s += "I hardly notice my old trouble anymore — ${short(a)} did its job, or practice did."
                arc = fresh(main)
            }
            a.exhausted && a.issue == main -> {
                val next = list.firstOrNull { it != a.issue && it != "spread" }
                if (next != null) arc = fresh(next, "I’ve stopped fighting the old trouble head-on. ")
                else s += CALM[w.index % CALM.size]
            }
            a.settled && a.issue == main -> {
                val t = TOOLS.getValue(a.issue)
                if (a.alt) {
                    s += "${cap(short(a))} stays with me. The trouble is still there, but I’m out of ideas for now; maybe this part only gives way to time."
                    arc = a.copy(weeks = 0, start = t.metric(w), exhausted = true)
                } else {
                    s += "${cap(short(a))} is mine now and stays."
                    val rest = t.alt.substringAfter(". ", t.alt)
                    s += "On top of it, " + rest.replaceFirstChar { it.lowercase() }
                    arc = Arc(a.issue, true, 0, t.metric(w))
                }
            }
            else -> {
                val t = TOOLS.getValue(a.issue)
                val now = t.metric(w)
                val better = now < a.start * 0.85
                val weeks = a.weeks + 1
                when {
                    weeks == 1 -> {
                        s += "Last week I gave myself ${short(a)}. What came of it: ${t.phrase(a.start, now)}${if (better) "" else " — nothing to show yet"}. ${WEEK2[w.index % WEEK2.size]}"
                        arc = a.copy(weeks = weeks)
                    }
                    better -> {
                        s += "${cap(short(a))} is turning from a rule I remind myself of into something I simply do — ${t.phrase(a.start, now)}."
                        val next = list.firstOrNull { it != a.issue }
                        if (next != null && (next != "spread" || a.issue != "spread")) arc = fresh(next, "Time for the next thing. ")
                        else { s += "Nothing else stands out; I’ll keep it and keep going."; arc = a.copy(weeks = 0, start = now, settled = true) }
                    }
                    !a.alt -> {
                        s += "${weeks + 1} weeks of ${short(a)}, and ${t.phrase(a.start, now)}. I’ll drop it."
                        s += t.alt
                        arc = Arc(a.issue, true, 0, now)
                    }
                    else -> {
                        s += "${cap(short(a))} hasn’t moved anything — ${t.phrase(a.start, now)}. Maybe this one only gives way to time."
                        arc = Arc(a.issue, true, 0, now, exhausted = true)
                    }
                }
            }
        }
        return s.joinToString(" ") to arc
    }
}
