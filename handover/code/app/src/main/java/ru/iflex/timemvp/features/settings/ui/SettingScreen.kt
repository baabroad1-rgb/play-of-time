package ru.iflex.timemvp.features.settings.ui

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
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.core.utils.beatsPerSecondLength
import ru.iflex.timemvp.core.utils.defaultBeatsPerSecond
import ru.iflex.timemvp.features.settings.ui.SettingsContract.Event
import ru.iflex.timemvp.ui_lib.Icon
import ru.iflex.timemvp.ui_lib.Screen
import ru.iflex.timemvp.ui_lib.ToggleBox
import ru.iflex.timemvp.ui_theme.Dimens
import ru.iflex.timemvp.ui_theme.baseBackgroundColor
import ru.iflex.timemvp.ui_theme.baseTextColor
import ru.iflex.timemvp.ui_theme.baseTextStyle
import ru.iflex.timemvp.ui_theme.disabledTextColor
import ru.iflex.timemvp.ui_theme.disabledTextFieldStyle
import ru.iflex.timemvp.ui_theme.disabledTextStyle
import ru.iflex.timemvp.ui_theme.greyBackgroundColor
import ru.iflex.timemvp.ui_theme.resultRedColor
import ru.iflex.timemvp.ui_theme.smallShape
import ru.iflex.timemvp.ui_theme.textFieldBackgroundColor

@Composable
fun SettingScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit
) {
    // Подключение вьюмодели
    val viewModel = koinViewModel<SettingsViewModel>()

    // Состояние экрана
    val state = viewModel.viewState.value

    // События
    val setEvent: (Event) -> Unit = { viewModel.setEvent(event = it) }

    Screen {

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
            //region "Звук"
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

            //region "Вибрация"
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

            //region "Cheer-Ups"
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

            //region "Ударов в минуту"
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
                            if (it.last().isDigit() && it.toInt() <= beatsPerSecondLength) {
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

            //region "Точность"
            Row(
                modifier = Modifier
                    .padding(
                        start = Dimens.horizontalPadding,
                        end = Dimens.horizontalPadding,
                        top = Dimens.largeVerticalPadding * 2
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {

                    Icon(
                        modifier = Modifier
                            .padding(end = Dimens.horizontalPadding),
                        painter = painterResource(id = R.drawable.ic_lock),
                        color = MaterialTheme.colorScheme.disabledTextColor
                    )

                BasicText(
                    modifier = Modifier.padding(

                    ),
                    text = stringResource(id = R.string.accuracy),
                    style = disabledTextStyle//baseTextStyle
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = Dimens.horizontalPadding)
                    .background(
                        color = MaterialTheme.colorScheme.textFieldBackgroundColor,
                        shape = smallShape
                    )
                    .fillMaxWidth()
            ) {
                AccuracyItem(
                    text = stringResource(id = R.string.deciseconds),
                    isSelected = state.accuracy == Accuracy.DECISECONDS,
                    onSelect = {
                        //setEvent(Event.SetAccuracy(Accuracy.DECISECONDS))
                    }
                )
                AccuracyItem(
                    text = stringResource(id = R.string.centiseconds),
                    isSelected = state.accuracy == Accuracy.CENTISECONDS,
                    onSelect = {
                        //setEvent(Event.SetAccuracy(Accuracy.CENTISECONDS))
                    }
                )
                AccuracyItem(
                    text = stringResource(id = R.string.milliseconds),
                    isSelected = state.accuracy == Accuracy.MILLISECONDS,
                    onSelect = {
                        //setEvent(Event.SetAccuracy(Accuracy.MILLISECONDS))
                    }
                )
            }
            //endregion

            //region "Start over" (Alex 2026-07-15, упрощено до одной кнопки)
            StartOverSection(
                enabled = !state.resetInProgress,
                onErase = { setEvent(Event.RequestReset) }
            )
            //endregion
        }
            // Sticky нижняя навигация.
            val exportAction = ru.iflex.timemvp.ui_lib.rememberExcelShareAction()
            ru.iflex.timemvp.ui_lib.BottomNavBar(
                currentPage  = currentPage,
                onSelectPage = onSelectPage,
                onExport     = exportAction
            )
        }

        // Диалог подтверждения сброса — поверх всего экрана.
        if (state.confirmReset) {
            ResetConfirmDialog(
                onConfirm = { setEvent(Event.ConfirmReset) },
                onCancel  = { setEvent(Event.CancelReset) }
            )
        }
        // Спиннер на время экспорта/wipe (до автоперезапуска).
        if (state.resetInProgress) {
            ru.iflex.timemvp.ui_lib.AppSpinner()
        }
    }
}

// ── «Начать сначала» (Alex 2026-07-15) ──────────────────────────────────────

@Composable
private fun StartOverSection(
    enabled: Boolean,
    onErase: () -> Unit
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
                    color    = MaterialTheme.colorScheme.baseTextColor.copy(alpha = 0.5f)
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