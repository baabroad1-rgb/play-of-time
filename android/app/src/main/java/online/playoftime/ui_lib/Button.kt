package online.playoftime.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.baseTextStyle
import online.playoftime.ui_theme.disabledAccentColor
import online.playoftime.ui_theme.smallShape

@Composable
fun Button(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.accentColor,
    disabledColor: Color = MaterialTheme.colorScheme.disabledAccentColor,
    textColor: Color = MaterialTheme.colorScheme.baseTextColor,
    text: String? = null,
    icon: Painter? = null,
    onClick: () -> Unit = {},
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .clip(
                shape = smallShape
            )
            .background(
                color =  if (enabled) color
                else disabledColor
            )
            .defaultMinSize(minHeight = Dimens.buttonHeight, minWidth = Dimens.buttonWidth),

        horizontalArrangement = Arrangement.spacedBy(
            space = 6.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        if (text != null) {
            BasicText(
                text = text,
                style = baseTextStyle.copy(color = textColor),
            )
        }

        if (icon != null) {
            Icon(
                painter = icon,
                color = textColor
            )
        }
    }
}