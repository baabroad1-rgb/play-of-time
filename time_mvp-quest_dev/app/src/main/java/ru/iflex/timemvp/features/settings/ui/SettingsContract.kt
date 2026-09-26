package ru.iflex.timemvp.features.settings.ui

import ru.iflex.timemvp.core.ViewEvent
import ru.iflex.timemvp.core.ViewSideEffect
import ru.iflex.timemvp.core.ViewState

class SettingsContract {

    data class State(
        val title: String = "",
        val isSoundMode: Boolean = true,
        val isVibrationMode:  Boolean = false,
        val beatsPerSecond:  String = "2",
        val isCheerUpsEnabled: Boolean = true,
        /** Звуки салюта/HOORAY!/sorry. Default true (Alex 2026-09-10). */
        val isCelebrationSoundsEnabled: Boolean = true,
        /** Обводка близнецов/соседей в логе. Default true (Alex 2026-09-10). */
        val isTwinOutlineEnabled: Boolean = true,
        /** Прогресс-бар секунд на кнопке секундомера. */
        val isProgressBarOn: Boolean = true,
        /** Числовой отсчёт на кнопке во время бега. */
        val isNumericRunOn: Boolean = true,
        /** true = показывается диалог подтверждения полного сброса. */
        val confirmReset: Boolean = false,
        /** true пока идёт экспорт/wipe — спиннер, повторные тапы заблокированы. */
        val resetInProgress: Boolean = false
    ) : ViewState

    sealed class Event : ViewEvent {
        data object SwitchSoundMode: Event()
        data object SwitchVibrationMode: Event()
        data class SetBeatsPerSecond(val beats: Int): Event()
        data class ChangeBeatsPerSecond(val beats: String): Event()
        data object ToggleCheerUps: Event()
        data object ToggleCelebrationSounds: Event()
        data object ToggleTwinOutline: Event()
        data object ToggleProgressBar: Event()
        data object ToggleNumericRun: Event()

        /** Тап по «Erase everything & start over» — показать подтверждение. */
        data object RequestReset: Event()
        data object CancelReset: Event()
        /** Подтверждено: принудительный Excel в Downloads → wipe БД →
         *  прогресс на L1.1 → рестарт приложения. */
        data object ConfirmReset: Event()
    }

    sealed class Effect : ViewSideEffect
}
