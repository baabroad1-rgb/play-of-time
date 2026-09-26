package ru.iflex.timemvp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ru.iflex.timemvp.core.utils.landscapeMode
import ru.iflex.timemvp.core.utils.orientationMode
import ru.iflex.timemvp.core.utils.portraitMode
import ru.iflex.timemvp.features.about.ui.AboutScreen
import ru.iflex.timemvp.features.main.ui.MainScreen
import ru.iflex.timemvp.features.settings.ui.SettingScreen
import ru.iflex.timemvp.features.stats.ui.StatScreen
import ru.iflex.timemvp.ui_lib.DrawerContent
import ru.iflex.timemvp.ui_lib.menus
import ru.iflex.timemvp.ui_theme.TimeMVPTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeMVPTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            DrawerContent(menus) { route ->
                                coroutineScope.launch {
                                    drawerState.close()
                                }

                                navController.navigate(route)
                            }

                        }
                    }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.MainScreen
                    ) {

                        composable<NavRoutes.MainScreen> {
                            MainScreen(
                                drawerState = drawerState,
                                navController = navController
                            )
                        }

                        composable<NavRoutes.StatScreen> {
                            StatScreen(
                                drawerState = drawerState,
                                onEarnMore  = { navController.navigate(NavRoutes.MainScreen) }
                            )
                        }

                        composable<NavRoutes.SettingScreen> {
                            SettingScreen(
                                drawerState = drawerState
                            )
                        }

                        composable<NavRoutes.AboutScreen> {
                            AboutScreen(
                                drawerState = drawerState
                            )
                        }

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
}
