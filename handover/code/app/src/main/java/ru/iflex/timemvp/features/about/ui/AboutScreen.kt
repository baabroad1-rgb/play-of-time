package ru.iflex.timemvp.features.about.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.core.utils.tidyLineBreaks
import ru.iflex.timemvp.ui_theme.Dimens

// Стиль баблов (cheer-up / session summary): жёлтый фон + тёмно-коричневый текст.
private val AboutCardBg    = Color(0xFFF5C02E)
private val AboutTextColor = Color(0xFF2A1E00)

@Composable
fun AboutScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit
) {
    ru.iflex.timemvp.ui_lib.Screen {
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
                    val uriHandler = LocalUriHandler.current
                    BasicText(
                        text  = "playoftime.online",
                        style = TextStyle(
                            fontSize       = 14.sp,
                            lineHeight     = 20.sp,
                            fontWeight     = FontWeight.Bold,
                            color          = AboutTextColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { uriHandler.openUri("https://playoftime.online") }
                    )

                    AboutTitle("Credits")
                    AboutBody(
                        "Concept & Development: Alexander Belyshkin\n" +
                        "Design: Semyon Kulikov"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Sticky нижняя навигация.
            val exportAction = ru.iflex.timemvp.ui_lib.rememberExcelShareAction()
            ru.iflex.timemvp.ui_lib.BottomNavBar(
                currentPage  = currentPage,
                onSelectPage = onSelectPage,
                onExport     = exportAction
            )
        }
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
