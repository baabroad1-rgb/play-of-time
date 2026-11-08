package online.playoftime.features.rival

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Doppelgänger's hand and head — Kotlin port of tools/micro_player/micro_player.js
 * (Alex 08.11.2026: «эмулятор, который будет стараться попасть в три секунды
 * независимо от игрока, но не через управление счётчиком, а на основании
 * обратной связи»).
 *
 * He does NOT know his own duration. Like a person he has
 *   • an inner clock with Weber noise: press = (intention + trueBias)·(1 + N(0, cv)) + motor;
 *   • his own bias he is unaware of (rushes or drags), slowly drifting;
 *   • practice: cv falls from [cv0] to [cvBest] with attempts.
 * After each attempt he sees only what the player sees: the counter at the
 * level's precision and the colour. A Kalman filter estimates his bias from
 * that: big steps while unsure, small ones later, no twitching on noise.
 *
 * The diary is rule-based (no language model) — first-person notes at
 * noticeable moments, each kind of thought rate-limited. English: the app UI
 * is English.
 *
 * The whole state is a plain JSON object ([toJson]/[fromJson]) so the rival
 * survives restarts and keeps learning where he stopped.
 */
class MicroPlayer private constructor(
    private val aimMs: Int,
    private val cv0: Double,
    private val cvBest: Double,
    private val practiceTau: Double,
    private val motorMs: Double,
    private val drift: Double,
    private var rngState: Int,
) {
    var trueBias = 0.0
        private set
    var biasHat = 0.0
        private set
    private var p = 200.0 * 200.0          // uncertainty of biasHat, ms²
    var n = 0                              // attempts made
        private set
    private var digitsCell = -1            // cell (ms) of the last seen precision, -1 = none yet
    private var sinceClean = 0
    private var cleanTotal = 0
    private var confident = false
    private val said = HashMap<String, Int>()
    private val hist = ArrayDeque<Seen>()  // last [HIST] attempts as seen on screen

    private data class Seen(val err: Double, val cell: Int, val step: Double, val side: Int)

    // ── RNG: mulberry32, same sequence as the JS prototype ──────────────────
    private fun next(): Double {
        rngState += 0x6D2B79F5
        var t = (rngState xor (rngState ushr 15)) * (1 or rngState)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }

    private fun normal(): Double {
        var u = 0.0
        var v = 0.0
        while (u == 0.0) u = next()
        while (v == 0.0) v = next()
        return sqrt(-2.0 * ln(u)) * cos(2.0 * Math.PI * v)
    }

    private fun cv(): Double = cvBest + (cv0 - cvBest) * exp(-n / maxOf(1.0, practiceTau))

    /** How long he holds the button — decided before he sees the result. */
    fun nextDurationMs(): Long {
        val intention = aimMs - biasHat
        val clock = (intention + trueBias) * (1.0 + normal() * cv())
        val motor = normal() * motorMs
        trueBias += normal() * drift      // slow drift: fatigue, mood
        return (clock + motor).roundToLong()
    }

    /**
     * Feedback — exactly what the screen shows: the error at the level's
     * precision ([seenErrMs] = shown − aim, a multiple of [cellMs]) and whether
     * the game painted it green. Returns new diary lines (may be empty).
     */
    fun feedback(seenErrMs: Double, cellMs: Int, win: Boolean): List<String> {
        n++
        val before = biasHat
        // Observation of his own bias: z = seenErr + biasHat. Observation noise =
        // clock noise + motor + counter rounding (cell²/12); process noise = drift.
        val r = (aimMs * cv()).let { it * it } + motorMs * motorMs + cellMs.toDouble() * cellMs / 12.0
        val q = drift * drift + 1.0
        p += q
        val k = p / (p + r)
        biasHat += k * seenErrMs
        p *= (1.0 - k)
        val step = biasHat - before
        val side = if (seenErrMs < 0) -1 else if (seenErrMs > 0) 1 else 0
        hist.addLast(Seen(seenErrMs, cellMs, step, side))
        while (hist.size > HIST) hist.removeFirst()
        return reflect(seenErrMs, cellMs, side, win)
    }

    private fun fmt(ms: Double) = "${abs(ms).roundToLong()} ms"
    private fun sgn(ms: Double) = (if (ms > 0) "+" else "−") + fmt(ms)

    private fun reflect(seenErr: Double, cell: Int, side: Int, win: Boolean): List<String> {
        val out = mutableListOf<String>()
        val h = hist.toList()
        val prev = if (h.size > 1) h[h.size - 2] else null
        fun once(kind: String, gap: Int, text: String) {
            if (n - (said[kind] ?: -1_000_000) >= gap) { said[kind] = n; out += text }
        }
        if (digitsCell != cell) {
            out += if (digitsCell != -1) {
                val lv = when (cell) { 100 -> "tenths"; 10 -> "hundredths"; 1 -> "thousandths"; else -> "" }
                "New precision: $lv. A hit now means landing within ±${halfCell(cell)} ms. Let's see what my hand is worth."
            } else "Starting out. I count to three the way I can and watch what the counter says."
            digitsCell = cell
        }
        if (side == 0) {
            cleanTotal++
            if (cleanTotal == 1) out += "Exactly three! My first clean three, on attempt $n."
            else if (sinceClean >= 25) out += "Exactly three at last, after $sinceClean misses. Changing nothing."
            sinceClean = 0
        } else {
            sinceClean++
            if (win) once("eased", 30, "Green, though not exactly three (${sgn(seenErr)}). The game is lenient with me for now — I'd rather not count on it.")
        }
        // a streak on one side
        val last3 = h.takeLast(3)
        if (side != 0 && last3.size == 3 && last3.all { it.side == side } &&
            !(h.size > 3 && h[h.size - 4].side == side)) {
            val mean = last3.sumOf { it.err } / 3.0
            once("streak", 15,
                "Third ${if (side < 0) "early" else "late"} in a row, ${fmt(mean)} on average. " +
                    "Looks like I ${if (side < 0) "rush" else "drag"}, so I'll ${if (side < 0) "hold longer" else "let go sooner"}.")
        }
        // overshoot: the correction after the last miss was a real share of it,
        // and he flew to the other side further than he was (otherwise it's noise)
        if (prev != null && prev.side != 0 && side != 0 && side != prev.side &&
            abs(seenErr) > abs(prev.err) * 1.3 &&
            abs(prev.step) >= maxOf(cell.toDouble(), 0.4 * abs(prev.err))) {
            once("overshoot", 10,
                "Overdid it: after an ${if (prev.err < 0) "early" else "late"} miss I corrected by ${fmt(prev.step)} " +
                    "and flew to the other side (${sgn(seenErr)}). Smaller steps.")
        }
        // twin: the same miss in absolute value
        if (prev != null && side != 0 && prev.side != 0 && abs(abs(seenErr) - abs(prev.err)) < 0.5) {
            once("twin", 40, "Repeated my last miss exactly (${fmt(seenErr)}). The hand remembers — if only I knew what.")
        }
        if (!confident && sqrt(p) < 15.0) {
            confident = true
            out += "I think I've got my bias: by nature I ${if (biasHat < 0) "rush" else "drag"} by about ${fmt(biasHat)}, " +
                "give or take ${sqrt(p).roundToLong()} ms. Small corrections from here — what's left is spread, not bias."
        }
        // every 25 attempts — a summary
        if (n % 25 == 0 && h.isNotEmpty()) {
            val w = h.takeLast(25)
            val hit = w.count { it.side == 0 }
            val e = w.count { it.side < 0 }
            val l = w.count { it.side > 0 }
            val m = w.sumOf { it.err } / w.size
            val sd = sqrt(w.sumOf { (it.err - m) * (it.err - m) } / maxOf(1, w.size - 1))
            val biasFirst = abs(m) > sd / 2
            val lean = maxOf(e, l) >= 6 && maxOf(e, l) >= 2 * maxOf(1, minOf(e, l))
            val lateMore = l > e
            out += "Last 25: $hit exact, $e early, $l late. " + when {
                biasFirst -> "Bias is the main thing now (${if (m < 0) "early" else "late"} by ${fmt(m)})."
                lean -> "More often ${if (lateMore) "late" else "early"} — a slight ${if (lateMore) "drag" else "rush"}, " +
                    "but the spread of ±${fmt(sd)} matters more."
                else -> "Hardly any bias — the spread of ±${fmt(sd)} is what holds me back."
            }
        }
        return out
    }

    private fun halfCell(cell: Int): String = if (cell % 2 == 0) "${cell / 2}" else "%.1f".format(cell / 2.0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("aim", aimMs); put("cv0", cv0); put("cvBest", cvBest); put("tau", practiceTau)
        put("motor", motorMs); put("drift", drift); put("rng", rngState)
        put("trueBias", trueBias); put("biasHat", biasHat); put("P", p); put("n", n)
        put("digits", digitsCell); put("sinceClean", sinceClean); put("cleanTotal", cleanTotal)
        put("confident", confident)
        put("said", JSONObject().apply { said.forEach { (k, v) -> put(k, v) } })
        put("hist", JSONArray().apply {
            hist.forEach { put(JSONArray().put(it.err).put(it.cell).put(it.step).put(it.side)) }
        })
    }

    companion object {
        private const val HIST = 25

        /**
         * A new Doppelgänger. Character: the attentive (Kalman) learner from the
         * prototype; his inborn bias is random — he may rush or drag by 60–180 ms
         * (live novices rush by ~100–200 ms on L1). Numbers from live players,
         * docs/FACE_V3_THREE_TRAITS.md.
         */
        fun newborn(seed: Int, aimMs: Int): MicroPlayer {
            val mp = MicroPlayer(aimMs, cv0 = 0.025, cvBest = 0.009, practiceTau = 700.0,
                motorMs = 3.0, drift = 1.0, rngState = seed)
            val sign = if (mp.next() < 0.5) -1.0 else 1.0
            mp.trueBias = sign * (60.0 + 120.0 * mp.next())
            return mp
        }

        fun fromJson(o: JSONObject): MicroPlayer {
            val mp = MicroPlayer(
                o.getInt("aim"), o.getDouble("cv0"), o.getDouble("cvBest"), o.getDouble("tau"),
                o.getDouble("motor"), o.getDouble("drift"), o.getInt("rng"))
            mp.trueBias = o.getDouble("trueBias")
            mp.biasHat = o.getDouble("biasHat")
            mp.p = o.getDouble("P")
            mp.n = o.getInt("n")
            mp.digitsCell = o.optInt("digits", -1)
            mp.sinceClean = o.optInt("sinceClean")
            mp.cleanTotal = o.optInt("cleanTotal")
            mp.confident = o.optBoolean("confident")
            o.optJSONObject("said")?.let { s -> s.keys().forEach { k -> mp.said[k] = s.getInt(k) } }
            o.optJSONArray("hist")?.let { a ->
                for (i in 0 until a.length()) {
                    val r = a.getJSONArray(i)
                    mp.hist.addLast(Seen(r.getDouble(0), r.getInt(1), r.getDouble(2), r.getInt(3)))
                }
            }
            return mp
        }
    }
}
