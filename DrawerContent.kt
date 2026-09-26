package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import ru.iflex.timemvp.NavRoutes

import ru.iflex.timemvp.R
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.baseTextStyle
import ru.iflex.timemvp.ui_theme.disabledTextColor
import ru.iflex.timemvp.ui_theme.disabledTextStyle


data class DrawerMenu(val icon: ImageVector, val title: String, val route: NavRoutes, val enabled: Boolean)

val menus
    @Composable
    get() = arrayOf(
        DrawerMenu(ImageVector.vectorResource(id = R.drawable.ic_clock), stringResource(id =R.string.main_screen_title), NavRoutes.MainScreen, true),
        DrawerMenu(ImageVector.vectorResource(id = R.drawable.ic_stat), "Analytics", NavRoutes.StatScreen, true),
        DrawerMenu(ImageVector.vectorResource(id = R.drawable.ic_settings), stringResource(id =R.string.settings_screen_title), NavRoutes.SettingScreen, true),
        DrawerMenu(ImageVector.vectorResource(id = R.drawable.ic_win), stringResource(id = R.string.about_screen_title), NavRoutes.AboutScreen, true)
    )
@Composable
fun DrawerContent(
    menus: Array<DrawerMenu>,
    onMenuClick: (NavRoutes) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = Dimens.largeVerticalPadding)
                .fillMaxWidth()
                .height(Dimens.logoSize)
                .paint(
                    painterResource(id = R.drawable.ic_clock),
                    contentScale = ContentScale.Fit
                ),
            contentAlignment = Alignment.Center
        ) {

        }
        Spacer(modifier = Modifier.height(Dimens.verticalPadding))
        menus.forEach {
            NavigationDrawerItem(
                label = {
                    Text(text = it.title, style = if (it.enabled) baseTextStyle else disabledTextStyle)
                        },
                icon = { Icon(modifier = Modifier.size(24.dp),
                    imageVector = it.icon,
                    contentDescription = null,
                    tint = if (it.enabled) MaterialTheme.colorScheme.baseTextColor else MaterialTheme.colorScheme.disabledTextColor
                ) },
                selected = false,
                onClick = {
                    if (it.enabled) {
                        onMenuClick(it.route)
                    }
                }
            )
        }
    }
}
