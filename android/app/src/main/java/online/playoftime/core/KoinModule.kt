package online.playoftime.core


import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.data.LocalStorageServiceContract
import online.playoftime.core.telemetry.AnalyticsReporter
import online.playoftime.core.telemetry.CrashReporter
import online.playoftime.core.telemetry.NoOpAnalyticsReporter
import online.playoftime.core.telemetry.NoOpCrashReporter
import online.playoftime.database.TimeDatabase
import online.playoftime.features.main.ui.MainViewModel
import online.playoftime.features.settings.ui.SettingsViewModel
import online.playoftime.features.settings.ui.use_case.SetBeatsPerSecondUseCase
import online.playoftime.features.settings.ui.use_case.SettingsUseCases
import online.playoftime.features.settings.ui.use_case.SwitchSoundModeUseCase
import online.playoftime.features.settings.ui.use_case.SwitchVibrationModeUseCase
import online.playoftime.features.stats.domain.usecase.ExportToExcelUseCase
import online.playoftime.features.stats.domain.usecase.StatUseCases
import online.playoftime.features.stats.domain.usecase.TotalDaysCount
import online.playoftime.features.stats.domain.usecase.AnalyticsUseCase
import online.playoftime.features.stats.domain.usecase.YourAnrUseCase
import online.playoftime.features.main.domain.usecase.GetLogAttemptsUseCase
import online.playoftime.features.main.domain.usecase.MainMetricsUseCase
import online.playoftime.features.main.domain.usecase.SerenityBalanceUseCase
import online.playoftime.features.stats.ui.StatsViewModel
import online.playoftime.features.leaderboard.data.LeaderboardApi
import online.playoftime.features.leaderboard.domain.usecase.LeaderboardSyncManager
import online.playoftime.features.leaderboard.ui.LeaderboardViewModel
import online.playoftime.use_case.AddAttemptUseCase
import online.playoftime.use_case.AddSessionUseCase
import online.playoftime.use_case.CalculateScmUseCase
import online.playoftime.use_case.ContextUseCase
import online.playoftime.use_case.GetCurrentSessionIdUseCase
import online.playoftime.use_case.MarkSessionQuestCompleteUseCase
import online.playoftime.use_case.RecalculateScmUseCase
import online.playoftime.use_case.SaveScmAfterAttemptUseCase
import online.playoftime.use_case.DetectErrorPatternsUseCase
import online.playoftime.use_case.GetQuestSummaryUseCase
import online.playoftime.use_case.GetSessionComparisonUseCase
import online.playoftime.use_case.UpdateSessionUseCase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE TryDatabaseModel ADD COLUMN hand TEXT NOT NULL DEFAULT 'RIGHT'")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE TryDatabaseModel ADD COLUMN scm REAL")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE SessionDatabaseModel ADD COLUMN isQuestComplete INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE SessionDatabaseModel ADD COLUMN questLevelId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SessionDatabaseModel ADD COLUMN questTaskId INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Хранение допуска ±t per attempt — нужно для корректного цвета строки лога
        // при загрузке исторических попыток (subquest-победа должна оставаться зелёной).
        db.execSQL("ALTER TABLE TryDatabaseModel ADD COLUMN toleranceMs INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Play Time теперь считается ТОЛЬКО пока MainScreen активен (Lifecycle=RESUMED).
        // До этого использовалось (endTime − startTime) — wall-clock от первого RUN до
        // последнего STOP, включая время когда игрок ушёл на Analytics или свернул app.
        // Новая колонка activeTimeMs тикается MainViewModel'ом; для исторических сессий
        // бэкфилл'ится (endTime − startTime) чтобы не занулять историю.
        db.execSQL("ALTER TABLE SessionDatabaseModel ADD COLUMN activeTimeMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE SessionDatabaseModel SET activeTimeMs = " +
                "CASE WHEN endTime > startTime THEN endTime - startTime ELSE 0 END"
        )
    }
}

@Volatile
private var timeDatabaseInstance: TimeDatabase? = null

/**
 * Единственный на процесс экземпляр базы. Нужен не только Koin'у: к той же базе
 * обращается PlayDataProvider, а ContentProvider создаётся РАНЬШЕ
 * Application.onCreate, то есть до старта Koin — полагаться там на DI нельзя.
 * Оба пути идут сюда, поэтому экземпляр остаётся один и миграции не расходятся.
 */
fun provideTimeDatabase(context: Context): TimeDatabase =
    timeDatabaseInstance ?: synchronized(TimeDatabase::class.java) {
        timeDatabaseInstance ?: Room.databaseBuilder(
            context.applicationContext,
            TimeDatabase::class.java,
            "time_database.db"
        ).addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
        ).build().also { timeDatabaseInstance = it }
    }

val appModule = module {
    single { provideTimeDatabase(androidApplication()) }

    singleOf(::LocalStorageService) {
        bind<LocalStorageServiceContract>()
    }

    // Telemetry (Alex 2026-09-01): no-op сегодня, реальный backend (Firebase
    // Crashlytics/Analytics или self-hosted) подключается заменой ЭТОГО
    // биндинга — call-сайты в MainViewModel/MainApplication/ExcelShareUtil
    // трогать не придётся.
    singleOf(::NoOpAnalyticsReporter) { bind<AnalyticsReporter>() }
    singleOf(::NoOpCrashReporter) { bind<CrashReporter>() }

    viewModelOf(::MainViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::StatsViewModel)
    viewModelOf(::LeaderboardViewModel)

    // Leaderboard (Alex 2026-08-12): тот же сервер, что у webapp.
    // single { LeaderboardApi() }, а не singleOf(::LeaderboardApi) — конструктор
    // принимает baseUrl: String = "..." с дефолтом, но singleOf пытается
    // зарезолвить ЛЮБОЙ параметр конструктора как DI-зависимость, и падает на
    // NoBeanDefFoundException для типа String (нет биндинга на голый String).
    single { LeaderboardApi() }
    singleOf(::LeaderboardSyncManager)

    // Самообновление вне Google Play (Alex 2026-09-22) — тот же "голый
    // String-параметр с дефолтом" повод писать single{} вручную, что и у LeaderboardApi.
    single { online.playoftime.core.update.UpdateApi() }

    factoryOf(::ContextUseCase)
    factoryOf(::AddAttemptUseCase)
    factoryOf(::AddSessionUseCase)
    factoryOf(::GetCurrentSessionIdUseCase)

    factoryOf(::SettingsUseCases)
    factoryOf(::SwitchSoundModeUseCase)
    factoryOf(::SwitchVibrationModeUseCase)
    factoryOf(::SetBeatsPerSecondUseCase)

    // Аналитика (после чистки 2026-07-14 оставлены только реально дергаемые
    // из StatsViewModel + YourAnrUseCase, который используется MainViewModel).
    factoryOf(::StatUseCases)
    factoryOf(::YourAnrUseCase)
    factoryOf(::ExportToExcelUseCase)
    factoryOf(::TotalDaysCount)
    factoryOf(::AnalyticsUseCase)

    factoryOf(::UpdateSessionUseCase)
    factoryOf(::MarkSessionQuestCompleteUseCase)
    factoryOf(::DetectErrorPatternsUseCase)
    factoryOf(::GetQuestSummaryUseCase)
    factoryOf(::GetSessionComparisonUseCase)

    // SCM
    factoryOf(::CalculateScmUseCase)
    factoryOf(::SaveScmAfterAttemptUseCase)
    factoryOf(::RecalculateScmUseCase)

    // Метрики главного экрана
    factoryOf(::MainMetricsUseCase)
    factoryOf(::SerenityBalanceUseCase)
    factoryOf(::GetLogAttemptsUseCase)
}
