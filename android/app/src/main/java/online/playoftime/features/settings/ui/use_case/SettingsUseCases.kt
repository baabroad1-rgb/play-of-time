package online.playoftime.features.settings.ui.use_case

data class SettingsUseCases(
    val switchSoundMode: SwitchSoundModeUseCase,
    val switchVibrationMode: SwitchVibrationModeUseCase,
    val setBeatsPerSecond: SetBeatsPerSecondUseCase,
)
