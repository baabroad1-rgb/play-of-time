package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.iflex.timemvp.R
import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.baseBackgroundColor
import ru.iflex.timemvp.ui_theme.baseTextColor

/**
 * Sticky нижняя навигационная панель — 4 вкладки: Play / Analytics / Settings /
 * About (+ опционально Export). Активная подсвечена accentColor, остальные
 * приглушены. Тап отправляет `onSelectPage(index)` — MainActivity анимирует
 * HorizontalPager к выбранной странице.
 *
 * Индексы совпадают с порядком страниц в pager: 0=Play, 1=Analytics,
 * 2=Settings, 3=About.
 */
@Composable
fun BottomNavBar(
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onExport: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.baseBackgroundColor)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab(R.drawable.ic_run,      "Play",      currentPage == 0) { if (currentPage != 0) onSelectPage(0) }
        NavTab(R.drawable.ic_stat,     "Analytics", currentPage == 1) { if (currentPage != 1) onSelectPage(1) }
        NavTab(R.drawable.ic_settings, "Settings",  currentPage == 2) { if (currentPage != 2) onSelectPage(2) }
        NavTab(R.drawable.ic_about,    "About",     currentPage == 3) { if (currentPage != 3) onSelectPage(3) }
        // Export — действие, не навигация: selected=false всегда.
        if (onExport != null) {
            NavTab(R.drawable.ic_export, "Export", selected = false, onClick = onExport)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent   = MaterialTheme.colorScheme.accentColor
    val baseText = MaterialTheme.colorScheme.baseTextColor
    val iconTint = if (selected) accent else baseText.copy(alpha = 0.55f)
    val textColor = if (selected) accent else baseText.copy(alpha = 0.55f)
    val bg = if (selected) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(bg, shape = RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            modifier = Modifier.size(16.dp),
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = ColorFilter.tint(iconTint)
        )
        Spacer(Modifier.height(2.dp))
        BasicText(
            text  = label,
            style = TextStyle(
                fontSize   = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = textColor,
                textAlign  = TextAlign.Center
            )
        )
    }
}
