package ru.iflex.timemvp.ui_theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


val appTitleStyle
    @Composable
    get() = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.baseTextColor
    )

val baseTextStyle
    @Composable
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.baseTextColor
    )

val disabledTextFieldStyle
    @Composable
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.disabledTextFieldColor
    )

val disabledTextStyle
    @Composable
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.disabledTextColor
    )

val smallTextStyle
    @Composable
    get() = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.baseTextColor
    )

val statisticTextStyle
    @Composable
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.baseTextColor
    )

val stopwatchStyle
    @Composable
    get() = TextStyle(
        fontSize = 70.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.accentColor
    )

val stopwatchLandscapeStyle
    @Composable
    get() = TextStyle(
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.accentColor
    )

val stopwatchSmallStyle
    @Composable
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.accentColor
    )

val mainButtonStyle
    @Composable
    get() = TextStyle(
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.buttonTextColor
    )


