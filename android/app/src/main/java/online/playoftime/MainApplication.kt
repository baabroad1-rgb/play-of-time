package online.playoftime

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.GlobalContext.startKoin
import online.playoftime.core.appModule
import online.playoftime.core.services.services
import online.playoftime.core.migration.LegacyImporter
import online.playoftime.core.telemetry.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ДО startKoin: прогресс по квестам и личность игрока читаются при
        // инициализации, подтянуть их позже значит показать первый уровень
        // и новую личность, которой сервер лидерборда не знает. Операция
        // разовая и короткая — это несколько десятков значений, не таблицы.
        // В мостовой версии переноса нет: идентификатор тот же, что у источника —
        // приложение импортировало бы данные само из себя.

        startKoin {
            androidContext(this@MainApplication)
            modules(appModule, services)
        }

        installCrashReporting()
    }

    /**
     * Перенос истории из предыдущего приложения. Вне главного потока: ходит
     * в чужой ContentProvider и пишет в базу. Ошибку глотаем намеренно —
     * неудавшийся перенос не должен мешать играть, а попытка повторится при
     * следующем запуске (подробности в LegacyImporter).
     */
    private fun importLegacyData() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { LegacyImporter.run(applicationContext) }
        }
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