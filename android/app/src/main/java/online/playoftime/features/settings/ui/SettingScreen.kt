package online.playoftime.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import online.playoftime.R
import online.playoftime.core.utils.Hand
import online.playoftime.core.utils.beatsPerSecondLength
import online.playoftime.core.utils.defaultBeatsPerSecond
import online.playoftime.features.settings.ui.SettingsContract.Event
import online.playoftime.ui_lib.Icon
import online.playoftime.ui_lib.Screen
import online.playoftime.ui_lib.ToggleBox
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.baseBackgroundColor
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.baseTextStyle
import online.playoftime.ui_theme.disabledTextColor
import online.playoftime.ui_theme.disabledTextFieldStyle
import online.playoftime.ui_theme.disabledTextStyle
import online.playoftime.ui_theme.greyBackgroundColor
import online.playoftime.ui_theme.resultRedColor
import online.playoftime.ui_theme.smallShape
import online.playoftime.ui_theme.textFieldBackgroundColor

@Composable
fun SettingScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit
) {
    // Подключение вьюмодели
    val viewModel = koinViewModel<SettingsViewModel>()
    // Масштаб прогресс-бара (полуокно в мс) — прямой pref, отдельно от MVI.
    val barPrefs = org.koin.compose.koinInject<online.playoftime.core.data.LocalStorageService>()
    var barScale by remember { mutableStateOf(barPrefs.getBarHalfWindowMs()) }
    var barAuto  by remember { mutableStateOf(barPrefs.getBarScaleAuto()) }
    // Рука игрока (переехала из верхней строки главного экрана) — прямой pref.
    var playHand by remember { mutableStateOf(barPrefs.getSelectedHand()) }

    // Состояние экрана
    val state = viewModel.viewState.value

    // События
    val setEvent: (Event) -> Unit = { viewModel.setEvent(event = it) }

    // Диалог подтверждения сброса / спиннер во время wipe — перекрывают весь
    // экран (Alex 2026-08-14, баг «кнопка удалить данные не срабатывает»).
    // Раньше эти блоки рендерились КАК СИБЛИНГИ основного Column, БЕЗ return —
    // то есть SettingScreen() эмитил ДВА top-level composable за один проход.
    // Тап по кнопке реально переключал state.confirmReset (событие доходило
    // до ViewModel — подтверждено проверкой SharedPreferences на реальном
    // устройстве), но диалог визуально не появлялся: слот страницы внутри
    // HorizontalPager, судя по всему, не стекирует лишние корневые узлы как Box.
    // MainScreen.kt для аналогичных полноэкранных оверлеев (WelcomeOverlay /
    // GreetingView / FailCard) использует именно early-return — тот же паттерн
    // применён здесь, ДО основного Column.
    if (state.confirmReset) {
        ResetConfirmDialog(
            onConfirm = { setEvent(Event.ConfirmReset) },
            onCancel  = { setEvent(Event.CancelReset) }
        )
        return
    }
    if (state.resetInProgress) {
        online.playoftime.ui_lib.AppSpinner()
        return
    }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background((MaterialTheme.colorScheme.baseBackgroundColor))
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Секция Start over удлинила экран — на маленьких дисплеях
                // без скролла нижние опции не влезали.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.verticalPadding)
        ) {
            // ── Порядок настроек (Alex 2026-09-10, дополнено) ───────────────
            // 1 cheer-ups · 1b celebration sounds · 1c twin outline · 2 sound ·
            // 3 vibration · 4 beats/sec · 5 numeric time · 6 progress bar ·
            // 7 bar scale · 8 touch sampling · 9 erase data

            //region 1. Cheer-Ups
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.cheer_ups,
                isOn = state.isCheerUpsEnabled,
                onToggleClick = {
                    setEvent(Event.ToggleCheerUps)
                }
            )
            //endregion

            //region 1b. Звуки салюта/HOORAY!/sorry (Alex 2026-09-10) — отдельно
            // от Cheer-Ups: та настройка про текст бабла/подсветку, эта чисто
            // про звук (фейерверк на GreetingView + победный HOORAY! + sorry
            // при провале окна).
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.celebration_sounds,
                isOn = state.isCelebrationSoundsEnabled,
                onToggleClick = {
                    setEvent(Event.ToggleCelebrationSounds)
                }
            )
            //endregion

            //region 1c. Обводка близнецов/соседей в логе (Alex 2026-09-10)
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.twin_outline,
                isOn = state.isTwinOutlineEnabled,
                onToggleClick = {
                    setEvent(Event.ToggleTwinOutline)
                }
            )
            //endregion

            //region 2. Звук
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.sound,
                isOn = state.isSoundMode,
                onToggleClick = {
                    setEvent(Event.SwitchSoundMode)
                }
            )
            //endregion

            //region 3. Вибрация
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.vibration,
                isOn = state.isVibrationMode,
                onToggleClick = {
                    setEvent(Event.SwitchVibrationMode)
                }
            )
            //endregion

            //region 4. Ударов в минуту
            Row(
                modifier = Modifier
                    .padding(
                        start = Dimens.horizontalPadding,
                        top = Dimens.largeVerticalPadding,
                        end = Dimens.horizontalPadding
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                if (!(state.isSoundMode || state.isVibrationMode)) {
                    Icon(
                        modifier = Modifier
                            .padding(end = Dimens.horizontalPadding),
                        painter = painterResource(id = R.drawable.ic_lock),
                        color = MaterialTheme.colorScheme.disabledTextColor
                    )
                }
                BasicText(
                    modifier = Modifier.weight(1F),
                    text = stringResource(id = R.string.beats_per_second),
                    style = if (state.isSoundMode || state.isVibrationMode) {
                        baseTextStyle
                    } else {
                        disabledTextStyle
                    }
                )

                BasicTextField(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.textFieldBackgroundColor,
                            shape = smallShape
                        )
                        .width(Dimens.toggleWidth)
                        .focusable()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                if (state.beatsPerSecond.isNotEmpty()) {
                                    setEvent(Event.SetBeatsPerSecond(state.beatsPerSecond.toInt()))
                                } else {
                                    setEvent(Event.SetBeatsPerSecond(defaultBeatsPerSecond))
                                }
                            }
                        },

                    value = state.beatsPerSecond,
                    onValueChange = {
                        if (it.isNotEmpty()) {
                            // Только 1..16 (Alex 2026-10-27): «0» проходил и ронял
                            // приложение при следующем запуске (StopWatch делит на него 1000).
                            if ((it.toIntOrNull() ?: 0) in 1..beatsPerSecondLength) {
                                setEvent(Event.ChangeBeatsPerSecond(it))
                            } else {
                                it.dropLast(1)
                            }
                        } else {
                            setEvent(Event.ChangeBeatsPerSecond(it))
                        }
                    },
                    textStyle = if (state.isSoundMode || state.isVibrationMode) {
                        baseTextStyle.copy(textAlign = TextAlign.Center)
                    } else {
                        disabledTextFieldStyle.copy(textAlign = TextAlign.Center)
                           },
                    singleLine = true,
                    enabled = state.isSoundMode || state.isVibrationMode,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                )
            }
            //endregion

            //region 5. Числовой отсчёт
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.numeric_countdown,
                isOn = state.isNumericRunOn,
                onToggleClick = {
                    setEvent(Event.ToggleNumericRun)
                }
            )
            //endregion

            //region 6. Прогресс-бар
            ToggleBox(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                text = R.string.progress_bar,
                isOn = state.isProgressBarOn,
                onToggleClick = {
                    setEvent(Event.ToggleProgressBar)
                }
            )
            //endregion

            //region 7. Масштаб прогресс-бара (Alex 2026-07-25)
            if (state.isProgressBarOn) {
                BasicText(
                    text = "Progress bar scale",
                    modifier = Modifier.padding(
                        start = Dimens.horizontalPadding,
                        top = Dimens.largeVerticalPadding,
                        end = Dimens.horizontalPadding
                    ),
                    style = baseTextStyle.copy(
                        color = MaterialTheme.colorScheme.baseTextColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                online.playoftime.ui_lib.ScaleChips(
                    halfWindowMs = if (barAuto) 0 else barScale,
                    onChange = { v ->
                        if (v == 0) {
                            barAuto = true; barPrefs.putBarScaleAuto(true)
                        } else {
                            barAuto = false; barPrefs.putBarScaleAuto(false)
                            barScale = v; barPrefs.putBarHalfWindowMs(v)
                        }
                    },
                    modifier = Modifier.padding(
                        start = Dimens.horizontalPadding,
                        top = 8.dp,
                        end = Dimens.horizontalPadding
                    )
                )
                BasicText(
                    text = "«Auto» scales the bar to the level (tenths / hundredths / " +
                        "thousandths). Or pick a fixed span: bar edges = 3.000 ± this; " +
                        "«2–4 s» = 2.000–4.000, «0–4 s» shows the whole range.",
                    modifier = Modifier.padding(
                        start = Dimens.horizontalPadding,
                        top = 6.dp,
                        end = Dimens.horizontalPadding
                    ),
                    style = baseTextStyle.copy(
                        color = MaterialTheme.colorScheme.disabledTextColor,
                        fontSize = 12.sp
                    )
                )
            }
            //endregion

            //region 7b. Playing hand (переехал из верхней строки — Alex 2026-07-26)
            BasicText(
                text = "Playing hand",
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                style = baseTextStyle.copy(
                    color = MaterialTheme.colorScheme.baseTextColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Row(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = 8.dp,
                    end = Dimens.horizontalPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(Hand.LEFT to "Left", Hand.RIGHT to "Right").forEach { (h, lbl) ->
                    val sel = playHand == h
                    Box(
                        modifier = Modifier
                            .clip(smallShape)
                            .background(
                                if (sel) MaterialTheme.colorScheme.accentColor
                                else MaterialTheme.colorScheme.textFieldBackgroundColor
                            )
                            .clickable { playHand = h; barPrefs.putSelectedHand(h) }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text  = lbl,
                            style = baseTextStyle.copy(
                                color = if (sel) androidx.compose.ui.graphics.Color(0xFF1A1300)
                                        else MaterialTheme.colorScheme.baseTextColor,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
            BasicText(
                text = "Which hand you tap with. It's saved with every attempt so your " +
                    "stats can compare hands — it does not change the gameplay itself.",
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = 6.dp,
                    end = Dimens.horizontalPadding
                ),
                style = baseTextStyle.copy(
                    color = MaterialTheme.colorScheme.disabledTextColor,
                    fontSize = 12.sp
                )
            )
            //endregion

            //region 8. Touch sampling — диагностика + адаптивный дизеринг (Alex 2026-07-26)
            BasicText(
                text = "Touch sampling rate",
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = Dimens.largeVerticalPadding,
                    end = Dimens.horizontalPadding
                ),
                style = baseTextStyle.copy(
                    color = MaterialTheme.colorScheme.baseTextColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
            online.playoftime.ui_lib.TouchRateMeter(
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = 6.dp,
                    end = Dimens.horizontalPadding
                )
            )
            BasicText(
                text = "Touch panels sample your finger in discrete steps, so the exact " +
                    "tap moment snaps to a grid and some values (like exactly 3.000) fall " +
                    "in the gaps. Dithering fixes this: a tiny random shift, one grid cell " +
                    "wide, spreads that unavoidable uncertainty evenly — every value, " +
                    "including exactly 3.000, becomes equally likely.\n\n" +
                    "It only applies at the millisecond level (Level 3). At tenths and " +
                    "hundredths of a second the win window is far wider than the touch " +
                    "cell, so the grid never blocks a hit and the raw time is kept as-is — " +
                    "no dithering there.\n\n" +
                    "It is now ADAPTIVE: the game keeps measuring your real tap rate and " +
                    "sizes the shift to match. Effective precision = half the touch cell " +
                    "(half of 1000 / rate):\n" +
                    "   120 Hz → ±0.004 s      250 Hz → ±0.002 s\n" +
                    "   500 Hz → ±0.001 s      1000 Hz → OFF\n\n" +
                    "At ~1000 Hz and above the grid already resolves to the millisecond, so " +
                    "dithering switches OFF automatically — no shift is added.\n\n" +
                    "A single tap uses the panel's BASELINE rate (press & hold still to read " +
                    "it), not the higher rate seen while dragging.",
                modifier = Modifier.padding(
                    start = Dimens.horizontalPadding,
                    top = 8.dp,
                    end = Dimens.horizontalPadding,
                    bottom = Dimens.largeVerticalPadding
                ),
                style = baseTextStyle.copy(
                    color = MaterialTheme.colorScheme.disabledTextColor,
                    fontSize = 12.sp
                )
            )
            //endregion

            //region 9. Erase data (Alex 2026-07-15) + Export (Alex 2026-08-22,
            // moved here from the bottom nav bar — "рядом с удалением истории")
            StartOverSection(
                enabled  = !state.resetInProgress,
                onErase  = { setEvent(Event.RequestReset) },
                onExport = online.playoftime.ui_lib.rememberExcelShareAction()
            )
            //endregion
        }
            // Sticky нижняя навигация вынесена в MainActivity (Alex 2026-08-08).
        }
}

// ── «Начать сначала» (Alex 2026-07-15) ──────────────────────────────────────

@Composable
private fun StartOverSection(
    enabled: Boolean,
    onErase: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier.padding(
            start = Dimens.horizontalPadding,
            end   = Dimens.horizontalPadding,
            top   = Dimens.largeVerticalPadding * 2
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicText(
            text  = "Start over",
            style = disabledTextStyle
        )
        // Export (Alex 2026-08-22): жил вкладкой в нижнем меню, переехал сюда —
        // рядом с "Erase all data", тот же визуальный стиль строки-кнопки, но
        // нейтральный цвет (не resultRedColor — не деструктивное действие).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(smallShape)
                .background(MaterialTheme.colorScheme.textFieldBackgroundColor)
                .clickable(enabled = enabled, onClick = onExport)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BasicText(
                text  = "Export attempt history",
                style = baseTextStyle.copy(color = MaterialTheme.colorScheme.accentColor, fontWeight = FontWeight.SemiBold)
            )
            BasicText(
                text  = "Share your full history as an Excel file",
                style = baseTextStyle.copy(
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.58f)
                )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(smallShape)
                .background(MaterialTheme.colorScheme.textFieldBackgroundColor)
                .clickable(enabled = enabled, onClick = onErase)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BasicText(
                text  = "Erase all data & start over",
                style = baseTextStyle.copy(color = resultRedColor, fontWeight = FontWeight.SemiBold)
            )
            BasicText(
                text  = "History is first saved to Downloads as an Excel file, " +
                        "then everything is wiped and the game restarts from Level 1",
                style = baseTextStyle.copy(
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.58f)
                )
            )
        }
    }
}

@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val cardInteraction = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.greyBackgroundColor.copy(alpha = 0.9f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.horizontalPadding * 2)
                .clip(smallShape)
                .background(MaterialTheme.colorScheme.textFieldBackgroundColor)
                // ФИКС: clickable(enabled=false) НЕ глотал тапы — тап по тексту
                // проваливался в фон и закрывал диалог. Включённый no-op
                // clickable без индикации ПЕРЕХВАТЫВАЕТ тап.
                .clickable(
                    interactionSource = cardInteraction,
                    indication        = null,
                    onClick           = {}
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText(
                text = "Erase ALL data and start over?\n\n" +
                    "1. Your full history is first saved to the Downloads folder " +
                    "as an Excel file.\n" +
                    "2. Then every attempt, session and coin is wiped.\n" +
                    "3. The game restarts from Level 1, Quest 1.\n\n" +
                    "This cannot be undone.",
                style = baseTextStyle.copy(fontSize = 14.sp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                BasicText(
                    text = "Cancel",
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = baseTextStyle.copy(fontWeight = FontWeight.SemiBold)
                )
                BasicText(
                    text = "SAVE & ERASE",
                    modifier = Modifier
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = baseTextStyle.copy(
                        color = resultRedColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}