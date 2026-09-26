package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Тонкая обёртка над Scaffold для контента без topBar. Раньше принимала
 * `title / drawerState / showTopBar / actionIconId` — всё было под флагом
 * showTopBar = true в топ-баре с гамбургер-меню. Оба удалены (v11.78 —
 * навигация уехала в BottomNavBar; v12.02 — ModalNavigationDrawer тоже
 * удалён). Осталось только «отступ под system insets + контент».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(content: @Composable () -> Unit) {
    Scaffold {
        Box(modifier = Modifier.padding(it)) {
            content()
        }
    }
}
