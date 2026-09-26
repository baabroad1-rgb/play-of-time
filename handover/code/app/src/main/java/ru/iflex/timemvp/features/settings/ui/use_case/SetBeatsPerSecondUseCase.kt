package ru.iflex.timemvp.features.settings.ui.use_case

import ru.iflex.timemvp.core.data.LocalStorageService

class SetBeatsPerSecondUseCase (
    private val localStorage: LocalStorageService
) {
    operator fun invoke(beatsCount: Int) {
        localStorage.putBeatsPerSecond(beatsCount)
    }
}