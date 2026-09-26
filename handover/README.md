# Play of Time — пакет передачи проекта (16 июля 2026, v12.39)

## Что в папке

```
handover/
├── README.md                            ← этот файл
├── OUTDATED_DOCS_AND_CURRENT_DECISIONS.md  ← устаревшие пункты gdrive-документов + актуальные решения
├── code/                                ← полный исходный код (без build-артефактов)
│   ├── AGENTS.md                        ← инструкции для Codex (читается автоматически)
│   ├── CLAUDE.md                        ← хроника разработки по датам (журнал + бэклог)
│   ├── keystore.jks                     ← ключ подписи
│   └── app/, gradle/, ...
├── docs/
│   ├── DOCS_INDEX.md                    ← индекс всех документов Google Drive со ссылками и статусом актуальности
│   ├── CLAUDE_history.md                ← копия хроники (= code/CLAUDE.md)
│   └── plan_remaining_work.md           ← аудит от 16.06 (частично устарел)
└── apk/
    └── play-of-time-v12.39.apk          ← актуальная debug-сборка (15 MB, R8 включён)
```

## Что нового в этой версии (12.11 → 12.39)

- **v12.22–v12.24**: устранён 30-мс лаг stopwatch-кнопки — kernel timestamps через
  `PointerInputChange.uptimeMillis`, drift-корректный тик на `SystemClock.uptimeMillis`,
  изоляция `state.time` в отдельный `MutableState<String>` (тик больше не рекомпозит
  MainScreen). Детали в `code/memory/perf_stopwatch_latency.md`.
- **v12.25**: golden LEVEL COMPLETE блок показывает "clean threes" только на L3
  (на L1/L2 отображается только `5/5 quests · N attempts`).
- **v12.26–v12.32**: массовая чистка dead code — удалены 27+ Kotlin файлов, 9 drawables,
  21 строковый ресурс, 7 полей `MainContract.State`, ~350 строк неиспользуемого кода.
  R8 включён на debug: 23 MB → 15 MB. Release: 10 MB.
- **v12.33**: закрыта TOCTOU-гонка последнего тика с заморозкой (`tickLock` в
  StopWatch.kt) — на кнопке и в логе больше не расходятся значения (~7 мс, 1/10 попыток).
- **v12.34**: статус-бар виден в полноэкранном режиме (скрыты только nav-кнопки);
  вибро в момент ФИКСАЦИИ числа (не на нажатие STOP); параметры Excitation/Inhibition
  (геом. среднее ранних/поздних ошибок, session vs history) в GreetingView; инфо-текст
  квеста растягивается на свободную ширину; метка периода на аналитике
  (Session #N / дата / месяц под period pill); аналитика перезагружается при каждом
  выходе на передний план (Event.Refresh, без мигания спиннера).
- **v12.35**: Settings → «Erase all data & start over» (одна кнопка): подтверждение →
  принудительный Excel-экспорт в Downloads → wipe БД → прогресс на L1.1 → авторестарт
  приложения. Общий `writeExcelToDownloads` в core/utils (используется и StatsViewModel).
- **v12.36**: фикс сброса — `resetQuestProgressSync()` пишет прогресс ОДНИМ editor'ом
  через синхронный `commit()`. Раньше `apply()` (async) не успевал доехать до диска
  до `Process.killProcess`, и после рестарта игрок оказывался на СТАРОМ уровне.
  Проверено end-to-end на эмуляторе: L2.4 → сброс → L1.1, xlsx в Downloads, БД пуста.

- **v12.37**: анимация «капли» — после каждой попытки капля цвета результата летит
  с конца эррор-бара свежей строки лога вниз на гистограмму; столбик подрастает
  в момент приземления (suppressErrorMs в DistribChart скрывает вклад до посадки).
- **v12.39**: фикс «Export failed: ...XSSFWorkbook» на R8-сборках (v12.33–v12.38):
  log4j-api — реальная runtime-зависимость POI, но R8 вырезал no-arg конструктор
  DefaultFlowMessageFactory (создаётся через Class.newInstance). Добавлен
  -keep class org.apache.logging.log4j.** в proguard-rules.pro — чинит экспорт
  и в debug, и в release.

Офлайн-копии docx/pdf с Google Drive при желании: правой кнопкой по папке
«play of time» в Drive → «Скачать» → распаковать в `docs/gdrive/`.

## Как собрать
1. JDK 17+ (разработка велась на Temurin 21) и Android SDK.
2. `local.properties` в пакет не включён (машинно-специфичный). На новой машине:
   задать переменную окружения `ANDROID_HOME`, либо создать `code/local.properties`
   со строкой `sdk.dir=<путь к Android SDK>`. Сборка чистой копии проверена на v12.32.
3. `cd code && ./gradlew assembleDebug` (или `assembleRelease` для 10-MB подписной сборки).
4. APK появится в `code/app/build/outputs/apk/debug/` (или `release/`). Имя APK = версия
   ДО автобампа (см. AGENTS.md, раздел «Версионирование»).
5. **Внимание про скорость сборки**: debug теперь тоже через R8 (`isMinifyEnabled = true`
   в `app/build.gradle.kts`). Первая сборка после clean — ~9 минут. Инкрементальные —
   быстро. Если R8 внезапно ломает поведение stopwatch-кнопки (что мы уже устранили
   архитектурно) — временно вернуть `isMinifyEnabled = false` в debug-блоке.

## Настройка Codex

Codex автоматически читает файл **AGENTS.md** из корня рабочей папки — он уже лежит
в `code/` и содержит: стек, сборку, структуру, жёсткие правила (что нельзя ломать),
домены-ловушки (индульгенция квестов, race conditions, play time) и стиль работы.

### Вариант A — Codex CLI
```
cd C:\PlayOfTime\handover\code
codex
```
Codex подхватит `AGENTS.md` сам. Первый промпт — см. ниже.

### Вариант B — Codex в IDE (VS Code) / Codex Web
Открыть папку `C:\PlayOfTime\handover\code` как workspace / указать её как репозиторий.
AGENTS.md в корне будет прочитан автоматически.

### Первый промпт (скопировать как есть)

```
Ты берёшь на сопровождение Android-проект Play of Time. Прежде чем что-либо менять:

1. Прочитай AGENTS.md (в корне) — правила, сборка, ловушки.
2. Прочитай OUTDATED_DOCS_AND_CURRENT_DECISIONS.md (в корне) — какие пункты исходной
   документации устарели и какие решения действуют сейчас. При противоречии
   документов и кода — прав код.
3. Просмотри CLAUDE.md (в корне) — это журнал разработки по датам; раздел
   "What Still Needs To Be Done" — актуальный бэклог.
4. Собери проект (./gradlew assembleDebug) и подтверди, что сборка зелёная.

После этого коротко перескажи мне: (а) как устроена система квестов с индульгенцией,
(б) как считается SCM, (в) три вещи, которые нельзя ломать. Ничего не менять,
пока я не подтвержу, что ты понял проект правильно.
```

### Правила общения с Codex по этому проекту
- Просить показывать макет/скриншот визуальных изменений ДО кода.
- Изменения согласовывать по одному (row-by-row), не пакетами.
- Требовать называть версию APK по имени файла в `app/build/outputs/apk/debug/`.
- После любого изменения квест-логики — прогон на эмуляторе с рукой LEFT
  (TEMP DEBUG хак: авто-стоп ровно на aim — позволяет быстро проходить квесты).

## Ключевые контакты и ресурсы
- Владелец: Alexander Belyshkin (концепт и продакт-решения).
- Сайт: playoftime.online · Google Drive папка: см. docs/DOCS_INDEX.md.
