package ru.iflex.timemvp.ui_lib

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.iflex.timemvp.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Two-sided firework salute overlaid on the "Play of Time" brand mark, ported
 * 1:1 from the HTML/CSS/JS prototype approved by Alex on 2026-08-16
 * (`scratchpad/build_firework_demo33.py` + the crowd_long.wav sound pass).
 *
 * LOCKED synchronization (do not hand-tune -- see memory:
 * firework-prototype-locked-params for the full derivation):
 * - Ring and triangle are geared exactly 1:1 (one ring lap per triangle
 *   flip, [CYCLE_MS] = 4000ms) -- required so each touch angle is crossed
 *   exactly once per cycle, precisely at the touch instant.
 * - Triangle's own rotation axis is 50% (its true center) -- NOT the
 *   letter's 48.5% centroid axis; sharing that axis was the round-30 bug
 *   that made the mirrored left touch reach ~15.6px farther than the
 *   natural right touch.
 * - Ring starts at phase P0 so "O" (in "OF") sits exactly on the left
 *   touch point at the touch instant, every cycle, forever.
 */

private const val CYCLE_MS = 4000f
private const val P0_DEG = 60.515f
private const val RING_SWEEP_DEG = 360f
private const val TRIANGLE_AXIS_X_PCT = 0.5f
private const val LETTER_AXIS_X_PCT = 0.485f

private const val T_TOUCH_MS = 2062.2f       // left touch instant
private const val T_TOUCH_R_MS = 3895.6f     // right touch instant
private const val ROCKET_RISE_MS = 550f      // launch -> explosion
private const val BURST_MS = 950f            // explosion -> fragments settle/fade
private const val H_ROCKET_BASE = 170f       // shared apex height so rockets explode together
private const val APEX_TARGET_X = 160f       // ring's vertical centerline -- explosion happens here

// Touch points, 320-unit logo-space (measured via perspective-matrix theta sweep on the actual art).
private const val OX_L = 41.875f
private const val OY_L = 156.875f
private const val OX_R = 278.125f
private const val OY_R = 156.25f
private val EXPLODE_Y_L = OY_L - H_ROCKET_BASE
private val EXPLODE_Y_R = OY_R - H_ROCKET_BASE
private val EXPLODE_MS_L = (T_TOUCH_MS + ROCKET_RISE_MS).mod(CYCLE_MS)
private val EXPLODE_MS_R = (T_TOUCH_R_MS + ROCKET_RISE_MS).mod(CYCLE_MS)

private const val ROCKET_PIECES = 5
private const val BURST_PIECES = 34

// Margins around the 320-unit logo box the burst particles need to fly into
// -- tightened 2026-08-17 to the actual measured particle travel (was a flat
// 200/260 matching the HTML prototype's generous canvas, which left most of
// the reserved card area empty since fireworks only ever reach ~50 units
// sideways and ~160 units above the logo; below/around that they never go).
// Side: worst-case burst dx reaches originX(160) +/- ~210 -> up to 50 units
// past the 320-wide logo edge, +buffer for particle half-width.
private const val MARGIN_SIDE = 60f
// Top: rocket climbs to ~-33, burst adds another ~130 above that -> ~-160
// at the highest fragment, +buffer.
private const val MARGIN_TOP = 170f
// Bottom: bursts fall back to about +65 units *inside* the already-320-tall
// logo box, never reaching its bottom edge -- small buffer only.
private const val MARGIN_BOTTOM = 30f
private val LOGO_VERTICAL_BIAS = (MARGIN_TOP - MARGIN_BOTTOM) / (MARGIN_TOP + MARGIN_BOTTOM)

private val ROCKET_COLORS = listOf(Color(0xFFFFE07A), Color(0xFFFFD35E), Color.White)
private val BURST_COLORS = listOf(
    Color(0xFFE63946), Color(0xFFFF5C5C), Color(0xFF3A86FF),
    Color(0xFF4CC9F0), Color(0xFF2ECC71), Color(0xFF6BCB77)
)

/** Real projectile arc: y(t) = H * u * (2-u), u = t_frac/apexFrac -- decelerating rise, apex at
 * apexFrac of the span, symmetric fall. Same closed form for both the rocket's straight rise
 * (apexFrac=1, explodes right at the top) and the burst fragments' own mini rise-then-fall. */
private fun projectileY(height: Float, apexFrac: Float, tFrac: Float): Float {
    val u = tFrac / apexFrac
    return height * u * (2f - u)
}

private fun wobbleRotation(tFrac: Float, rot0: Float, rot1: Float, amp: Float, freq: Float, phase: Float): Float {
    val base = rot0 + (rot1 - rot0) * tFrac
    return base + amp * sin((2 * PI * freq * tFrac + phase).toFloat())
}

/** tFrac in [0,1] if `phase` currently falls in the wrap-safe window [start, start+dur) of the
 * shared CYCLE_MS clock, else null. */
private fun windowTFrac(phase: Float, start: Float, dur: Float): Float? {
    val end = start + dur
    return if (end <= CYCLE_MS) {
        if (phase in start..end) (phase - start) / dur else null
    } else {
        val wrappedEnd = end - CYCLE_MS
        when {
            phase >= start -> (phase - start) / dur
            phase < wrappedEnd -> (phase + CYCLE_MS - start) / dur
            else -> null
        }
    }
}

private class RocketSpec(
    val originX: Float, val originY: Float, val launchMs: Float,
    val height: Float, val dxLaunch: Float, val width: Float, val heightPx: Float,
    val color: Color, val rot0: Float, val rot1: Float,
    val wAmp: Float, val wFreq: Float, val wPhase: Float
)

private class BurstSpec(
    val originX: Float, val originY: Float, val explodeMs: Float,
    val dxTotal: Float, val height: Float, val apexFrac: Float, val width: Float, val heightPx: Float,
    val color: Color, val rot0: Float, val rot1: Float,
    val wAmp: Float, val wFreq: Float, val wPhase: Float
)

private fun buildRockets(rnd: Random, originX: Float, originY: Float, launchMs: Float): List<RocketSpec> =
    List(ROCKET_PIECES) {
        val rot0 = rnd.nextFloat() * 360f
        val delta = (300f + rnd.nextFloat() * 320f) * (if (rnd.nextBoolean()) 1f else -1f)
        RocketSpec(
            originX = originX, originY = originY, launchMs = launchMs,
            height = H_ROCKET_BASE + rnd.nextFloat() * 40f - 20f,
            dxLaunch = rnd.nextFloat() * 20f - 10f,
            width = 3f + rnd.nextFloat() * 2f,
            heightPx = 7f + rnd.nextFloat() * 5f,
            color = ROCKET_COLORS[rnd.nextInt(ROCKET_COLORS.size)],
            rot0 = rot0, rot1 = rot0 + delta,
            wAmp = 10f + rnd.nextFloat() * 12f,
            wFreq = 1.2f + rnd.nextFloat() * 1.2f,
            wPhase = rnd.nextFloat() * (2 * PI).toFloat()
        )
    }

private fun buildBursts(rnd: Random, originX: Float, originY: Float, explodeMs: Float): List<BurstSpec> =
    List(BURST_PIECES) {
        val sideSign = if (rnd.nextBoolean()) 1f else -1f
        val rot0 = rnd.nextFloat() * 360f
        val delta = (260f + rnd.nextFloat() * 300f) * (if (rnd.nextBoolean()) 1f else -1f)
        BurstSpec(
            originX = originX, originY = originY, explodeMs = explodeMs,
            dxTotal = sideSign * (60f + rnd.nextFloat() * 150f),
            height = 30f + rnd.nextFloat() * 100f,
            apexFrac = 0.25f + rnd.nextFloat() * 0.2f,
            width = 5f + rnd.nextFloat() * 6f,
            heightPx = 3f + rnd.nextFloat() * 3f,
            color = BURST_COLORS[rnd.nextInt(BURST_COLORS.size)],
            rot0 = rot0, rot1 = rot0 + delta,
            wAmp = 20f + rnd.nextFloat() * 25f,
            wFreq = 1.5f + rnd.nextFloat() * 1.7f,
            wPhase = rnd.nextFloat() * (2 * PI).toFloat()
        )
    }

@Composable
fun SaluteCelebration(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    // Настройка "звуки салюта" (Alex 2026-09-10) — гасит ТОЛЬКО звук, визуальный
    // фейерверк играет как обычно независимо от этого флага.
    val localStorage = org.koin.compose.koinInject<ru.iflex.timemvp.core.data.LocalStorageService>()
    val soundEnabled = remember { localStorage.getCelebrationSoundsEnabled() }

    val rockets = remember {
        val rnd = Random(31)
        buildRockets(rnd, OX_L, OY_L, T_TOUCH_MS) + buildRockets(rnd, OX_R, OY_R, T_TOUCH_R_MS)
    }
    val bursts = remember {
        val rnd = Random(37)
        buildBursts(rnd, APEX_TARGET_X, EXPLODE_Y_L, EXPLODE_MS_L) +
            buildBursts(rnd, APEX_TARGET_X, EXPLODE_Y_R, EXPLODE_MS_R)
    }

    var elapsedMs by remember { mutableFloatStateOf(0f) }
    var lastPhase by remember { mutableFloatStateOf(-1f) }
    val sound = remember { SaluteSoundPlayer(context) }

    DisposableEffect(Unit) {
        if (soundEnabled) sound.load()
        onDispose { sound.release() }
    }

    LaunchedEffect(sound) {
        if (soundEnabled) sound.startAmbient(this)
    }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanosCompat()
        while (isActive) {
            val nanos = withFrameNanosCompat()
            val dtMs = (nanos - lastNanos) / 1_000_000f
            lastNanos = nanos
            elapsedMs += dtMs
            val phase = elapsedMs.mod(CYCLE_MS)
            if (lastPhase >= 0f) {
                val crossedLeft = (lastPhase < T_TOUCH_MS && phase >= T_TOUCH_MS) ||
                    (phase < lastPhase && T_TOUCH_MS >= lastPhase)
                val crossedRight = (lastPhase < T_TOUCH_R_MS && phase >= T_TOUCH_R_MS) ||
                    (phase < lastPhase && T_TOUCH_R_MS >= lastPhase)
                if (soundEnabled && (crossedLeft || crossedRight)) sound.playSalute()
            }
            lastPhase = phase
        }
    }

    val phase = elapsedMs.mod(CYCLE_MS)
    val ringAngle = P0_DEG + (phase / CYCLE_MS) * RING_SWEEP_DEG
    val triangleAngle = (phase / CYCLE_MS) * 360f
    val letterAngle = -(phase / CYCLE_MS) * 360f

    // Bursts fly well outside the logo's own 320-unit box (prototype margins:
    // 200 each side, 260 top/bottom) -- the outer canvas must be that much
    // larger than the logo itself, with the logo centered inside it, or the
    // fireworks get clipped at the logo's own edge and most of the effect is
    // lost.
    val canvasUnitsW = 320f + 2 * MARGIN_SIDE
    val canvasUnitsH = 320f + MARGIN_TOP + MARGIN_BOTTOM

    Box(modifier.aspectRatio(canvasUnitsW / canvasUnitsH)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unitScale = size.width / canvasUnitsW
            rockets.forEach { spec ->
                val tFrac = windowTFrac(phase, spec.launchMs, ROCKET_RISE_MS) ?: return@forEach
                val dx = spec.dxLaunch * (1f - tFrac) + (APEX_TARGET_X - spec.originX) * tFrac
                val dy = -projectileY(spec.height, 1f, tFrac)
                val rot = wobbleRotation(tFrac, spec.rot0, spec.rot1, spec.wAmp, spec.wFreq, spec.wPhase)
                val pScale = 1f - 0.15f * tFrac
                drawParticle(
                    MARGIN_SIDE + spec.originX + dx, MARGIN_TOP + spec.originY + dy,
                    spec.width, spec.heightPx, rot, pScale, spec.color, unitScale
                )
            }
            bursts.forEach { spec ->
                val tFrac = windowTFrac(phase, spec.explodeMs, BURST_MS) ?: return@forEach
                val dx = spec.dxTotal * tFrac
                val dy = -projectileY(spec.height, spec.apexFrac, tFrac)
                val rot = wobbleRotation(tFrac, spec.rot0, spec.rot1, spec.wAmp, spec.wFreq, spec.wPhase)
                val pScale = 1.2f - 0.5f * tFrac
                drawParticle(
                    MARGIN_SIDE + spec.originX + dx, MARGIN_TOP + spec.originY + dy,
                    spec.width, spec.heightPx, rot, pScale, spec.color, unitScale
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(320f / canvasUnitsW)
                .fillMaxHeight(320f / canvasUnitsH)
                .align(BiasAlignment(horizontalBias = 0f, verticalBias = LOGO_VERTICAL_BIAS))
        ) {
            Image(
                painter = painterResource(id = R.drawable.pot_logo_ring),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = ringAngle }
            )
            Image(
                painter = painterResource(id = R.drawable.pot_logo_triangle),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = triangleAngle
                        cameraDistance = 12f * density
                        transformOrigin = TransformOrigin(TRIANGLE_AXIS_X_PCT, 0.5f)
                    }
            )
            Image(
                painter = painterResource(id = R.drawable.pot_logo_letter),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = letterAngle
                        cameraDistance = 12f * density
                        transformOrigin = TransformOrigin(LETTER_AXIS_X_PCT, 0.5f)
                    }
            )
        }
    }
}

private fun DrawScope.drawParticle(
    canvasX: Float, canvasY: Float, wUnits: Float, hUnits: Float, rotDeg: Float, particleScale: Float, color: Color, unitScale: Float
) {
    val cx = canvasX * unitScale
    val cy = canvasY * unitScale
    val w = wUnits * unitScale * particleScale
    val h = hUnits * unitScale * particleScale
    rotate(rotDeg, pivot = Offset(cx, cy)) {
        drawRect(
            color = color,
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h)
        )
    }
}

/** [withFrameNanos] wrapper kept as its own function so the LaunchedEffect body reads linearly. */
private suspend fun withFrameNanosCompat(): Long =
    androidx.compose.runtime.withFrameNanos { it }

/**
 * SoundPool-backed player for the two families of pre-sliced crowd_long.wav
 * clips (see scratchpad/android_clips -- 9 natural phrases, each traced from
 * onset to where the envelope genuinely settles back to quiet, not a fixed
 * cut). `salute_clip_N` are capped to 2.2s with a real fade-out where the
 * source phrase runs longer (so a fast firing cadence doesn't stack whole
 * 7s barrages); `ambient_clip_N` keep each phrase's full natural length and
 * play continuously underneath at lower volume, one at a time with a random
 * gap, so what's heard is always a complete, undamaged piece of the
 * recording -- never truncated mid-phrase.
 */
private class SaluteSoundPlayer(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val saluteRawIds = intArrayOf(
        R.raw.salute_clip_0, R.raw.salute_clip_1, R.raw.salute_clip_2,
        R.raw.salute_clip_3, R.raw.salute_clip_4, R.raw.salute_clip_5,
        R.raw.salute_clip_6, R.raw.salute_clip_7, R.raw.salute_clip_8
    )
    private val ambientRawIds = intArrayOf(
        R.raw.ambient_clip_0, R.raw.ambient_clip_1, R.raw.ambient_clip_2,
        R.raw.ambient_clip_3, R.raw.ambient_clip_4, R.raw.ambient_clip_5,
        R.raw.ambient_clip_6, R.raw.ambient_clip_7, R.raw.ambient_clip_8
    )
    private var saluteSoundIds: IntArray = IntArray(0)
    private var ambientSoundIds: IntArray = IntArray(0)
    private var ambientJob: Job? = null

    // SoundPool.load() is async -- calling play() on an id before its own
    // load has finished decoding silently no-ops ("not READY" in logcat),
    // which is exactly what happened before this fix: GreetingView is
    // short-lived, so the very first salute/ambient triggers (which land
    // within a second of DisposableEffect's load() call) could go mute.
    // Track readiness per id via the completion listener instead of just
    // firing at whatever was requested.
    private val readyIds = java.util.concurrent.CopyOnWriteArraySet<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) readyIds.add(sampleId)
        }
    }

    fun load() {
        saluteSoundIds = IntArray(saluteRawIds.size) { pool.load(context, saluteRawIds[it], 1) }
        ambientSoundIds = IntArray(ambientRawIds.size) { pool.load(context, ambientRawIds[it], 1) }
    }

    fun playSalute() {
        val ready = saluteSoundIds.filter { it in readyIds }
        if (ready.isEmpty()) return
        pool.play(ready[Random.nextInt(ready.size)], 1f, 1f, 1, 0, 1f)
    }

    private fun playAmbient() {
        val ready = ambientSoundIds.filter { it in readyIds }
        if (ready.isEmpty()) return
        pool.play(ready[Random.nextInt(ready.size)], 0.4f, 0.4f, 1, 0, 1f)
    }

    fun startAmbient(scope: CoroutineScope) {
        ambientJob?.cancel()
        ambientJob = scope.launch {
            while (isActive) {
                playAmbient()
                delay((1400..3200).random().toLong())
            }
        }
    }

    fun release() {
        ambientJob?.cancel()
        pool.release()
    }
}
