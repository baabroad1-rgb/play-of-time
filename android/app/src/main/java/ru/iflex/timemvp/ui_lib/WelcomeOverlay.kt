package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.core.utils.tidyLineBreaks
import ru.iflex.timemvp.ui_theme.baseBackgroundColor

// Стиль баблов (cheer-up / session summary): жёлтый фон + тёмно-коричневый текст.
private val WelcomeCardBg   = Color(0xFFF5C02E)
private val WelcomeTextColor = Color(0xFF2A1E00)

private val welcomeParagraphs = listOf(
    "Time is a tool for self-control. The objective is simple: stop the timer " +
    "at exactly three seconds. As you progress, the challenge becomes " +
    "increasingly precise — 3, 3.0, 3.00, or 3.000 seconds. It sounds easy, " +
    "yet consistently achieving it is surprisingly difficult.",

    "This is not a reaction game. Every attempt is an exercise in " +
    "self-regulation. To stop the timer at the right moment, your conscious " +
    "mind must compensate for your natural temperament — whether you tend to " +
    "rush ahead or hesitate too long. With practice, you learn to " +
    "deliberately bring yourself into the mental state that produces the " +
    "most accurate action.",

    "Each attempt lasts only a few seconds and provides immediate, objective " +
    "feedback. This rapid learning cycle allows you to experiment with " +
    "different approaches — focus, relaxation, breathing, visualization, or " +
    "any strategy of your own — and quickly discover what consistently works " +
    "best for you. Over time, your brain learns this optimal state and " +
    "begins to reproduce it automatically.",

    "As your ability to regulate yourself improves, the effects extend " +
    "beyond the game. You become better at entering an optimal mental state " +
    "before demanding tasks, maintaining composure under pressure, and " +
    "producing consistent results. Your history of attempts also becomes a " +
    "record of your functional state, reflecting changes in focus, " +
    "confidence, stress, and fatigue over time.",

    "Many players eventually notice that time itself seems to slow down. " +
    "The clock does not change, but your mind begins to fit more " +
    "perceptions, thoughts, and actions into the same interval. This " +
    "ability can be valuable in any activity that demands speed, precision, " +
    "and sound decision-making.",

    "Ultimately, Play of Time is not about stopping a timer at exactly " +
    "three seconds. It is about experiencing deliberate control over your " +
    "own performance, replacing uncertainty with confidence, and " +
    "discovering that consistent success can become the result of intention " +
    "rather than chance."
)

/**
 * Большое модальное окно первого запуска: жёлтая карточка в стиле баблов
 * (cheer-up) с описанием игры + тёмная кнопка старта. Перекрывает весь экран,
 * закрывается только кнопкой — пользователь должен осознанно начать.
 */
@Composable
fun WelcomeOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.baseBackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WelcomeCardBg)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(18.dp))
                BasicText(
                    text  = "Play of Time",
                    style = TextStyle(
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = WelcomeTextColor
                    )
                )
                welcomeParagraphs.forEach { paragraph ->
                    BasicText(
                        text  = tidyLineBreaks(paragraph),
                        style = TextStyle(
                            fontSize   = 14.sp,
                            lineHeight = 20.sp,
                            color      = WelcomeTextColor
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // Кнопка старта — тёмная pill на жёлтой карточке, всегда видна снизу
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(WelcomeTextColor)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text  = "Start playing",
                    style = TextStyle(
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = WelcomeCardBg,
                        textAlign  = TextAlign.Center
                    )
                )
            }
        }
    }
}
