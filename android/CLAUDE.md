# Play of Time — Project Briefing for Claude Code

> **🔜 Открытое — `docs/TASKS.md`, раздел «Расчистка бэклога 25.10.2026»**: черты поведения сделаны 25.10 (7 черт, `behavioral_profile.py`); чистка базы — перед выкладкой в Google Play (Алекс: «потом»).
>
> **START HERE:** [`../docs/HANDOFF_2026-09-29.md`](../docs/HANDOFF_2026-09-29.md) — состояние проекта, что живое, что открыто, правила работы с Алексом. Читать перед любой работой.
>
> Предыдущие записки не удалены и местами полезны, но как «состояние» устарели: [`../docs/HANDOFF_2026-09-26.md`](../docs/HANDOFF_2026-09-26.md) — секреты, автоматика и три грабли (по этим темам всё ещё точна), [`../docs/HANDOFF_2026-09-20.md`](../docs/HANDOFF_2026-09-20.md) — досье проекта до переезда в git, таблица решений D29–D39. Инструменты из неё — в `../webapp/handoff/tools/`.

## What is this app?
Play of Time is a native Android stopwatch-based training app rooted in Gellerstein's psychotechnics methodology. The user taps RUN, waits ~3 seconds, taps STOP, and tries to hit exactly 3.000 seconds. The app tracks accuracy, calculates a "Serenity Coin Metric" (SCM), and progresses the user through quests.

## Tech Stack
- Native Android, Kotlin
- Jetpack Compose (UI)
- Room (database)
- Koin (dependency injection)
- MVI architecture (BaseViewModel, Contract pattern)
- Project path: `C:\PlayOfTime\android`
- Build: `.\gradlew clean assembleDebug`
- Emulator: API 34 (Pixel 6)

## App Structure
- `features/main/` — main stopwatch screen
- `features/stats/` — analytics screen (StatScreen.kt)
- `features/settings/` — settings screen
- `features/about/` — about screen
- `ui_lib/` — shared UI components (HandToggle, StopWatch, etc.)
- `core/utils/` — Quest system, accuracy levels, constants
- `database/` — Room DB (TimeDatabase, SessionDao, TryDao, StatisticDao)
- `use_case/` — business logic (SCM calculation, session management)

## Key Documentation (Google Drive, folder "play of time")
- `measures.docx` — all metric formulas and definitions
- `parametr_table.docx` — 38 parameters with exact calculations
- `plan4revision_v10.docx` — revision plan from product owner
- `plan_remaining_work.md` — prioritized audit with file-level fix instructions
- `list_of_quests.docx` — 10 quests, victory conditions, adaptation rules
- `cheer_ups.docx` — cheer-up trigger conditions and messages
- `Аналитический экран.pdf` — analytics screen design prototype
- `основной экран.pdf` — main screen design prototype
- `quests_oder.pdf` — quest flowchart with adaptation logic
- Google Drive folder ID: `1aC9cv460P-xKaI97zOKANlyBLrJUmk8C`
- **Полный индекс со статусами актуальности: [`../docs/DOCS_INDEX.md`](../docs/DOCS_INDEX.md)** (37 документов, сверено 26.09.2026). Список выше — подмножество.

## История разработки — в docs/CHANGELOG.md

Журнал `What Was Done (дата)` за июнь–сентябрь 2026 (36 записей) вынесен в
[`../docs/CHANGELOG.md`](../docs/CHANGELOG.md). Здесь остались только правила,
структура и актуальный бэклог — то, что нужно читать каждый раз.

**Записывать сразу, а не в конце разговора.** Алекс 29.09.2026: «всегда
после правки записывай всё в файл, потому что я не знаю когда закончатся
токены». Сессия обрывается без предупреждения; всё, что осталось только в
переписке, пропадает. Закончил правку — коммить. Договорились о чём-то на
словах — запиши, не дожидаясь реализации (образец незаконченного
проектирования: `../docs/FACE_V2_DESIGN.md`).

**Новые записи о сделанном — в `../docs/CHANGELOG.md`, в конец файла**, не сюда.
Этот файл должен остаться коротким: он загружается в контекст при каждом запуске.

Что менялось в коде построчно — `git log`. Журнал отвечает на вопрос «почему
так решили», git — на вопрос «что именно изменилось».


## ⚠️ Ветка 14.x заморожена — разработка только в 15.xx

Алекс 28.09.2026: «изменения которые мы делаем в нативе, в 14 версию не
переносятся. только в 15. 14 нужна только для преемственности».

Мостовая версия живёт в ветке `legacy-bridge`: старый пакет
`ru.iflex.timemvp.quest`, `versionName` 14.93. Её единственная работа —
дать тем, у кого стоит старое приложение, кнопку переезда на новое и
отдать историю через ContentProvider. Новых функций и починок она НЕ
получает, даже если правка ложится в неё за минуту. Номер 14.94 не
поднимаем: те, у кого 14.93 уже стоит, останутся с тем, что есть.

Вся разработка — в `main`, пакет `online.playoftime`, версии 15.xx.
Раздача: `api/latest_apk.php` отдаёт мостовую 14.9x, `api/next_apk.php` —
новое приложение 15.xx. Перепутать нельзя: игрок поставит не то и
переедет без истории. Проверяется `.github/workflows/check-api.yml`.

Когда переезд закончится (в группах 14.9x и «версия не сообщена» не
останется активных за неделю игроков — смотреть
`.github/workflows/check-migration.yml`): ветка `legacy-bridge` и
`api/latest_apk.php` больше не нужны, а `core/update/UpdateApi.kt` должен
вернуться с `next_apk.php` на `latest_apk.php` (об этом сказано и в самом
файле).

## ⚠️ УДАЛИТЬ ПРИ ВЫХОДЕ В GOOGLE PLAY — временная заплатка самообновления

Alex (22 сентября 2026): сначала согласился оставить JSON-вариант как
временную заплатку ("заплатка до перехода в гуглплэй... пометь что надо
убрать в релизе"), затем в том же разговоре сам отменил это решение —
JSON и переименование apk в "latest" не нравятся категорически: "именование
файлов сохраняется как есть, никаких ласт версион, определи актуальность по
имени единственного файла в папке."

**Финальная реализация (та же дата, позже в тот же день)**: JSON убран
полностью. `core/update/UpdateApi.kt` делает HEAD-запрос на уже существующий
`api/latest_apk.php` (его написала параллельная веб-сессия в тот же день для
ссылки "download app apk" на сайте — переиспользован, не задублирован),
читает настоящее имя файла из `Content-Disposition`, вытаскивает версию
регуляркой и сравнивает с `BuildConfig.VERSION_NAME`. APK на сервере лежит
под своим настоящим именем сборки (`play-of-time-vX.Y.apk`), без
переименования; `latest_apk.php` сам берёт самый свежий `.apk` из
`downloads/`, если их там вдруг несколько.

Самообновление вне Google Play (`core/update/UpdateApi.kt`,
`ui_lib/AppUpdateButton.kt`, вызов в `MainViewModel.init`, разрешение
`REQUEST_INSTALL_PACKAGES` в манифесте, cache-path `app_update` в
`file_provider_paths.xml`) — существует ТОЛЬКО пока приложение раздаётся
сайдлоадом с playoftime.online. Как только релиз уходит в Google Play — вся
эта native-часть удаляется целиком, Play сам обновляет приложение.
**`api/latest_apk.php` и папка `webapp/downloads/` НЕ удаляются** — ими
по-прежнему пользуется прямая ссылка "download app apk" на сайте,
независимо от того, есть Google Play или нет.

Подробности — doc-comment в начале `core/update/UpdateApi.kt`; инструкция
по деплою — `webapp/downloads/README_update_deploy.md`.

## What Still Needs To Be Done

### Priority 1: Main Screen Design Alignment
- Compare with `основной экран.pdf` design prototype
- Fix any discrepancies

### Priority 2: Sparkline Logic Per Period
Design says sparklines should vary by period:
- Session period: show last 3 attempts
- Today: show last 3 days
- All Time: no sparklines
- Currently always shows last 5 sessions — needs fixing in AnalyticsUseCase.kt

### Priority 3: Analytics Screen — Remaining Prototype Gaps
- Sparklines (TrendSparkline) still exist in PACE OF LEARNING card — prototype shows small two-line sparklines at card top; verify or remove
- Card typography: prototype uses specific font sizes/weights per card type — compare carefully
- The `?` global help button visible in prototype nav bar is not yet implemented

### Priority 4: Quest System — Remaining
- Quest 2.2 first-attempt bug: unknown symptom, could not reproduce from code alone. Need to reproduce on device and describe what goes wrong (wrong N shown? win not counted? window resets incorrectly?).
- Post-session GreetingView info: verify what the design spec requires. Currently shows Level/Quest label, Attempts, Wins, Hit Rate%. May need SCM earned or other fields.
- **TEMP DEBUG — remove after quest tuning is finalized**: `MainViewModel.kt` has a left-hand auto-stop hack. When the HandToggle is set to LEFT, the stopwatch auto-stops at the aim time so the quest-level logic can be exercised without reaction-time jitter. Records the attempt at exactly `startTime + currentAimTime` (so duration is a clean 3.000 s, registers as TRUE_THREE). Search for `TEMP DEBUG` in `MainViewModel.kt` to remove. The HandToggle UI itself is a real feature and must stay.

### Priority 5: Other Screens
- Update About screen content
- Verify cheer-up triggers match `cheer_ups.docx`
- Blue values: check hundredths vs thousandths precision

### Priority 6: Google Play Preparation
- Update app description from `about4googleplay_rus_and_eng.docx`

## Touch sampling / honest tap timing
**See [`../docs/touch_sampling.md`](../docs/touch_sampling.md)** — полный разбор: почему на L3 «ровно 3.000» почти не выбивалось (8-мс сетка = дефолтная частота ДОСТАВКИ касаний, НЕ предел панели; 120 Гц — это refresh экрана), и как включить честную высокую частоту опроса тача:
- **Game Mode** — `android:appCategory="game"` + `android:isGame="true"` в `AndroidManifest.xml` (главный рычаг, поднимает частоту опроса панели на поддерживающих устройствах).
- **requestUnbufferedDispatch(SOURCE_TOUCHSCREEN)** — в `StopWatchButton.kt`, функция `requestHiRateTouch()` (API 33+, пере-арм на каждый down).
- **Дизеринг АДАПТИВНЫЙ (v12.82)** — ширина = половина ИЗМЕРЕННОЙ ячейки (`TouchRateEstimator`, замер по тапам), авто-0 при ячейке ≤1.2 мс (~1000 Гц). Только L3 (на L1/L2 окно победы шире ячейки — дизеринга нет). `DEFAULT_TOUCH_CELL_MS=4.0` в `Utils.kt`, ячейка хранится в `LocalStorage.getTouchCellMs()`.
- **Компенсация echo/re-roll (v12.84)** — чтобы дизеринг не рассеивал физические повторы (близнецов, серии троек): повтор в ту же СЫРУЮ ячейку → то же значение (echo); узкое исключение — переброс 2999/3001 если промах (`1>tol`, под индульгенцией off). Без памяти (только предыдущая попытка). Детали: `../docs/touch_sampling.md`.
- **Проверка только по Excel-экспорту с реального устройства** (тест равномерности mod-8, скрипт в доке). Эмулятор частоту панели не отражает.

## Important Notes
- Hand toggle is a LEGITIMATE feature (tracks which hand user plays with) — do NOT remove it
- The `deflection` field still exists in TryDatabaseModel (DB compatibility) — just not used anymore
- SessionStatisticItemState.kt must NOT be deleted — it's still referenced by GetLogAttemptsUseCase
- PowerShell is used (not CMD) — use PowerShell syntax for terminal commands
- User prefers written communication over voice for technical work
- User's name: Alex (Alexandr Belyshkin), company: Usethics
