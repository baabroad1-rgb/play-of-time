package ru.iflex.timemvp.features.settings.ui.use_case

import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.core.utils.Accuracy

class SetAccuracyUseCase (
    private val localStorage: LocalStorageService
) {
    operator fun invoke(accuracy: Accuracy) {
        localStorage.putAccuracy(accuracy)
    }
}