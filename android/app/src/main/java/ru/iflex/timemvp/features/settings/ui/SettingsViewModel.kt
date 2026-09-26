package ru.iflex.timemvp.features.settings.ui

import android.content.Intent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.iflex.timemvp.R
import ru.iflex.timemvp.core.BaseViewModel
import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.excelFileName
import ru.iflex.timemvp.core.utils.getCurrentQuest
import ru.iflex.timemvp.core.utils.orientationMode
import ru.iflex.timemvp.core.utils.portraitMode
import ru.iflex.timemvp.core.utils.writeExcelToDownloads
import ru.iflex.timemvp.database.TimeDatabase
import ru.iflex.timemvp.features.leaderboard.data.LeaderboardApi
import ru.iflex.timemvp.features.settings.ui.SettingsContract.Effect
import ru.iflex.timemvp.features.settings.ui.SettingsContract.Event
import ru.iflex.timemvp.features.settings.ui.SettingsContract.State
import ru.iflex.timemvp.features.settings.ui.use_case.SettingsUseCases
import ru.iflex.timemvp.features.stats.domain.usecase.ExportToExcelUseCase
import ru.iflex.timemvp.features.stats.domain.usecase.TotalDaysCount
import ru.iflex.timemvp.use_case.ContextUseCase

class SettingsViewModel(
    private val context: ContextUseCase,
    private val settingsUseCases: SettingsUseCases,
    private val localStorage: LocalStorageService,
    private val timeDatabase: TimeDatabase,
    private val exportToExcel: ExportToExcelUseCase,
    private val totalDaysCount: TotalDaysCount,
    private val leaderboardApi: LeaderboardApi
) : BaseViewModel<Event, State, Effect>() {
    override fun setInitialState() = State()

    init {
        with(context()) {
            Intent().also { intent ->
                intent.setAction(orientationMode)
                intent.putExtra(orientationMode, portraitMode)
                sendBroadcast(intent)
            }
        }

        viewModelScope.launch {
            setState {
                copy(
                    title             = context.context.getString(R.string.settings_screen_title),
                    isSoundMode       = localStorage.getSoundOn(),
                    isVibrationMode   = localStorage.getVibrationOn(),
                    beatsPerSecond    = localStorage.getBeatsPerSecond().toString(),
                    isCheerUpsEnabled          = localStorage.getCheerUpsEnabled(),
                    isCelebrationSoundsEnabled = localStorage.getCelebrationSoundsEnabled(),
                    isTwinOutlineEnabled       = localStorage.getTwinOutlineEnabled(),
                    isProgressBarOn   = localStorage.getShowProgressBar(),
                    isNumericRunOn    = localStorage.getShowNumericRun()
                )
            }
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.SwitchSoundMode -> {
                viewModelScope.launch {
                    settingsUseCases.switchSoundMode(!viewState.value.isSoundMode)
                    setState { copy(isSoundMode = !viewState.value.isSoundMode) }
                }
            }

            is Event.SwitchVibrationMode -> {
                viewModelScope.launch {
                    settingsUseCases.switchVibrationMode(!viewState.value.isVibrationMode)
                    setState { copy(isVibrationMode = !viewState.value.isVibrationMode) }
                }
            }

            is Event.SetBeatsPerSecond -> {
                viewModelScope.launch {
                    settingsUseCases.setBeatsPerSecond(event.beats)
                }
            }

            is Event.ChangeBeatsPerSecond -> {
                viewModelScope.launch {
                    setState { copy(beatsPerSecond = event.beats) }
                }
            }

            is Event.ToggleCheerUps -> {
                val newValue = !viewState.value.isCheerUpsEnabled
                localStorage.putCheerUpsEnabled(newValue)
                setState { copy(isCheerUpsEnabled = newValue) }
            }

            is Event.ToggleCelebrationSounds -> {
                val newValue = !viewState.value.isCelebrationSoundsEnabled
                localStorage.putCelebrationSoundsEnabled(newValue)
                setState { copy(isCelebrationSoundsEnabled = newValue) }
            }

            is Event.ToggleTwinOutline -> {
                val newValue = !viewState.value.isTwinOutlineEnabled
                localStorage.putTwinOutlineEnabled(newValue)
                setState { copy(isTwinOutlineEnabled = newValue) }
            }

            is Event.ToggleProgressBar -> {
                val newValue = !viewState.value.isProgressBarOn
                localStorage.putShowProgressBar(newValue)
                setState { copy(isProgressBarOn = newValue) }
            }

            is Event.ToggleNumericRun -> {
                val newValue = !viewState.value.isNumericRunOn
                localStorage.putShowNumericRun(newValue)
                setState { copy(isNumericRunOn = newValue) }
            }

            is Event.RequestReset -> setState { copy(confirmReset = true) }
            is Event.CancelReset  -> setState { copy(confirmReset = false) }

            is Event.ConfirmReset -> {
                if (viewState.value.resetInProgress) return
                setState { copy(confirmReset = false, resetInProgress = true) }
                viewModelScope.launch(Dispatchers.IO) {
                    // 1. Принудительный экспорт — история уезжает в Downloads
                    //    ДО удаления, без share-диалога (отменить нельзя).
                    runCatching {
                        val days = totalDaysCount()
                        val data = exportToExcel()
                        if (data.isNotEmpty()) {
                            writeExcelToDownloads(
                                context.context, "$days$excelFileName", data
                            )
                        }
                    }
                    // 1.5. Сообщаем серверу, что игрок начал сначала — иначе
                    //    его строка в Ranks (coins/level) осталась бы на
                    //    старых значениях после локального сброса (Alex
                    //    2026-09-03, парный фикс к веб-стороне). Best-effort,
                    //    как обычный leaderboard sync: сеть недоступна —
                    //    runCatching молча проглатывает, Reset не блокируется
                    //    и не откатывается из-за этого.
                    runCatching {
                        leaderboardApi.resetEpoch(
                            org.json.JSONObject().apply {
                                put("playerId", localStorage.getOrCreatePlayerId())
                                put("syncToken", localStorage.getOrCreateSyncToken())
                            }
                        )
                    }
                    // 2. Wipe. clearAllTables обязателен вне main-потока — мы на IO.
                    runCatching { timeDatabase.clearAllTables() }
                    // 3. Прогресс → L1.1. СИНХРОННЫЙ commit() внутри — apply()
                    //    не успевал доехать до диска до killProcess, и после
                    //    рестарта игрок оказывался на СТАРОМ уровне (баг v12.35).
                    localStorage.resetQuestProgressSync(getCurrentQuest(1, 1).accuracy)
                    // 4. Рестарт СТРОГО с Main-потока: startActivity с фонового
                    //    потока в гонке с exit() мог не успеть дойти до
                    //    ActivityManager — приложение просто закрывалось.
                    withContext(Dispatchers.Main) { restartApp() }
                }
            }
        }
    }

    /**
     * Полный перезапуск процесса. Нужен потому что MainViewModel держит
     * in-memory состояние (attempts, currentSession, logBuffer, restore-логика
     * в init) — «мягкий» сброс потребовал бы ручной синхронизации десятка
     * полей. Холодный старт гарантирует чистое состояние всех ViewModel'ей.
     *
     * Вызывается на Main-потоке. Intent уходит в ActivityManager (другой
     * процесс) и переживает killProcess — система поднимет новый процесс
     * под новую MainActivity.
     */
    private fun restartApp() {
        val ctx = context.context
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ctx.startActivity(intent)
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
