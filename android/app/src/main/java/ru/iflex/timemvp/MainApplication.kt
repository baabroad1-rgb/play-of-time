package ru.iflex.timemvp

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.GlobalContext.startKoin
import ru.iflex.timemvp.core.appModule
import ru.iflex.timemvp.core.services.services
import ru.iflex.timemvp.core.telemetry.CrashReporter

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MainApplication)
            modules(appModule, services)
        }

        installCrashReporting()
    }

    /**
     * Оборачивает default uncaught-exception handler: сначала отдаёт throwable
     * в CrashReporter (сегодня no-op), затем всегда передаёт исходному
     * обработчику — поведение приложения при краше НЕ меняется, просто
     * появляется точка подключения реального backend'а (Alex 2026-09-01).
     */
    private fun installCrashReporting() {
        val crashReporter = get().get<CrashReporter>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { crashReporter.recordException(throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}