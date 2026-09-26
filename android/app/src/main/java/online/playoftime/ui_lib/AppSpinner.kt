package online.playoftime.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.activityIndicatorColor
import online.playoftime.ui_theme.greyBackgroundColor

@Composable
fun AppSpinner() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.greyBackgroundColor)
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(Dimens.appSpinner),
            color = MaterialTheme.colorScheme.activityIndicatorColor
        )
    }
}