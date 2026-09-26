package ru.iflex.timemvp.features.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.disabledAccentColor
import ru.iflex.timemvp.ui_theme.smallTextStyle

class AccuracyItem {
}

@Composable
fun AccuracyItem(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
) {

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = {
                onSelect()
            },
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.disabledAccentColor,
                unselectedColor = MaterialTheme.colorScheme.disabledAccentColor,
                //selectedColor = MaterialTheme.colorScheme.accentColor,
                //unselectedColor = MaterialTheme.colorScheme.accentColor
            )
        )

        BasicText(
            modifier = modifier.clickable(
                onClick = { onSelect() },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
            text = text,
            style = smallTextStyle

        )
    }
}