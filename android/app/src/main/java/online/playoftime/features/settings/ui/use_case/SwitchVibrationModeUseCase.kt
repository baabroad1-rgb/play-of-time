package online.playoftime.features.settings.ui.use_case

import online.playoftime.core.data.LocalStorageService

class SwitchVibrationModeUseCase(
    private val localStorage: LocalStorageService
) {
    operator fun invoke(isOn: Boolean) {
        localStorage.putVibrationOn(isOn)
    }
}