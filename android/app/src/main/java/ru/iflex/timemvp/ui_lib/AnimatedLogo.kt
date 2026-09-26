package ru.iflex.timemvp.ui_lib

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import ru.iflex.timemvp.R

/**
 * Animated brand mark, ported 1:1 (same timing/values) from the CSS
 * prototype approved by Alex on 2026-08-15: ring rests, then periodically
 * spins clockwise once with an overshoot-and-settle "inertia" ease and
 * lands back at the same orientation; triangle and letter continuously
 * flip around a strictly vertical axis through the letter's own centroid,
 * counter-rotating (opposite directions, same speed); each gets a faint
 * time-lagged trail (denser on the letter) so the two directions read as
 * visibly different rather than looking like the same rotation mirrored
 * (a flat rotateY/rotationY flip is direction-ambiguous on its own — the
 * "spinning dancer" illusion — a plain silhouette can't disambiguate it).
 */

private const val LETTER_AXIS_X_PCT = 0.485f   // letter "t" centroid, measured from source art
private const val TRIANGLE_AXIS_X_PCT = 0.5f   // triangle's own true center (2026-08-16 fix -- was
                                                // wrongly sharing LETTER_AXIS_X_PCT, which made the
                                                // mirrored left touch reach ~15.6px farther than the
                                                // natural right touch; see memory: firework-prototype-
                                                // locked-params for the full derivation)
// internal (not private) -- AboutScreen.kt's meditation fullscreen (Alex
// 2026-09-15) ticks a vibration on this exact same period, so it reads this
// constant directly instead of duplicating the number and risking drift.
internal const val SPIN_DURATION_MS = 3000     // triangle/letter revolution — one full flip,
                                                // "beak touches one side to the other" (Alex 2026-09-10)
private val TRAIL_TRIANGLE = emptyList<Pair<Int, Float>>()  // triangle keeps no trail (Alex 2026-08-15)
private val TRAIL_LETTER   = listOf(50 to 0.55f, 100 to 0.42f, 160 to 0.28f, 240 to 0.15f)

@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density

    // Кольцо несёт надпись "PLAY OF TIME" — её собственное движение (Alex
    // 2026-09-10: "для надписи тоже три секунды от начала движения до
    // состояния покоя") теперь длится ровно 3000мс целиком (было 700+400+300
    // = 1400мс), с сохранением исходной пропорции overshoot/settle-фаз
    // (7:4:3 → 1500:850:650). Пауза покоя ПЕРЕД началом движения (3200мс) не
    // трогалась — просьба касалась только самого движения.
    val ringAngle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            ringAngle.animateTo(372f, tween(1500, easing = FastOutSlowInEasing))
            ringAngle.animateTo(355f, tween(850))
            ringAngle.animateTo(360f, tween(650))
            ringAngle.snapTo(0f)
        }
    }

    Box(modifier.aspectRatio(1f)) {
        Image(
            painter = painterResource(id = R.drawable.pot_logo_ring),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = ringAngle.value }
        )

        TRAIL_TRIANGLE.forEach { (delayMs, alpha) ->
            SpinningLayer(
                drawableId = R.drawable.pot_logo_triangle,
                direction = 1f,
                delayMs = delayMs,
                alpha = alpha,
                density = density,
                axisXPct = TRIANGLE_AXIS_X_PCT
            )
        }
        SpinningLayer(
            drawableId = R.drawable.pot_logo_triangle,
            direction = 1f,
            delayMs = 0,
            alpha = 1f,
            density = density,
            axisXPct = TRIANGLE_AXIS_X_PCT
        )

        TRAIL_LETTER.forEach { (delayMs, alpha) ->
            SpinningLayer(
                drawableId = R.drawable.pot_logo_letter,
                direction = -1f,
                delayMs = delayMs,
                alpha = alpha,
                density = density
            )
        }
        SpinningLayer(
            drawableId = R.drawable.pot_logo_letter,
            direction = -1f,
            delayMs = 0,
            alpha = 1f,
            density = density
        )
    }
}

/** One rotating copy (main shape or a single trail ghost) of a triangle/letter layer. */
@Composable
private fun SpinningLayer(
    drawableId: Int,
    direction: Float,
    delayMs: Int,
    alpha: Float,
    density: Float,
    axisXPct: Float = LETTER_AXIS_X_PCT
) {
    val transition = rememberInfiniteTransition(label = "pot-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = direction * 360f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(SPIN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs, StartOffsetType.Delay)
        ),
        label = "pot-spin-angle"
    )
    Image(
        painter = painterResource(id = drawableId),
        contentDescription = null,
        alpha = alpha,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin(axisXPct, 0.5f)
            }
    )
}
