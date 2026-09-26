package ru.iflex.timemvp.features.settings.ui

import ru.iflex.timemvp.core.ViewEvent
import ru.iflex.timemvp.core.ViewSideEffect
import ru.iflex.timemvp.core.ViewState
import ru.iflex.timemvp.core.utils.Accuracy

class SettingsContract {

    data class State(
        val title: String = "",
        val isSoundMode: Boolean = true,
        val isVibrationMode:  Boolean = false,
        val beatsPerSecond:  String = "2",
        val accuracy: Accuracy = Accuracy.MILLISECONDS,
        val isCheerUpsEnabled: Boolean = true,
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
        data class SetAccuracy(val accuracy: Accuracy): Event()
        data object ToggleCheerUps: Event()

        /** Тап по «Erase everything & start over» — показать подтверждение. */
        data object RequestReset: Event()
        data object CancelReset: Event()
        /** Подтверждено: принудительный Excel в Downloads → wipe БД →
         *  прогресс на L1.1 → рестарт приложения. */
        data object ConfirmReset: Event()
    }

    sealed class Effect : ViewSideEffect
}
