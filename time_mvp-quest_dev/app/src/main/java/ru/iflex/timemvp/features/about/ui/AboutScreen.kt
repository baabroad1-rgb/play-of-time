package ru.iflex.timemvp.features.about.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import ru.iflex.timemvp.core.services.VibrationService
import ru.iflex.timemvp.core.utils.tidyLineBreaks
import ru.iflex.timemvp.ui_lib.SPIN_DURATION_MS
import ru.iflex.timemvp.ui_theme.Dimens

// Стиль баблов (cheer-up / session summary): жёлтый фон + тёмно-коричневый текст.
private val AboutCardBg    = Color(0xFFF5C02E)
private val AboutTextColor = Color(0xFF2A1E00)

@Composable
fun AboutScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit
) {
        // Fullscreen "meditation" view (Alex 2026-09-10): tap the animation —
        // full black screen, animation alone, no chrome. Early-return, single
        // root node (same pattern as WelcomeOverlay/GreetingView/FailCard on
        // the main screen) — a second top-level sibling silently failed to
        // render as an overlay once before, see SettingScreen's 13-14 August
        // "Erase all data" bug.
        var showMeditation by remember { mutableStateOf(false) }
        if (showMeditation) {
            MeditationFullscreen(onDismiss = { showMeditation = false })
            return
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = Dimens.horizontalPadding,
                        vertical   = Dimens.verticalPadding
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AboutCardBg)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    AboutTitle("About Play of Time")
                    AboutBody(
                        "Play of Time is a training game for precise time perception " +
                        "and self-control. The objective is simple: stop the timer at " +
                        "exactly three seconds. As you progress, the challenge becomes " +
                        "increasingly precise — 3, 3.0, 3.00, or 3.000. It sounds easy, " +
                        "yet consistently achieving it is surprisingly difficult. The " +
                        "method is inspired by the Soviet psychologist S. Gellerstein, " +
                        "who studied the perception of short time intervals and their " +
                        "role in human performance."
                    )

                    AboutTitle("Self-regulation, not reaction")
                    AboutBody(
                        "This is not a reaction game. Every attempt is an exercise in " +
                        "self-regulation: to stop the timer at the right moment, your " +
                        "conscious mind must compensate for your natural temperament — " +
                        "whether you rush ahead or hesitate too long. Each attempt gives " +
                        "immediate, objective feedback, so you can experiment with focus, " +
                        "relaxation, breathing, or visualization and quickly discover " +
                        "what consistently works best for you."
                    )

                    AboutTitle("Serenity")
                    AboutBody(
                        "High accuracy requires more than quick reactions — it requires " +
                        "balance. The calmer and more stable your internal state, the " +
                        "more consistent your results. Serenity Coins reward steady, " +
                        "well-controlled performance rather than isolated lucky hits."
                    )

                    AboutTitle("Luck Coefficient")
                    AboutBody(
                        "The app compares your actual performance with the theoretical " +
                        "probability of success. Despite its name, this coefficient does " +
                        "not measure chance — it reflects attention, anticipation, and " +
                        "movement precision. As these improve, your \"luck\" becomes " +
                        "increasingly under your control."
                    )

                    AboutTitle("Beyond the game")
                    AboutBody(
                        "As your ability to regulate yourself grows, the effects extend " +
                        "beyond the game: you get better at entering an optimal state " +
                        "before demanding tasks and staying composed under pressure. " +
                        "Many players even notice that time itself seems to slow down — " +
                        "the mind fits more perceptions, thoughts, and actions into the " +
                        "same interval."
                    )

                    AboutTitle("Learn more")
                    // Single line (Alex 2026-09-15: "напиши в одну строчку") — was
                    // the link alone, sharing a Row with the logo column, which on
                    // a narrow phone with the logo's own 140dp reserved could have
                    // wrapped this sentence onto two lines. Full-width row of its
                    // own now, so it's never squeezed by the logo next to it.
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val uriHandler = LocalUriHandler.current
                        BasicText(
                            text  = "Learn more at ",
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = AboutTextColor)
                        )
                        BasicText(
                            text  = "playoftime.online",
                            style = TextStyle(
                                fontSize       = 14.sp,
                                lineHeight     = 20.sp,
                                fontWeight     = FontWeight.Bold,
                                color          = AboutTextColor,
                                textDecoration = TextDecoration.Underline
                            ),
                            modifier = Modifier.clickable { uriHandler.openUri("https://playoftime.online") }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // "Tap for meditation" replaced (Alex 2026-09-15) with the
                    // rhythm explanation, now sitting to the LEFT of the logo
                    // instead of a caption above it.
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "Need to tune into the target time? Tap the logo — " +
                                "its full turn takes exactly three seconds. Watch it " +
                                "to feel the rhythm you need.",
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            style = TextStyle(
                                fontSize   = 12.5.sp,
                                lineHeight = 17.sp,
                                color      = AboutTextColor.copy(alpha = 0.8f)
                            )
                        )
                        ru.iflex.timemvp.ui_lib.AnimatedLogo(
                            modifier = Modifier
                                .size(140.dp)
                                .clickable { showMeditation = true }
                        )
                    }

                    AboutTitle("Credits")
                    AboutBody(
                        "Concept & Development: Alexander Belyshkin\n" +
                        "Design: Semion Kulikov\n" +
                        "Financial support: Alexey Sibiryakov"
                    )

                    // Версия сборки + дата релиза (Alex 2026-07-26).
                    Spacer(modifier = Modifier.height(20.dp))
                    BasicText(
                        text  = "Version ${ru.iflex.timemvp.BuildConfig.VERSION_NAME} · " +
                                ru.iflex.timemvp.BuildConfig.BUILD_DATE,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color    = AboutTextColor.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Sticky нижняя навигация вынесена в MainActivity (Alex 2026-08-08).
        }
}

/**
 * Full black screen, animation alone, tap anywhere to leave (Alex 2026-09-10).
 * Scoped to this page's own content area, same as every other full-screen
 * overlay in this app (GreetingView/FailCard etc. on MainScreen) — the
 * bottom nav bar is a sibling of the pager in MainActivity, not something
 * a single page can cover.
 */
@Composable
private fun MeditationFullscreen(onDismiss: () -> Unit) {
    // Vibration on the triangle/letter's own rotation, fullscreen ONLY (Alex
    // 2026-09-15: "и в полноэкранном режиме и только в нем... вибросигнал
    // при старте цикла и его окончании"). One tick at composition (= the
    // flip's own start, since SpinningLayer's main layer starts at delayMs=0
    // with no start-of-app offset), then every SPIN_DURATION_MS after — end
    // of one 3s cycle and start of the next are the same instant (Restart
    // repeat mode), so a single repeating tick every 3000ms covers both
    // without a separate "end" pulse.
    val vibrationService = koinInject<VibrationService>()
    LaunchedEffect(Unit) {
        while (true) {
            vibrationService.tick()
            delay(SPIN_DURATION_MS.toLong())
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        ru.iflex.timemvp.ui_lib.AnimatedLogo(modifier = Modifier.size(260.dp))
    }
}

@Composable
private fun AboutTitle(title: String) {
    Spacer(modifier = Modifier.height(14.dp))
    BasicText(
        text  = title,
        style = TextStyle(
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold,
            color      = AboutTextColor
        )
    )
}

@Composable
private fun AboutBody(body: String) {
    Spacer(modifier = Modifier.height(6.dp))
    BasicText(
        text  = tidyLineBreaks(body),
        style = TextStyle(
            fontSize   = 14.sp,
            lineHeight = 20.sp,
            color      = AboutTextColor
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
