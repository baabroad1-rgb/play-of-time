package online.playoftime.features.rival

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Doppelgänger's hand and head — Kotlin port of tools/micro_player/micro_player.js
 * (character "fly") and tools/micro_player/mushroom_body.js.
 *
 * Hand. He does NOT know his own duration. Like a person he has an inner clock
 * with Weber noise: press = (intention + trueBias)·(1 + N(0, cv)) + motor; his
 * own bias he is unaware of (rushes or drags), slowly drifting; practice makes
 * the hand steadier (cv falls from [cv0] to [cvBest]) — that is the body, not
 * the mind.
 *
 * Head — learns to learn (Alex 08.11.2026: «какие основания считать это ИИ?…
 * а насколько сложно реализовать это в виде минимальной нейронной сеточки?…
 * выгрузили же структуру мозга дрозофилы»). Built like the fly's learning
 * centre, the mushroom body — not an emulation of the fly brain (that one does
 * not learn and cannot count 3 s), but the circuit a fly uses to tie an odour
 * to a reward:
 *   • inputs (projection neurons) — what he saw on the counter lately: miss in
 *     the level's cells, side of the last three, a streak, "bigger than usual",
 *     the precision; rates 0…1;
 *   • [N_KC] Kenyon cells, each listening to [FAN_IN] random inputs; only the 5 %
 *     most excited fire (the APL neuron does that in a fly) — a sparse code;
 *   • outputs — votes for how much of the seen miss to correct by: [STEPS].
 *     He chooses not when to press, but the step of his own learning;
 *   • dopamine — after the next attempt: how much smaller than usual the miss
 *     came out (+ for an exact three); every output learns for its own option
 *     ("what if I had corrected otherwise"). Only Kenyon cell → output synapses
 *     learn, like in a fly. The wiring into Kenyon cells is random and fixed —
 *     rebuilt from the birth seed, not stored.
 * He is NOT told his own noise (the old Kalman version was) — in the simulator
 * he still nearly catches up with that Kalman (tools/micro_player/README.md).
 *
 * Diary — rule-based (no language model) first-person notes at noticeable
 * moments, plus every 100 attempts: how he corrects now in three situations,
 * and when that changed noticeably — real changes in his learning.
 *
 * State: small things in [toJson]/[fromJson]; the synapses ([w], 14 000
 * floats) in a binary file — [writeBrain]/[readBrain].
 */
class MicroPlayer private constructor(
    private val aimMs: Int,
    private val birthSeed: Int,
    private var rngState: Int,
) {
    private val cv0 = 0.025
    private val cvBest = 0.009
    private val practiceTau = 700.0
    private val motorMs = 3.0
    private val drift = 1.0

    var trueBias = 0.0
        private set
    /** What he applies against his bias — the sum of his own corrections. */
    var biasHat = 0.0
        private set
    var n = 0                              // attempts made
        private set
    private var digitsCell = -1
    private var sinceClean = 0
    private var cleanTotal = 0
    private val said = HashMap<String, Int>()
    private val hist = ArrayDeque<Seen>()

    private data class Seen(val err: Double, val cell: Int, val step: Double, val side: Int)

    // ── mushroom body ───────────────────────────────────────────────────────
    private val kcIn = IntArray(N_KC * FAN_IN)
    private val kcW = FloatArray(N_KC * FAN_IN)
    private var w = FloatArray(N_KC * STEPS.size)
    private var avgAbs = -1.0              // usual size of a miss, -1 = not yet
    private var pendActive: IntArray? = null
    private var pendJ = 0
    private var pendPrevErr = 0.0
    private var pendAll = DoubleArray(STEPS.size)
    private data class Choice(val k: Double, val side: Int, val prevSide: Int, val cells: Double)
    private val recent = ArrayDeque<Choice>()
    private val habitsSaid = HashMap<String, Int>()

    init {
        // Wiring PN → Kenyon cell: random and fixed, from the birth seed.
        var s = birthSeed xor 0x2545F491
        fun wire(): Double {
            s += 0x6D2B79F5
            var t = (s xor (s ushr 15)) * (1 or s)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
        for (i in 0 until N_KC * FAN_IN) {
            kcIn[i] = (wire() * N_PN).toInt().coerceAtMost(N_PN - 1)
            kcW[i] = (0.5 + wire()).toFloat()
        }
    }

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
        val side = if (seenErrMs < 0) -1 else if (seenErrMs > 0) 1 else 0
        val cur = Seen(seenErrMs, cellMs, 0.0, side)
        val step = learnAndChoose(hist.takeLast(4) + cur, win)
        biasHat += step
        hist.addLast(cur.copy(step = step))
        while (hist.size > HIST) hist.removeFirst()
        return reflect(seenErrMs, cellMs, side, win)
    }

    // ── the mushroom body at work ───────────────────────────────────────────

    private fun features(h: List<Seen>): FloatArray {
        val pn = FloatArray(N_PN)
        val last = h.last()
        val cell = last.cell
        val lc = log2(1.0 + abs(last.err) / cell)
        for (k in 0 until 6) pn[k] = exp(-((lc - k) * (lc - k)) / 0.5).toFloat()
        for (j in 0 until 3) {
            val x = h.getOrNull(h.size - 1 - j) ?: continue
            pn[6 + 3 * j + (x.side + 1)] = 1f
        }
        var run = 0
        if (last.side != 0) {
            var j = h.size - 1
            while (j >= 0 && h[j].side == last.side) { run++; j-- }
        }
        if (run > 0) pn[15 + minOf(run, 3) - 1] = 1f
        val rel = if (avgAbs > 0) abs(last.err) / maxOf(avgAbs, 0.5) else 1.0
        val rl = log2(rel + 1e-3)
        doubleArrayOf(-1.5, 0.0, 1.0, 2.0).forEachIndexed { k, c ->
            pn[18 + k] = exp(-((rl - c) * (rl - c)) / 0.6).toFloat()
        }
        pn[22 + if (cell >= 100) 0 else if (cell >= 10) 1 else 2] = 1f
        pn[25] = 0.5f
        return pn
    }

    /** Sparse code: the [N_ACTIVE] most excited Kenyon cells (quickselect, no sort). */
    private fun encode(pn: FloatArray): IntArray {
        val a = FloatArray(N_KC)
        for (i in 0 until N_KC) {
            var s = 0f
            for (f in 0 until FAN_IN) s += pn[kcIn[i * FAN_IN + f]] * kcW[i * FAN_IN + f]
            a[i] = s + (next() * 1e-3).toFloat()   // break ties
        }
        val threshold = kthLargest(a.copyOf(), N_ACTIVE)
        val out = IntArray(N_ACTIVE)
        var c = 0
        for (i in 0 until N_KC) if (a[i] >= threshold && c < N_ACTIVE) out[c++] = i
        return if (c == N_ACTIVE) out else out.copyOf(c)
    }

    private fun kthLargest(x: FloatArray, k: Int): Float {
        var lo = 0
        var hi = x.size - 1
        val target = k - 1                  // index in descending order
        while (lo < hi) {
            val pivot = x[(lo + hi) ushr 1]
            var i = lo
            var j = hi
            while (i <= j) {
                while (x[i] > pivot) i++
                while (x[j] < pivot) j--
                if (i <= j) { val t = x[i]; x[i] = x[j]; x[j] = t; i++; j-- }
            }
            if (target <= j) hi = j else if (target >= i) lo = i else break
        }
        return x[target]
    }

    private fun values(active: IntArray): DoubleArray {
        val v = DoubleArray(STEPS.size)
        for (i in active) for (q in STEPS.indices) v[q] += w[i * STEPS.size + q].toDouble()
        for (q in STEPS.indices) v[q] /= active.size
        return v
    }

    /** Dopamine for the last choice, then a new choice. Returns the correction (ms). */
    private fun learnAndChoose(h: List<Seen>, win: Boolean): Double {
        val last = h.last()
        val absErr = abs(last.err)
        val halfCell = last.cell / 2.0
        val active0 = pendActive
        if (active0 != null) {
            val norm = maxOf(if (avgAbs > 0) avgAbs else if (absErr > 0) absErr else 1.0, halfCell)
            // "What if I had corrected otherwise?" — the next miss for another
            // step is predictable: e' = e + (k_chosen − k_other)·e_previous.
            for (q in STEPS.indices) {
                val e2 = last.err + (STEPS[pendJ] - STEPS[q]) * pendPrevErr
                val rq = -abs(e2) / norm + if (abs(e2) <= halfCell) 0.5 else 0.0
                val dq = (ETA * (rq - pendAll[q])).toFloat()
                for (i in active0) w[i * STEPS.size + q] += dq
            }
        }
        val a = maxOf(absErr, halfCell)
        avgAbs = if (avgAbs < 0) a else 0.97 * avgAbs + 0.03 * a

        val active = encode(features(h))
        val v = values(active)
        val mx = v.max()
        val p = DoubleArray(STEPS.size) { exp((v[it] - mx) / TEMP) }
        val sum = p.sum()
        var u = next() * sum
        var j = 0
        while (j < STEPS.size - 1) { u -= p[j]; if (u <= 0) break; j++ }
        pendActive = active
        pendJ = j
        pendPrevErr = last.err
        pendAll = v
        // for the diary — not the random pick but the expected step by current votes
        val ek = STEPS.indices.sumOf { p[it] * STEPS[it] } / sum
        val prevSide = if (h.size > 1) h[h.size - 2].side else 0
        recent.addLast(Choice(ek, last.side, prevSide, absErr / last.cell))
        while (recent.size > 100) recent.removeFirst()
        return STEPS[j] * last.err
    }

    private fun habits(): Map<String, Int?> {
        fun avg(f: (Choice) -> Boolean): Int? {
            val x = recent.filter(f)
            return if (x.size >= 8) (x.sumOf { it.k } / x.size * 100).roundToInt() else null
        }
        return mapOf(
            "repeat" to avg { it.side != 0 && it.side == it.prevSide },
            "switched" to avg { it.side != 0 && it.prevSide != 0 && it.side != it.prevSide },
            "near" to avg { it.cells <= 1 },
        )
    }

    // ── diary ───────────────────────────────────────────────────────────────

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
            } else "Starting out. I count to three the way I can and watch what the counter says. " +
                "How much to correct after a miss — I don't know yet either; I'll find out."
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
        val last3 = h.takeLast(3)
        if (side != 0 && last3.size == 3 && last3.all { it.side == side } &&
            !(h.size > 3 && h[h.size - 4].side == side)) {
            val mean = last3.sumOf { it.err } / 3.0
            once("streak", 15,
                "Third ${if (side < 0) "early" else "late"} in a row, ${fmt(mean)} on average. " +
                    "Looks like I ${if (side < 0) "rush" else "drag"}, so I'll ${if (side < 0) "hold longer" else "let go sooner"}.")
        }
        if (prev != null && prev.side != 0 && side != 0 && side != prev.side &&
            abs(seenErr) > abs(prev.err) * 1.3 &&
            abs(prev.step) >= maxOf(cell.toDouble(), 0.4 * abs(prev.err))) {
            once("overshoot", 10,
                "Overdid it: after an ${if (prev.err < 0) "early" else "late"} miss I corrected by ${fmt(prev.step)} " +
                    "and flew to the other side (${sgn(seenErr)}).")
        }
        if (prev != null && side != 0 && prev.side != 0 && abs(abs(seenErr) - abs(prev.err)) < 0.5) {
            once("twin", 40, "Repeated my last miss exactly (${fmt(seenErr)}). The hand remembers — if only I knew what.")
        }
        // every 100 attempts — how he learns now; a change only if noticeable (≥ 15 points)
        if (n % 100 == 0) {
            val hb = habits()
            val names = listOf(
                "repeat" to "after a second miss on the same side",
                "switched" to "after jumping to the other side",
                "near" to "within a cell of three",
            )
            val parts = mutableListOf<String>()
            val changed = mutableListOf<String>()
            for ((key, label) in names) {
                val v = hb[key] ?: continue
                parts += "$label by $v%"
                val was = habitsSaid[key]
                if (was != null && abs(v - was) >= 15) changed += "$label by $v% instead of $was%"
            }
            val first = habitsSaid.isEmpty()
            if (changed.isNotEmpty()) out += "I've changed how I learn: now I correct ${changed.joinToString("; ")}."
            else if (first && parts.isNotEmpty()) out += "How I learn so far: I correct ${parts.joinToString("; ")} of the miss."
            if (changed.isNotEmpty() || (first && parts.isNotEmpty())) {
                hb.forEach { (k, v) -> if (v != null) habitsSaid[k] = v }
            }
        }
        if (n % 25 == 0 && h.isNotEmpty()) {
            val w25 = h.takeLast(25)
            val hit = w25.count { it.side == 0 }
            val e = w25.count { it.side < 0 }
            val l = w25.count { it.side > 0 }
            val m = w25.sumOf { it.err } / w25.size
            val sd = sqrt(w25.sumOf { (it.err - m) * (it.err - m) } / maxOf(1, w25.size - 1))
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

    // ── persistence ─────────────────────────────────────────────────────────

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 2)
        put("aim", aimMs); put("birth", birthSeed); put("rng", rngState)
        put("trueBias", trueBias); put("biasHat", biasHat); put("n", n)
        put("digits", digitsCell); put("sinceClean", sinceClean); put("cleanTotal", cleanTotal)
        put("said", JSONObject().apply { said.forEach { (k, v) -> put(k, v) } })
        put("hist", JSONArray().apply {
            hist.forEach { put(JSONArray().put(it.err).put(it.cell).put(it.step).put(it.side)) }
        })
        put("avgAbs", avgAbs)
        pendActive?.let { act ->
            put("pend", JSONObject()
                .put("a", JSONArray().apply { act.forEach { put(it) } })
                .put("j", pendJ).put("e", pendPrevErr)
                .put("v", JSONArray().apply { pendAll.forEach { put(it) } }))
        }
        put("recent", JSONArray().apply {
            recent.forEach { put(JSONArray().put(it.k).put(it.side).put(it.prevSide).put(it.cells)) }
        })
        put("habits", JSONObject().apply { habitsSaid.forEach { (k, v) -> put(k, v) } })
    }

    /** Synapses Kenyon cell → outputs, the only thing he learns. */
    fun writeBrain(file: File) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(w.size)
            for (x in w) out.writeFloat(x)
        }
        tmp.renameTo(file)
    }

    fun readBrain(file: File) {
        if (!file.exists()) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { inp ->
                val size = inp.readInt()
                if (size == w.size) for (i in 0 until size) w[i] = inp.readFloat()
            }
        }
    }

    companion object {
        private const val HIST = 25
        private const val N_PN = 26
        private const val N_KC = 2000
        private const val FAN_IN = 6
        private const val N_ACTIVE = 100           // 5 %
        private const val ETA = 0.05
        private const val TEMP = 0.03
        private val STEPS = doubleArrayOf(0.0, 0.10, 0.20, 0.35, 0.50, 0.75, 1.00)

        private fun log2(x: Double) = ln(x) / ln(2.0)

        /**
         * A new Doppelgänger: his inborn bias is random — he may rush or drag by
         * 60–180 ms (live novices rush by ~100–200 ms on L1); his synapses are
         * all zero — he knows nothing about how to correct.
         */
        fun newborn(seed: Int, aimMs: Int): MicroPlayer {
            val mp = MicroPlayer(aimMs, birthSeed = seed, rngState = seed)
            val sign = if (mp.next() < 0.5) -1.0 else 1.0
            mp.trueBias = sign * (60.0 + 120.0 * mp.next())
            return mp
        }

        /** Null if the saved state is of the old (Kalman) kind — he is then born anew. */
        fun fromJson(o: JSONObject): MicroPlayer? {
            if (o.optInt("v") != 2) return null
            val mp = MicroPlayer(o.getInt("aim"), o.getInt("birth"), o.getInt("rng"))
            mp.trueBias = o.getDouble("trueBias")
            mp.biasHat = o.getDouble("biasHat")
            mp.n = o.getInt("n")
            mp.digitsCell = o.optInt("digits", -1)
            mp.sinceClean = o.optInt("sinceClean")
            mp.cleanTotal = o.optInt("cleanTotal")
            o.optJSONObject("said")?.let { s -> s.keys().forEach { k -> mp.said[k] = s.getInt(k) } }
            o.optJSONArray("hist")?.let { a ->
                for (i in 0 until a.length()) {
                    val r = a.getJSONArray(i)
                    mp.hist.addLast(Seen(r.getDouble(0), r.getInt(1), r.getDouble(2), r.getInt(3)))
                }
            }
            mp.avgAbs = o.optDouble("avgAbs", -1.0)
            o.optJSONObject("pend")?.let { p ->
                val a = p.getJSONArray("a")
                mp.pendActive = IntArray(a.length()) { a.getInt(it) }
                mp.pendJ = p.getInt("j")
                mp.pendPrevErr = p.getDouble("e")
                val v = p.getJSONArray("v")
                mp.pendAll = DoubleArray(STEPS.size) { v.optDouble(it, 0.0) }
            }
            o.optJSONArray("recent")?.let { a ->
                for (i in 0 until a.length()) {
                    val r = a.getJSONArray(i)
                    mp.recent.addLast(Choice(r.getDouble(0), r.getInt(1), r.getInt(2), r.getDouble(3)))
                }
            }
            o.optJSONObject("habits")?.let { s -> s.keys().forEach { k -> mp.habitsSaid[k] = s.getInt(k) } }
            return mp
        }
    }
}
