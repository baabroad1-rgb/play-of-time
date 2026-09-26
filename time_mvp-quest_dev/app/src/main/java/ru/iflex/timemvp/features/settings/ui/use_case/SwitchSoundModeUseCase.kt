package ru.iflex.timemvp.features.settings.ui.use_case

import ru.iflex.timemvp.core.data.LocalStorageService

class SwitchSoundModeUseCase(
    private val localStorage: LocalStorageService
) {
    operator fun invoke(isOn: Boolean) {
        localStorage.putSoundOn(isOn)
    }
}