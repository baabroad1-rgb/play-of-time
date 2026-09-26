package ru.iflex.timemvp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import ru.iflex.timemvp.core.utils.landscapeMode
import ru.iflex.timemvp.core.utils.orientationMode
import ru.iflex.timemvp.core.utils.portraitMode
import ru.iflex.timemvp.features.about.ui.AboutScreen
import ru.iflex.timemvp.features.main.ui.MainScreen
import ru.iflex.timemvp.features.settings.ui.SettingScreen
import ru.iflex.timemvp.features.stats.ui.StatScreen
import ru.iflex.timemvp.ui_theme.TimeMVPTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Полноэкранный immersive-режим (Alex 2026-07-13): статус-бар и
        // системные навигационные кнопки скрыты, контент растянут под них.
        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE — свайп от края временно
        // показывает бары, потом они снова прячутся. Applied в
        // onWindowFocusChanged тоже — на случай возврата из шторки/IME.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersive()
        setContent {
            TimeMVPTheme {
                // ── Свайп между экранами через стандартный HorizontalPager ────
                // (Alex 2026-07-12: «шторка, палец тянет, отпустил — доедет»).
                // Раньше было: детектор жестов → детект порога → мгновенный
                // navigate() со слайд-анимацией. Палец не тянул содержимое —
                // это было не «шторка», а «щелчок».
                //
                // HorizontalPager делает всё сам: контент следует за пальцем,
                // при отпускании — fling с настраиваемым snap. Все 4 экрана
                // держатся как страницы; переход между ними больше не создаёт
                // back-stack entry.
                //
                // ModalNavigationDrawer удалён (v12.02): его edge-свайп был
                // «хвостом» гамбургер-меню. Топ-бар везде скрыт (Screen.kt
                // не принимает showTopBar), drawerState тоже больше не нужен.
                val pagerState = rememberPagerState(pageCount = { 4 })
                val scope      = rememberCoroutineScope()
                val goTo: (Int) -> Unit = { idx ->
                    scope.launch { pagerState.animateScrollToPage(idx) }
                }

                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> MainScreen(
                            currentPage  = pagerState.currentPage,
                            onSelectPage = goTo
                        )
                        1 -> StatScreen(
                            currentPage  = pagerState.currentPage,
                            onSelectPage = goTo,
                            onEarnMore   = { goTo(0) }
                        )
                        2 -> SettingScreen(
                            currentPage  = pagerState.currentPage,
                            onSelectPage = goTo
                        )
                        3 -> AboutScreen(
                            currentPage  = pagerState.currentPage,
                            onSelectPage = goTo
                        )
                    }
                }
            }
        }
    }

    private var myReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {

            val mode = intent.getStringExtra(orientationMode)

            if (mode == landscapeMode){
                this@MainActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }else if(mode == portraitMode){
                this@MainActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(myReceiver, IntentFilter(orientationMode), RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(myReceiver)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    private fun applyImmersive() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Скрываем только нижние навигационные кнопки. Статус-бар (часы,
        // батарея, сеть) остаётся видимым — Alex 2026-07-15: «оставить
        // системную верхнюю строчку в полноэкранном режиме».
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
}
