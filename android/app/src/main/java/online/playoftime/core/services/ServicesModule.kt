package online.playoftime.core.services

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val services = module {
    singleOf(::VibrationService)
}