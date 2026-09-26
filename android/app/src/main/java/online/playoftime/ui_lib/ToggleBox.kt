package online.playoftime.ui_lib

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import online.playoftime.R
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseTextStyle
import online.playoftime.ui_theme.disabledAccentColor
import online.playoftime.ui_theme.disabledTextColor
import online.playoftime.ui_theme.disabledTextStyle
import online.playoftime.ui_theme.switchIsOffColor
import online.playoftime.ui_theme.switchIsOffDisabledColor
import online.playoftime.ui_theme.whiteColor

@Composable
fun ToggleBox(
    modifier: Modifier = Modifier,
    @StringRes text: Int,
    onToggleClick: () -> Unit = {},
    isOn: Boolean = false,
    enabled: Boolean = true,
    definiteMode: Boolean = true
) {
    var _isOn by remember(isOn) { mutableStateOf(isOn) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!enabled) {
            Icon(
                modifier = Modifier
                    .padding(end = Dimens.horizontalPadding),
                painter = painterResource(id = R.drawable.ic_lock),
                color = MaterialTheme.colorScheme.disabledTextColor
            )
        }
        BasicText(
            modifier = Modifier.weight(1F),
            text = stringResource(id = text),
            style = if (enabled) baseTextStyle else disabledTextStyle
        )

        Box(
            modifier = Modifier
                .size(Dimens.toggleWidth + Dimens.horizontalPadding, Dimens.toggleHeight)
                .padding(start = Dimens.horizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier
                    .clickable {
                        if (enabled) {
                            if (definiteMode) _isOn = !_isOn
                            onToggleClick()
                        }
                    },
                painter = painterResource(
                    if (_isOn) {
                        R.drawable.switch_is_on
                    } else {
                        R.drawable.switch_is_off
                    }
                ),
                color = if (enabled) {
                    if (_isOn) {
                        MaterialTheme.colorScheme.accentColor
                    } else {
                        MaterialTheme.colorScheme.switchIsOffColor
                    }
                } else {
                    if (_isOn) {
                        MaterialTheme.colorScheme.disabledAccentColor
                    } else {
                        MaterialTheme.colorScheme.switchIsOffDisabledColor
                    }
                }
            )
            Row(
                modifier = Modifier
                    .size(Dimens.toggleWidth + Dimens.horizontalPadding, Dimens.toggleHeight)
                    .padding(
                        start = Dimens.horizontalPadding / 4,
                        end = Dimens.horizontalPadding / 4
                    ),
                horizontalArrangement = if (_isOn) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.switch_white_circle),
                    color = MaterialTheme.colorScheme.whiteColor
                )
            }
        }
    }
}