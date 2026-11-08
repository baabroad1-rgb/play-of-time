package online.playoftime.ui_theme

import androidx.compose.ui.graphics.Color

val blueColor = Color(0xFF1C84FF)
val blueVariantColor = Color(0xFF39A0FF)
val redColor = Color(0xFFFF3F56)
val redVariantColor = Color(0xFFED3611)
val darkGreyColor = Color(0xFF3D3D3D)
val greyVariantColor = Color(0xE60A0A0A)
val sandColor = Color(0xFFE6A440)
val roseColor = Color(0xFFF6B49E)
val greenColor = Color(0xFF59DF4D)
val lightGreyColor = Color(0xFFEEEEEE)
val whiteBaseColor = Color(0xFFFFFFFF)
val blackColor = Color(0xFF000000)
val orangeColor = Color(0xFFFF551C)
// Жёлтый режима подсказок. Тот же, которым залит бабл подсказки
// (HelpBubbleBg в HelpTapOverlay) — источник один, чтобы не разъехались.
val helpYellowColor = Color(0xFFF5C02E)
val orangeShadeColor = Color(0xFFFFB79F)
val transparentColor = Color(0x00000000)
val extraLightGreyColor = Color(0xFFEBEBEB)
val extraGreyVariantColor = Color(0xFFA9A9A9)

// Цвета результата попытки (новый дизайн)
val resultRedColor = Color(0xFFDE4264)    // Early — раньше цели
val resultBlueColor = Color(0xFF0CA8CC)   // Late  — позже цели
val resultGreenColor = Color(0xFF8EFF51)  // True Three — точное попадание
// Победа по смягчению (Alex 2026-11-03): попытка засчитана в пределах допуска
// индульгенции, но не ровно 3.0 / 3.00 / 3.000 на точности уровня — блёклый
// зелёный (resultGreenColor наполовину смешан с серым). Яркий зелёный остаётся
// только у чистых троек.
val resultGreenSoftColor = Color(0xFF87BF68)

/** Цвет победы: яркий для чистой тройки, блёклый — для победы по смягчению. */
fun winGreen(clean: Boolean): Color = if (clean) resultGreenColor else resultGreenSoftColor

// В аналитике победа по смягчению ещё и со своей стороны от тройки (Alex
// 2026-11-08: «зелёный слева чуть покраснее, а справа чуть посинее»): тот же
// блёклый, повёрнутый по тону на ±11° (OKLCH) к красному раннему / синему позднему.
val resultGreenSoftEarlyColor = Color(0xFF9BBA58)
val resultGreenSoftLateColor  = Color(0xFF71C379)

/** Блёклый зелёный победы по смягчению с оттенком своей стороны: раньше 3 — к красному, позже — к синему. */
fun easedGreen(errorMs: Double): Color = when {
    errorMs < 0 -> resultGreenSoftEarlyColor
    errorMs > 0 -> resultGreenSoftLateColor
    else        -> resultGreenSoftColor
}
val resultYellowColor = Color(0xFFE6A440) // Pending — 1.5 сек после остановки