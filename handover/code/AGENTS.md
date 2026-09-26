# Play of Time — инструкции для AI-агента (Codex)

## Что это
Нативное Android-приложение (Kotlin + Jetpack Compose) — тренажёр восприятия времени
по методике Геллерштейна. Игрок жмёт RUN, ждёт ~3 секунды, жмёт STOP и пытается
попасть ровно в 3.000. Приложение считает точность, Serenity Coins (SCM) и ведёт
игрока по 15 квестам (3 уровня × 5 квестов, точность 0.1 → 0.01 → 0.001 с).

## Источники истины (в порядке приоритета)
1. **Код** в этой папке.
2. `OUTDATED_DOCS_AND_CURRENT_DECISIONS.md` (в этой папке; дубль на уровень выше) —
   какие пункты исходных документов устарели и что действует вместо них.
   Читать ПЕРЕД любыми решениями по механикам.
3. `CLAUDE.md` (в этой папке) — полная хроника разработки по датам: почему всё
   устроено так, известные баги, отложенные задачи. Это журнал предыдущего
   AI-ассистента; используй как справочник, раздел «What Still Needs To Be Done» —
   актуальный бэклог.
4. Документы Google Drive (`../docs/DOCS_INDEX.md`) — только как исторический
   контекст; при противоречии с кодом прав код.

## Стек и сборка
- Kotlin, Jetpack Compose, Room (schema v7), Koin DI, MVI (BaseViewModel + Contract).
- Сборка: `./gradlew assembleDebug` (Windows: `.\gradlew assembleDebug`). Нужен JDK 17+
  (разработка велась на Temurin 21).
- APK: `app/build/outputs/apk/debug/play-of-time-v<версия>.apk`.
- **Версионирование**: `app/version.properties` автобампается ПОСЛЕ сборки; APK
  именуется версией ДО бампа. Версию всегда называй по имени файла APK, не по логу
  «Version bumped → name=X» (тот печатает следующий номер).
- Эмулятор для проверки: любой API 34+; package `ru.iflex.timemvp.quest`,
  activity `ru.iflex.timemvp.MainActivity`.

## Структура
- `app/src/main/java/ru/iflex/timemvp/`
  - `features/main/` — главный экран (MainScreen/MainViewModel/MainContract,
    QuestProgress, TopMetricsBar, DistribChart, HelpTapOverlay, PeriodToggle)
  - `features/stats/` — аналитика (StatScreen, AnalyticsUseCase)
  - `features/settings/`, `features/about/`
  - `ui_lib/` — общие компоненты (CheerUpBubble, WelcomeOverlay, SessionSummaryOverlay,
    GreetingView, BottomNavBar, StopWatchButton, ExcelShareUtil)
  - `core/utils/` — Quest.kt (все 15 квестов + индульгенция), CheerUpMessages.kt,
    ErrorPattern.kt, tidyLineBreaks()
  - `core/data/LocalStorageService.kt` — SharedPrefs (состояние квеста, флаги)
  - `database/` — Room (TimeDatabase, миграции в core/KoinModule.kt)
  - `use_case/` — бизнес-логика (SCM, сессии, экспорт Excel)

## Жёсткие правила (нарушение = регрессия)
1. **HandToggle (выбор руки L/R) — легитимная фича, НЕ удалять.** Но в
   `MainViewModel.kt` есть `TEMP DEBUG`: при руке LEFT секундомер авто-стопается
   ровно на aim — это отладочный хак для тюнинга квестов, удалить только когда
   владелец скажет.
2. `SessionStatisticItemState.kt` НЕ удалять (используется GetLogAttemptsUseCase).
3. Поле `deflection` в TryDatabaseModel — legacy, оставить (совместимость БД).
4. isWin попытки замораживается в момент записи (поле `toleranceMs` в БД) — никаких
   ретро-переоценок.
5. Тексты для игрока — plain English, без технических слов «window», «attempt limit»,
   «adaptive parameter». Домашний словарь: precision, focus, threes, land, aim, series.
6. Типографика: значения не должны разрываться при переносе строк. Внутри смысловых
   сегментов — NBSP ( ); в баблах текст прогоняется через `tidyLineBreaks()`
   в точке рендера. В инфо-строке квеста переносы допустимы только на «·».
7. Стиль баблов/оверлеев: фон #F5C02E, текст #2A1E00, скругление 12–16dp.
8. При изменении формулы SCM — bump ключа рекалькуляции в LocalStorageService
   (scm_recalculated_vN) и комментария в RecalculateScmUseCase.
9. Room-миграции только аддитивные, добавлять в KoinModule.addMigrations.
10. Числа > 999 в UI — разделитель разрядов пробелом.

## Домены-ловушки
- **Индульгенция квестов**: только одна адаптация за раз (IN_N → N±33, ROW → t±1ms),
  капы N≤99 / t≤3ms, три правила выхода (см. OUTDATED-док, раздел 1). Логика в
  `MainViewModel.onQuestWindowSuccess/Failure`, `checkVictory`, `Quest.kt`.
- **Play time** = `SessionDatabaseModel.activeTimeMs` — тикает только при активном
  MainScreen. Не возвращаться к endTime−startTime.
- **Session/attempts race**: addSession асинхронный, STOP-обработчик ждёт
  `pendingAddSession?.join()`. UpdateSessionUseCase пишет ТОЛЬКО endTime+activeTimeMs
  (не полный объект) — иначе затрёт isQuestComplete.
- Списки попыток в state — newest-first.

## Стиль работы с владельцем (Alex)
- Общение по-русски; тексты в приложении — английские.
- Визуальные изменения показывать макетом ДО реализации; правки принимать по одной
  (row-by-row), не пакетами.
- Багфиксы важнее фич.
