package ru.iflex.timemvp.ui_lib

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.ui_theme.baseTextStyle
import ru.iflex.timemvp.ui_theme.greyBackgroundColor
import ru.iflex.timemvp.ui_theme.popupBackgroundColor
import ru.iflex.timemvp.ui_theme.smallShape

@Composable
fun InfoDialog(
    message: String = "",
    onCloseClick: () -> Unit = {},
    @StringRes buttonText: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .background(
                color = MaterialTheme.colorScheme.greyBackgroundColor
            )
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(all = Dimens.standartPadding*2)
                .shadow(
                    elevation = 10.dp,
                    shape = smallShape
                )
                .background(
                    color = MaterialTheme.colorScheme.popupBackgroundColor
                )
                .padding(all = Dimens.standartPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = message,
                style = baseTextStyle.copy(textAlign = TextAlign.Center)
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth(),
                text = if (buttonText != 0) stringResource(id = buttonText) else stringResource(ru.iflex.timemvp.R.string.button_ok),
                onClick = { onCloseClick() }
            )
        }
    }
}