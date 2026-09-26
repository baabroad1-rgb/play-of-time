package online.playoftime.ui_lib

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Полупрозрачный янтарь для заливки бабла + плотный янтарь для обводки.
private val CheerUpBackground = Color(0xA6F5C02E)   // rgba(245, 192, 46, 0.65)
private val CheerUpBorder     = Color(0xFFFFD35E)   // яркая насыщенная кайма
private val CheerUpTextColor  = Color(0xFF2A1E00)   // dark brown text -- task line
private val CheerUpOpenerColor = Color(0xFF6B5210)  // muted brown -- opener/recap line

/**
 * Bubble для отображения post-event чирапа поверх текста квеста.
 *
 * @param specificText — текст чирапа
 * @param onDismiss    — вызывается при тапе на «×» (крестик в правом верхнем углу)
 */
@Composable
fun CheerUpBubble(
    specificText: String,
    generalText: String,             // kept for source compatibility — always empty now
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(CheerUpBackground)
        .border(2.dp, CheerUpBorder, RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Box(modifier = bubbleModifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 28.dp),   // room for × in the top-right
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                BasicText(
                    text  = "👋",
                    style = TextStyle(fontSize = 22.sp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                // weight(1f) — иначе BasicText в Row получает unbounded width и
                // текст не wrap'ается, обрезается за правым краем бабла.
                // tidyLineBreaks — служебные слова (a/of/in/to/…) приклеиваются
                // к следующему слову, «—» и «·» к предыдущему: без висячих
                // предлогов в конце строк. Применяется здесь (в точке рендера),
                // чтобы покрыть и асинхронно дописанные суффиксы.
                // Один размер шрифта везде (Alex 2026-08-17: «не нравится что
                // разным размером шрифта» — старая версия делала строку задачи
                // крупнее opener'а, 19sp vs 16sp). Разделение теперь только по
                // весу/цвету: opener — обычный вес, приглушённый коричневый;
                // строка задачи (от "Quest #.../SubQuest#...") — полужирная,
                // тёмная, с "→ " перед тегом как якорем для глаза.
                val tidied = online.playoftime.core.utils.tidyLineBreaks(specificText)
                val tagIdx = Regex("Quest #|SubQuest#").find(tidied)?.range?.first
                val annotated = buildAnnotatedString {
                    if (tagIdx != null) {
                        withStyle(SpanStyle(color = CheerUpOpenerColor, fontWeight = FontWeight.Normal)) {
                            append(tidied.substring(0, tagIdx))
                        }
                        withStyle(SpanStyle(color = CheerUpTextColor, fontWeight = FontWeight.Bold)) {
                            append("→ ")
                            append(tidied.substring(tagIdx))
                        }
                    } else {
                        withStyle(SpanStyle(color = CheerUpOpenerColor, fontWeight = FontWeight.Normal)) {
                            append(tidied)
                        }
                    }
                }
                BasicText(
                    modifier = Modifier.weight(1f),
                    text  = annotated,
                    style = TextStyle(
                        fontSize   = 15.sp,
                        lineHeight = 21.sp,
                        color      = CheerUpTextColor
                    )
                )
            }
        }

        // Close × прижат к углу через offset (компенсирует внутренний padding бабла)
        // и обведён тонким кружком в том же тёмно-коричневом цвете, что и текст.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp)
                .size(28.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.dp, CheerUpTextColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text  = "×",
                    style = TextStyle(
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = CheerUpTextColor,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}

/** Extension helper: applies blur on API 31+, no-op below. */
fun Modifier.blurBackdrop(radiusPx: Float = 12f): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = RenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.DECAL)
                .asComposeRenderEffect()
        }
    } else this
