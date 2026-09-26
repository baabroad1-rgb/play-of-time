package ru.iflex.timemvp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import ru.iflex.timemvp.features.leaderboard.ui.LeaderboardScreen
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
                val pagerState = rememberPagerState(pageCount = { 5 })
                val scope      = rememberCoroutineScope()
                val goTo: (Int) -> Unit = { idx ->
                    scope.launch { pagerState.animateScrollToPage(idx) }
                }

                // Sticky нижняя навигация вынесена СЮДА, единым экземпляром НАД
                // HorizontalPager (Alex 2026-08-08): раньше каждая из 4 страниц
                // рисовала свою собственную BottomNavBar внутри страницы пейджера —
                // при свайпе бар уезжал вместе с контентом и пересоздавался при
                // каждом переключении (дёрганье, лишняя перерисовка). Теперь бар —
                // единственный composable-инстанс, стоит неподвижно под пейджером;
                // пейджер анимирует только область контента над ним.
                ru.iflex.timemvp.ui_lib.Screen {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state    = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) { page ->
                            // Page-индексы СОВПАДАЮТ с визуальным порядком вкладок
                            // BottomNavBar (Alex 2026-09-10: «свап по последовательности
                            // должен совпадать с последовательностью меню» — раньше
                            // pager-индексы шли Play/Analytics/Settings/About/Ranks, а
                            // визуально вкладки стояли Play/Ranks/Analytics/Settings/About,
                            // так что свайп вправо от Play уводил на Analytics, хотя
                            // соседняя вкладка визуально — Ranks). Теперь оба порядка
                            // один и тот же: 0=Play, 1=Ranks, 2=Analytics, 3=Settings, 4=About.
                            when (page) {
                                0 -> MainScreen(
                                    currentPage  = pagerState.currentPage,
                                    onSelectPage = goTo
                                )
                                1 -> LeaderboardScreen(
                                    currentPage  = pagerState.currentPage,
                                    onSelectPage = goTo
                                )
                                2 -> StatScreen(
                                    currentPage  = pagerState.currentPage,
                                    onSelectPage = goTo,
                                    onEarnMore   = { goTo(0) }
                                )
                                3 -> SettingScreen(
                                    currentPage  = pagerState.currentPage,
                                    onSelectPage = goTo
                                )
                                4 -> AboutScreen(
                                    currentPage  = pagerState.currentPage,
                                    onSelectPage = goTo
                                )
                            }
                        }

                        // Export убран отсюда (Alex 2026-08-22) — переехал в
                        // Settings, рядом с «Erase all data».
                        ru.iflex.timemvp.ui_lib.BottomNavBar(
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
