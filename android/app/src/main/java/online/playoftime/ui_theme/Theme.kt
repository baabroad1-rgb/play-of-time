package online.playoftime.ui_theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColorScheme()

private val LightColorPalette = lightColorScheme()

@Composable
fun TimeMVPTheme(
    content: @Composable () -> Unit
) {
    val colors = if (themeMode == ThemeMode.DARK) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colorScheme = colors,
        //typography = Typography,
        //shapes = Shapes,
        content = content
    )
}

enum class ThemeMode {
    LIGHT,
    DARK
}

var userThemeMode: ThemeMode = ThemeMode.DARK
var themeAsSystem = false

val themeMode: ThemeMode
    @Composable
    get() {
        return if (themeAsSystem) {
            if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            userThemeMode
        }
    }

val ColorScheme.accentColor: Color
    @Composable
    get() = orangeColor

val ColorScheme.disabledAccentColor: Color
    @Composable
    get() = orangeShadeColor

/**
 * Жёлтый режима подсказок (Alex 2026-09-27: «сделать жёлтый крестик на
 * закрытии справки — такого же цвета, как подсказки, не оранжевый»).
 *
 * Тот же цвет, которым залит бабл подсказки (HelpBubbleBg в HelpTapOverlay):
 * крестик выхода из режима — часть справки, а не обычный акцент приложения,
 * и оранжевым он читался как посторонняя кнопка. Один токен на оба места,
 * чтобы цвета не разъехались при будущей правке.
 */
val ColorScheme.helpAccentColor: Color
    @Composable
    get() = helpYellowColor

val ColorScheme.baseBackgroundColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) whiteBaseColor else blackColor

val ColorScheme.baseTextColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) darkGreyColor else whiteBaseColor

val ColorScheme.disabledTextColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) darkGreyColor else extraGreyVariantColor

val ColorScheme.disabledTextFieldColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) darkGreyColor else greyVariantColor

val ColorScheme.buttonTextColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) whiteColor else whiteColor

val ColorScheme.mainButtonTColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) accentColor else darkGreyColor

val ColorScheme.blackTextColor: Color
    @Composable
    get() = darkGreyColor

val ColorScheme.greyBackgroundColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) lightGreyColor else greyVariantColor

val ColorScheme.winColor: Color
    @Composable
    get() = greenColor

val ColorScheme.fastColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) redColor else redVariantColor

val ColorScheme.slowColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) blueColor else blueVariantColor

val ColorScheme.statisticColor: Color
    @Composable
    get() = sandColor

val ColorScheme.statisticVariantColor: Color
    @Composable
    get() = roseColor

val ColorScheme.textFieldBackgroundColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) lightGreyColor else darkGreyColor



val ColorScheme.translucentColor: Color
    @Composable
    get() = transparentColor

val ColorScheme.whiteColor: Color
    @Composable
    get() = whiteBaseColor


val ColorScheme.switchIsOffColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) extraGreyVariantColor else darkGreyColor

val ColorScheme.switchIsOffDisabledColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) extraLightGreyColor else extraGreyVariantColor

// Цвета результата попытки
val ColorScheme.earlyErrorColor: Color
    @Composable get() = resultRedColor

val ColorScheme.lateErrorColor: Color
    @Composable get() = resultBlueColor

val ColorScheme.trueThreeColor: Color
    @Composable get() = resultGreenColor
val ColorScheme.popupBackgroundColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) lightGreyColor else darkGreyColor

val ColorScheme.activityIndicatorColor: Color
    @Composable
    get() = if (themeMode == ThemeMode.LIGHT) blackColor else whiteBaseColor