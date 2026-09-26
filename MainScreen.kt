package ru.iflex.timemvp.features.main.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.KoinApplication
import ru.iflex.timemvp.NavRoutes
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.appModule
import ru.iflex.timemvp.features.main.ui.MainContract.Event
import ru.iflex.timemvp.features.main.ui.MainContract.Event.StopWatchButton
import ru.iflex.timemvp.core.utils.CheerUpTrigger
import ru.iflex.timemvp.ui_lib.CheerUpDialog
import ru.iflex.timemvp.ui_lib.GreetingView
import ru.iflex.timemvp.ui_lib.HandToggle
import ru.iflex.timemvp.ui_lib.Screen
import ru.iflex.timemvp.ui_lib.stop_watch.StopWatchButton
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.core.utils.ErrorPattern
import ru.iflex.timemvp.ui_theme.accentColor
import ru.iflex.timemvp.ui_theme.baseBackgroundColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.baseTextStyle
import ru.iflex.timemvp.ui_theme.resultBlueColor
import ru.iflex.timemvp.ui_theme.resultRedColor
import ru.iflex.timemvp.ui_theme.resultYellowColor

@Composable
fun MainScreen(
    drawerState: DrawerState,
    navController: NavController
) {
    val viewModel = koinViewModel<MainViewModel>()
    val state = viewModel.viewState.value
    val setEvent: (Event) -> Unit = { viewModel.setEvent(event = it) }

    var showHelpDialog by remember { mutableStateOf(false) }

    // Диалог Cheer-Up
    if (state.cheerUpTrigger != CheerUpTrigger.NONE) {
        CheerUpDialog(
            specificText = state.cheerUpSpecificText,
            generalText  = state.cheerUpGeneralText,
            onDismiss    = { setEvent(Event.DismissCheerUp) }
        )
    }

    // Оверлей завершения квеста — перекрывает весь экран
    if (state.displayGreetingView) {
        GreetingView(
            greetingText = state.greetingViewText,
            questStats   = state.questCompletionStats,
            onClick      = { setEvent(Event.GreetingView.Close) }
        )
        return
    }

    Screen(
        title = state.title,
        drawerState = drawerState
    ) {
        Column {
            // ── Основной контент ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.baseBackgroundColor),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Метрики: Balance | Serenity | True Three — показываем всегда.
                // При старте без попыток данные загружены за всё время (ALL_TIME),
                // в первый запуск вообще — показатели будут нулевыми/прочерком.
                val totalAttempts = state.redCount + state.blueCount + state.trueThreeCount
                TopMetricsBar(
                    redCount             = state.redCount,
                    blueCount            = state.blueCount,
                    balanceSide          = state.balanceSide,
                    totalSerenityBalance = state.totalSerenityBalance,
                    todaySerenityDelta   = state.todaySerenityDelta,
                    trueThreeCount       = state.trueThreeCount,
                    totalAttempts        = totalAttempts
                )

                // ── Переключатель руки — НАД блоком квеста ────────────
                HandToggle(
                    selectedHand   = state.selectedHand,
                    onHandSelected = { setEvent(Event.SelectHand(it)) }
                )

                // ── Блок квеста: LEVEL X / quest Y + текст + кнопка ? ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.horizontalPadding)
                ) {
                    // Строка «LEVEL 3 / quest 4» + кнопка «?»
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text  = "LEVEL ${state.currentQuest.levelId} / quest ${state.currentQuest.taskId}",
                            style = TextStyle(
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color      = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.55f)
                            )
                        )

                        // Кнопка «?» — открывает подсказку о квестах и уровнях
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.accentColor.copy(alpha = 0.55f),
                                    shape = CircleShape
                                )
                                .clickable { showHelpDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text  = "?",
                                style = TextStyle(
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color      = MaterialTheme.colorScheme.accentColor.copy(alpha = 0.70f)
                                )
                            )
                        }
                    }

                    // Основной текст задания (крупный)
                    BasicText(
                        modifier = Modifier.padding(top = 4.dp),
                        text  = state.taskTitle,
                        style = TextStyle(
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.baseTextColor
                        )
                    )
                }

                // Кнопка-карточка с секундомером
                Box(contentAlignment = Alignment.Center) {
                    StopWatchButton(
                        modifier          = Modifier.padding(horizontal = Dimens.horizontalPadding),
                        state             = state.stopWatchState,
                        time              = state.time,
                        onPress           = { setEvent(StopWatchButton.Press) },
                        onRelease         = { setEvent(StopWatchButton.Release) },
                        enabled           = true,
                        isStopButtonActive = state.isStopButtonActive,
                        resultType        = state.buttonResultType
                    )
                }

                // Чип детектора паттерна ошибок (показывается при наличии паттерна)
                if (state.errorPattern != ErrorPattern.NONE) {
                    val (patternText, patternColor) = when (state.errorPattern) {
                        ErrorPattern.SYSTEMATIC_EARLY  -> "⟵ Систематически раньше цели" to resultRedColor
                        ErrorPattern.SYSTEMATIC_LATE   -> "⟶ Систематически позже цели" to resultBlueColor
                        ErrorPattern.INCREASING_DRIFT  -> "↗ Ошибки нарастают — drift" to resultYellowColor
                        ErrorPattern.JAGGED_RHYTHM     -> "↯ Рваный ритм" to resultYellowColor
                        ErrorPattern.NONE              -> "" to resultYellowColor
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.horizontalPadding, vertical = 2.dp)
                            .background(
                                color = patternColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = patternText,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = patternColor
                            )
                        )
                    }
                }

                // Лог попыток — занимает оставшееся место и скроллится
                if (state.displayedAttempts.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = Dimens.smallPadding)
                    ) {
                        items(state.displayedAttempts.size) { idx ->
                            SessionStatisticItem(
                                state           = state.displayedAttempts[idx],
                                scaleMaxMs      = state.scaleMaxMs,
                                earlyMeanMs     = state.geomMeanEarlyMs,
                                lateMeanMs      = state.geomMeanLateMs,
                                absoluteMeanMs  = state.geomMeanAbsoluteOffsetMs
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text  = "No data for this period",
                            style = baseTextStyle.copy(
                                textAlign = TextAlign.Center,
                                color     = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // ── Аналитика — зафиксирована над нижней панелью ──────────
            if (state.distribBuckets.isNotEmpty() && state.displayedAttempts.isNotEmpty()) {
                DistribChart(
                    buckets    = state.distribBuckets,
                    scaleMaxMs = state.scaleMaxMs,
                    earlyMs    = state.geomMeanEarlyMs,
                    lateMs     = state.geomMeanLateMs,
                    absoluteMs = state.geomMeanAbsoluteOffsetMs,
                    accuracy   = state.accuracy
                )
                GeomMeanRow(
                    earlyMs    = state.geomMeanEarlyMs,
                    absoluteMs = state.geomMeanAbsoluteOffsetMs,
                    lateMs     = state.geomMeanLateMs,
                    scaleMaxMs = state.scaleMaxMs,
                    accuracy   = state.accuracy
                )
            }

            // ── Нижняя панель ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.baseBackgroundColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PeriodToggle(
                    current  = state.periodFilter,
                    onSelect = { setEvent(Event.SelectPeriod(it)) }
                )

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { navController.navigate(NavRoutes.SettingScreen) },
                        painter           = painterResource(R.drawable.ic_settings),
                        contentDescription = "settings",
                        colorFilter       = ColorFilter.tint(
                            MaterialTheme.colorScheme.accentColor.copy(alpha = 0.7f)
                        )
                    )
                    Image(
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { navController.navigate(NavRoutes.StatScreen) },
                        painter           = painterResource(R.drawable.ic_stat),
                        contentDescription = "analytics",
                        colorFilter       = ColorFilter.tint(
                            MaterialTheme.colorScheme.accentColor.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }

        // ── Диалог-подсказка «Квест и уровень» ───────────────────────────
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor   = MaterialTheme.colorScheme.baseBackgroundColor,
                title = {
                    BasicText(
                        text  = "Quest & Level",
                        style = TextStyle(
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.baseTextColor
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BasicText(
                            text  = "Level\n\nYour current difficulty level (1–3). Higher levels demand finer precision:\n• Level 1 — within 100 ms\n• Level 2 — within 20 ms\n• Level 3 — within 10 ms\n\nComplete all quests in a level to advance.",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.85f)
                            )
                        )
                        BasicText(
                            text  = "Quest\n\nA specific challenge within your level. Each quest defines how many True Three hits you must land — once, twice, or three times, either in a row or per session.\n\nComplete the quest to move to the next one.",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.85f)
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        BasicText(
                            text  = "Got it",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color    = MaterialTheme.colorScheme.accentColor
                            )
                        )
                    }
                }
            )
        }

        // Сообщение об успехе (оверлей)
        if (state.displayGreetingView) {
            GreetingView(
                greetingText = state.greetingViewText,
                onClick      = { setEvent(Event.GreetingView.Close) }
            )
        }
    }
}

@Preview
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    KoinApplication(application = {
        androidContext(context)
        modules(appModule)
    }) {
        MainScreen(
            drawerState   = rememberDrawerState(initialValue = DrawerValue.Closed),
            navController = rememberNavController()
        )
    }
}
