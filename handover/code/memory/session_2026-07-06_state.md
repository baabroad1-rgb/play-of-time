---
name: session-2026-07-06-state
description: "Рабочее состояние сессий 6–7 июля 2026 (v11.84→v11.87): play-time трекер, фиксы лога, типографика"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3388c846-0089-498f-81d6-ccdb82bb1b92
---

# Состояние на 7 июля 2026, APK = play-of-time-v11.88.apk (интерактивная справка)

**ВАЖНО про версии:** автобампер именует APK ДО инкремента; в лог печатает СЛЕДУЮЩИЙ номер.
Называть версию по имени файла в `app/build/outputs/apk/debug/`, не по «Version bumped → name=X».

## Сделано 6–7 июля (см. также [[project-overview]])

### v11.84 — иконка Analytics
- `ic_stat.xml` — pie chart заменён на 3 растущих столбика (используется в BottomNavBar + PeriodPill).
- `StatScreen.PeriodPill`: слово «Analytics» → `Image(ic_stat)` 18dp tint accent. 4 кнопки фильтра получили ~40dp ширины.

### v11.85 — Play Time только при активном MainScreen
- Миграция Room **6→7**: `SessionDatabaseModel.activeTimeMs` (backfill из endTime−startTime).
- `MainViewModel`: `mainScreenActive` + `tickStartMs`; `startTickIfEligible()` / `flushTick()`.
  Тикер идёт когда MainScreen RESUMED И currentSession != null.
  flushTick в 4 закрывающих ветках (contraction/session/fail/hurray) + OnScreenInactive.
- `MainContract.Event.OnScreenActive/OnScreenInactive`; MainScreen шлёт их через
  `DisposableEffect(LocalLifecycleOwner)` + LifecycleEventObserver (ON_RESUME/ON_PAUSE).
- Все Play Time места → activeTimeMs: `StatisticDao.getTotalTime()` (SUM(activeTimeMs)),
  AnalyticsUseCase.periodPlayMs, SessionTreeData.activeTimeMs (новое поле), session tree row duration,
  GetQuestSummaryUseCase.pureTimeMs, AFTER_GAME чирап totalPureMs.

### v11.86 — range of deviation полоска
- Градиент выпилен: два плоских rect'а в clipPath, стык на `centerFrac = |early|/(|early|+|late|)`.
- Клювик Serenity point — НЕПОДВИЖЕН в геометрическом центре (решение Alex: клювик = цель,
  игрок должен свести цветовой стык полоски к клювику). НЕ двигать клювик на centerFrac.

### v11.87 (файл!) — numbering + isQuestComplete + типографика + дни квеста
1. **GetLogAttemptsUseCase**: разделитель сессии теперь ЗАГОЛОВОК НАД попытками своей сессии,
   у КАЖДОЙ сессии включая верхнюю. Раньше был footer с номером верхнего блока — читался как
   заголовок нижнего → «дурит с нумерацией» (жалоба 6 июля, скриншот).
2. **Root cause isQuestComplete закрыт**: новый DAO `updateSessionTimes(id, endTime, activeTimeMs)`;
   `UpdateSessionUseCase` использует его вместо полного @Update — больше не затирает
   isQuestComplete=true от async markSessionQuestComplete.
3. **`tidyLineBreaks()`** (core/utils/CheerUpMessages.kt, top-level): NBSP после служебных слов
   (a/an/the/and/of/in/on/at/to/for/per/as/by/is/are/was/it/no), NBSP перед «—» и «·»,
   не клеит после конца предложения. Применяется В ТОЧКЕ РЕНДЕРА: CheerUpBubble,
   GreetingView (timeSpent + closingText), SessionSummaryOverlay headline.
4. **Дни квеста**: GetQuestSummaryUseCase.questDays = distinct календарные дни сессий квеста
   (было: охват от первой сессии до сегодня → «30 days» для брошенного квеста).
   GreetingView: 1 день → «You spent 4 min of pure play time on this quest», >1 → «…across N days».

### v11.88 (файл!) — интерактивная справка главного экрана (7 июля)
- Тап по "?" → режим подсказок (баннер сверху); тап по элементу → янтарный бабл
  (стиль чирапа) с описанием + жёлтая рамка вокруг элемента + носик-указатель.
  Бабл под элементом если тот в верхней половине экрана, иначе над. Повторный тап
  по "?" — выход. Оверлей перехватывает ВСЕ тапы (случайных попыток нет).
- Новый файл `HelpTapOverlay.kt`: enum HelpTopic (10 зон: BALANCE, HIT_RATE, HAND,
  COINS, QUEST_PANEL, STOPWATCH, LOG, DISTRIBUTION, GEOM_MEAN, PERIOD), helpText(),
  Modifier.helpZone(map, topic) через onGloballyPositioned/boundsInRoot, оверлей
  с хит-тестом (наименьшая содержащая зона побеждает).
- MainScreen: Screen-контент обёрнут в Box(fillMaxSize); старый AlertDialog
  "Quest & Level" УДАЛЁН (заменён тап-справкой). TopMetricsBar получил параметр
  `helpZone: (HelpTopic) -> Modifier` для 4 зон топ-бара. PeriodToggle — modifier.
- Тексты справки: технический + человеческий смысл одним абзацем, БЕЗ "For you:"
  (Alex убрал). Механика подтверждена Alex'ом по мокапу; тексты баблов НЕ
  согласовывались построчно ("баблы пока присылать не надо").
- Проверено на эмуляторе: вход/баннер, бабл под кнопкой секундомера, бабл над
  period pill, мелкая зона Balance, выход и работа RUN после выхода.

### v11.89–v11.90 — welcome-окно + About redesign (7 июля)
- **v11.89**: welcome-окно первого запуска (`ui_lib/WelcomeOverlay.kt`), 6 абзацев
  интро-текста Alex'а, кнопка Start playing, флаг `welcome_shown` в LocalStorage,
  `showWelcome`/`Event.DismissWelcome` в MainContract/ViewModel, ранний return в MainScreen.
- **v11.90**: welcome и About переведены на стиль баблов — жёлтая карточка #F5C02E,
  текст #2A1E00, 14sp/20sp lineHeight, скругление 16dp. Кнопка welcome — тёмная pill
  с янтарным текстом. About переписан: объединение старого About + интро, ужат до
  ~длины одного текста (6 секций: About / Self-regulation / Serenity / Luck Coefficient /
  Beyond the game / Learn more / Credits). `playoftime.online` кликабелен
  (LocalUriHandler → https). `toleranceFormatted()` в MainViewModel: NBSP после «±» —
  последний разрывный пробел внутри сегментов инфо-строки QuestProgress (остальные
  сегменты уже были на NBSP, переносы только на «·»).
- Проверено на эмуляторе: welcome в новом стиле, повторный запуск без welcome,
  About-карточка, тап по ссылке открывает Chrome.

### Пакет передачи проекта (7 июля)
- `C:\PlayOfTime\handover\` — код (без build/local.properties, сборка чистой копии
  проверена), APK v11.90, `OUTDATED_DOCS_AND_CURRENT_DECISIONS.md` (устаревшие пункты
  gdrive-доков + актуальные решения), `docs/DOCS_INDEX.md` (все Drive-доки со
  ссылками и статусом), `code/AGENTS.md` для Codex, README с первым промптом.

### v11.91 — L3.1 без лимита (7 июля, решение Alex)
- Первые квесты уровней БЕЗ лимита попыток: L3.1 → maxAttemptsDefault=null,
  adaptiveN=false (было окно 33 + N±33). У L3.1 больше нет провала окна/индульгенции.
- `sessionAutoCloseAttempts` 33 → 99: у всех no-limit квестов (L1.1, L1.4, L1.5,
  L2.1, L3.1) счётчик сессии идёт до 99 и начинается заново.
- Бабл за L2.5 «Next: hit 3.000, once» — корректен (ONE_ONLY = SESSION-условие).
- Handover-пакет синхронизирован (код + APK v11.91 + докстрока OUTDATED).

### v11.92 — разнообразные чирапы за квесты (7 июля, таблица Alex)
- `CheerUpMessages.specificText`: AFTER_QUEST варьируется по arrival-квесту
  (новые параметры arrivalLevelId/arrivalTaskId, 12 фраз: «Nice start!», «Keep it
  rolling», «Time to raise the bar», «millisecond league», «One step from the crown»…).
- AFTER_LEVEL: строка задания «Next quest: hit {phase}» на новой строке в `**…**` —
  CheerUpBubble рендерит **-сегменты с солидной янтарной подсветкой (маркер).
- AFTER_GAME: «Take a bow! Export and share your Excel… fresh news at
  www.playoftime.online» (убраны @champions и .lol).
- NBSP-склейки «hit {phase}» сохранены (sed по спискам «: hit», «to hit», «— hit»).
- Проверено на эмуляторе через LEFT-автостоп: AFTER_QUEST L1.1→L1.2 и AFTER_LEVEL
  с подсветкой + enrichment. Handover синхронизирован (APK v11.92).

### v11.93–v11.95 — иконка + минимальный GreetingView на L1 (9 июля)
- v11.94: новая иконка приложения из `Desktop/image3000_1.png` (канатоходец,
  красно-жёлто-зелёная пирамида) — PNG в 5 плотностей + round, PIL-скрипт.
- v11.95: GreetingView на уровне 1 — минимальный режим (`minimal = levelId < 2`):
  только анимация, «Level 1 · Quest N — Complete!», время, таблица, кнопка.
  Скрыты: Serenity Coins earned, congratulation text, closing-текст. Полный вид —
  с L2. Плюс фикс: closing-текст с пустым worsenedList («pay special attention
  to: .») → «The result is mixed…». Проверено на эмуляторе.

### v11.96–v11.98 — Lucky Index: финал (10 июля)
- ИТОГОВАЯ ФОРМУЛА: Lucky = эмпирическая (чистые тройки / все попытки) ÷
  теоретическая (1 / слоты диапазона разброса). **Слот = ширине окна «чистой
  тройки» = 100/10/1 мс по точности попыток** (карточка — большинство попыток
  периода; дерево — большинство в узле).
- Вариант A (слот всегда 1 мс, v11.96–97) ОТКЛОНЁН после разбора: числитель
  countTrueThree считает тройку в точности отображения попытки (L1 = окно
  ~100 мс), 1мс-знаменатель разрывал пару и завышал Lucky на L1 в ~100 раз.
- Попутный фикс: `trueThreeWinsOf` в дереве был мс-точным (|err|<0.5) для всех
  уровней → L1-строки показывали 0 троек/lucky. Теперь тройка в точности своей
  попытки (slot/2), как в карточке. Влияет и на колонку wins/att4win дерева.
- Урок: перед правкой формулы сверять ЧИСЛИТЕЛЬ и ЗНАМЕНАТЕЛЬ на согласованность
  единиц. Бэклог-пункт Lucky закрыт окончательно (v11.98).

### v11.99 — аудит кода (10 июля, по просьбе Alex «пройдись, ничего не сломай»)
Исправлено (проверено на эмуляторе):
1. GetQuestSummaryUseCase: при < 2 попыток обе «половины» = вся выборка —
   ушёл баг GreetingView «Hit rate 100% ↓ 0%» на выигранном с 1 попытки квесте.
2. AnalyticsUseCase sparklines: `takeLast(5)` на newest-first списке брал
   5 СТАРЕЙШИХ сессий → `take(5)`. Убран бессмысленный `sumOf { 0.0 }.let`.
3. GetLogAttemptsUseCase: O(n²) нумерация попыток (reversed().indexOfFirst в
   цикле) → арифметика `size − 1 − idx`.
4. MainMetricsUseCase: удалён мёртвый расчёт stdDev (Скотт отменён 27.06) и
   параметр buildDistrib.
5. Удалены: `formatTimeError` (Utils.kt, 0 ссылок), `StatRepository.kt`
   (пустой, не в DI) — закрыты пункты техдолга 5.1/5.3 старого плана.
Замечено, НЕ трогал (дизайн-вопросы): Balance red/blue по сырым ошибкам (а
trueThree по округлению — стрелка не центрируется при чистых 3.0 на L1);
N+1 запросов в AnalyticsUseCase.sessionsList на ALL_TIME; лог/isWin за
Today/Month рендерится в ТЕКУЩЕЙ accuracy (известный бэклог).

### v11.100–v11.102 — экспорт Excel ускорен (11 июля)
- Корень «долго открывается»: XSSFWorkbook на 20К+ строк строился НА ГЛАВНОМ
  потоке (в Dispatchers.IO была только запись файла) → UI фризился секунды.
- Фиксы (`ExcelShareUtil.kt`): вся генерация в withContext(Dispatchers.IO);
  тост «Preparing export…» сразу по тапу; runCatching больше не глотает ошибки
  (Log.e + тост «Export failed: …»).
- **ВАЖНО: SXSSFWorkbook на Android НЕ РАБОТАЕТ** — AutoSizeColumnTracker тянет
  java.awt.FontRenderContext → NoClassDefFoundError (проверено на эмуляторе,
  v11.100–101 с SXSSF откачены к XSSF). Не пытаться снова.
- Проверено: share-диалог открывается, xlsx создаётся в cache/excel.

## Незакрытое / отложенное
- Recalc SC v4 UX-hint для старых игроков.
- Sparkline logic per period (Priority 2 бэклога).
- TEMP DEBUG левая рука auto-stop — в MainViewModel, не удалять до конца тюнинга.
- In-memory logBuffer separator (addAttemptToList) остался footer-style — logBuffer не рендерится в UI,
  но при возврате к нему согласовать с header-конвенцией.
- Today-фильтр форматирует исторические попытки ТЕКУЩЕЙ accuracy (после level-up L1-попытки
  показываются в сотых 2.98 вместо 3.0 и теряют зелёный цвет) — Alex пока не жаловался.
- Подозрение на «L1.5 выигран с 1 попытки» (бабл Level 1 done при session #5 = [3.05]) —
  не воспроизведено; следить после фикса isQuestComplete.

## v12.02 (11 июля 2026) — свайп-навигация, main всегда TODAY
- **Круговой свайп между экранами** (`MainActivity.kt`): ModalNavigationDrawer УДАЛЁН (его edge-swipe был «остатком гамбургера»). Вместо него Box вокруг NavHost с `detectHorizontalDragGestures`: порог 110dp, влево = следующий, вправо = предыдущий, по кругу Play→Analytics→Settings→About→Play. Индекс текущего экрана через `currentBackStackEntryAsState` + `hasRoute<>()`, захвачен `rememberUpdatedState` (pointerInput(Unit) не перезапускается). drawerState остался только как параметр Screen (не используется).
- **PeriodToggle убран с главного** (`MainScreen.kt`): всегда TODAY. `MainViewModel.init` → `loadMetrics(PeriodFilter.TODAY)` (было getPeriodFilter()). Лог попыток (weight 1f) получает освободившееся место; DistribChart не растёт, просто ниже. Файл PeriodToggle.kt остался (используется только на StatScreen? — нет, у стата свой PeriodPill; PeriodToggle теперь мёртвый, не удалял).
- **"?"**: FontWeight.Bold 14sp alpha 0.95, border 1.5dp alpha 0.75, `align(CenterVertically)` в Row → центр по высоте области квеста.
- Проверено на эмуляторе: свайпы в обе стороны с замыканием круга, RUN/auto-stop работает, HelpTopic.PERIOD зона больше не регистрируется (в справке не появится).
- Нюанс: очень быстрый свайп (180ms fling) иногда не срабатывает на About — при обычной скорости пальца стабильно.
- APK: play-of-time-v12.02.apk, handover синхронизирован.

## v12.03 (12 июля 2026) — свайп v2, время сессий, справка на аналитике
- **Свайп**: порог 110→48dp, срабатывает СРАЗУ при пересечении порога (в onHorizontalDrag, не onDragEnd, флаг fired). NavHost получил направленные slide-переходы (slideIn/OutHorizontally, tween 280ms); направление через destIndex()+slideDirection() (кратчайший путь по кругу, файл-private функции в MainActivity.kt).
- **Время вместо даты**: заголовки сессий в логе главного (GetLogAttemptsUseCase + MainViewModel logBuffer separator) и в дереве сессий аналитики (TreeSessionRow) — HH:mm (24ч).
- **Справка на аналитике**: HelpTopic расширен 7 STAT_* топиками (STAT_PERIOD/SUMMARY/CHANCE/GEOM/RANGE/PACE/SESSIONS) + helpText'ы. Новый `Modifier.helpZoneDisposable` (composable, DisposableEffect снимает зону при dispose — обязательно для LazyColumn, иначе устаревшие Rect'ы уехавших карточек ловят тапы). "?" кнопка в правом краю PeriodPill (параметры helpActive/onHelpClick/onQuestionRect). HelpTapOverlay поверх контента StatScreen.
- **Дерево сессий**: lucky и att4win центрированы по столбцам (TextAlign.Center, было End) в TreeRow и TreeSessionRow; formatGeomMs без знаков +/− (только цвет red/blue).
- Проверено на эмуляторе (пришлось перезапускать, cold boot + ANR "System UI isn't responding" → Wait). APK play-of-time-v12.03.apk, handover синхронизирован.

## v12.04 (12 июля 2026) — GreetingView: quest vs накопленное среднее, золотой блок уровня
- **GetQuestSummaryUseCase**: половинки квеста выброшены; теперь questEarly/LateMs vs overallEarly/LateMs — ГЕОМЕТРИЧЕСКИЕ средние (квест vs ВСЕ попытки БД, включая квест). Hit rate — доля (не среднее), quest vs overall.
- **QuestCompletionStats**: новые поля quest*/overall*, isLevelComplete/isGameComplete/nextPrecisionLabel/levelAttempts/levelTrueThrees; improved = квест лучше накопленного среднего (only if обе стороны > 0).
- **GreetingView**: таблица с подписью "this quest vs your overall average", формат "21 ms vs 31 ms" (значение квеста жирное зелёное/красное), formatMsOrDash. Золотой блок при isLevelComplete: "★ LEVEL N COMPLETE / Precision moves to hundredths of a second: 3.00 / 5/5 quests · N attempts · N clean threes" — показывается и на L1 (важнее minimal-режима). При isGameComplete вторая строка = "All 15 quests done — game finished!".
- **MainViewModel**: checkVictory заполняет level-поля (запрос сессий уровня в enrichment launch); GreetingView.Close — УДАЛЁН AFTER_LEVEL bubble enrichment и async-хвост "You beat your average" (дедупликация: числа в GreetingView, бабл = только «что дальше»).
- **CheerUpMessages AFTER_LEVEL**: "Level N done. Precision now moves to X of a second. **Next quest: hit …**" (числа убраны).
- ВАЖНО (эмулятор): НЕ запускать с `-gpu swiftshader_indirect` — вечные ANR "isn't responding"; с дефолтным GPU всё летает. Прогнан полный L1 (5 квестов) на чистой БД — новая таблица, золотой блок, укороченный бабл проверены скриншотами.
- Bash-heredoc в этой среде СЪЕДАЕТ backslash-escapes (\n → \n) — для правок с \n в Kotlin-строках использовать chr(92) в python.
- APK play-of-time-v12.04.apk, handover синхронизирован.
- v12.05: заголовок сессии в логе главного = 'dd/MM/yyyy · Session #N · квест · HH:mm' (дата на первом месте, оба источника)
- v12.06: дата только у ВЕРХНЕГО заголовка сессии, слева (sessionDateLabel в SessionStatisticItemState; SessionHeaderRow(dateLabel); GetLogAttempts: result.isEmpty() = topmost; MainViewModel separator всегда с датой — в SESSION он верхний)

## v12.07 (12 июля) — блок Interpretation в GreetingView (с L2.2)
- GetQuestSummaryUseCase.invoke(+finalSessionId): 5 метрик «победная сессия vs вся история»: Precision (геом. среднее |ошибок|), Luck (формула luckyIndexOf с аналитики), Focus (% смен знака red↔blue, нулевые ошибки вне знаменателя, история — по-сессионно), Consistency (% соседних пар со строгим уменьшением |ошибки|), Pace (пауза перед сессией vs геом. среднее межсессионных пауз, ceil до минут). Сентинелы -1 = строка скрыта.
- QuestCompletionStats: sess*/hist* поля + interpretationUnlocked (L>=3 || L2 task>=2).
- GreetingView: второй числовой блок "this session vs your history" (CompareRow, значение сессии цветное по вердикту) + блок "Interpretation" — по строке на параметр: **Имя** — фраза (better/worse/onPar, порог on-par ±10% от истории). Хелперы interpRow/verdictOf/InterpRowData внизу файла.
- Проверено на эмуляторе (L2.2, тест-режим все 3.000): показываются только Luck и Pace (Precision/Focus/Consistency корректно скрыты — нет ненулевых ошибок), вердикты on-par. Полный вид — только на живой игре с промахами.
- Правило: Precision-интерпретация всегда на сырых мс (1/1000), без округления до точности уровня — закреплено комментарием в GetQuestSummaryUseCase.errMs()

## v12.08 (12 июля) — переносы текста квеста и баблов
- РЕТРОСПЕКТИВА бага «in row × 0 | /2» (скрин Alex с устройства): строка была ПЕРЕКЛЕЕНА NBSP-ами — сплошной неразрывный ран длиннее строки Compose рвёт в СЛУЧАЙНОМ месте (у слэша). Правильная модель: явные точки переноса между смысловыми группами + защита слэшей.
- QuestProgress info-line: обычный пробел (=точка переноса) ПЕРЕД " in row", " ·0/3", " ·left", " ·no limit" (после точки — NBSP); слэши в "0/2" и "32/33" обёрнуты WORD JOINER U+2060 (запрет разрыва у слэша).
- describePhase (баблы): расклеен — слова goal-фразы обычными пробелами, клеятся только атомы "aim ± tol" и "N attempts"; запятая + обычный пробел.
- Проверка: эмулятор 720px + font_scale 1.2 — переносы по правильным точкам, слэш-атомы целы; на нормальной ширине строка в одну линию. (Экстремум 720px+1.7 вырожден — колонке остаётся ~5 символов, форс-брейк неизбежен при любой схеме.)
- ЭМУЛЯТОР: wm size override на живом эмуляторе замораживает экран (screencap отдаёт застывший кадр, adb reboot не лечит) — только cold boot (-no-snapshot). Для проверки переносов использовать font_scale (1.2), НЕ wm size.

## v12.09 (12 июля) — GreetingView редизайн по Alex
- «Your coins: 9.92 Serenity.» вместо «You earned…» (коины = расписка мира, не заработок).
- Карточка «this quest vs your overall average» УДАЛЕНА (getters earlyImproved/allWorsened и questEarly/overall поля в QuestCompletionStats остались, но UI их не использует; старый closing text удалён).
- Interpretation слит с выводом в единый живой параграф (стиль старого conclusion, по центру): предложение на параметр, начинается со слова-параметра (жирное), в конце «All in all — …» (4 варианта по балансу better/worse; worse-параметры перечисляются lowercase).
- Слова-параметры ТАПАБЕЛЬНЫ (ClickableText + pushStringAnnotation "param"): тап подсвечивает янтарным и слово, и свою строку таблицы (CompareRow highlighted=, bg 0x33F5C02E); повторный тап/тап мимо снимает. ClickableText сам съедает тап — GreetingView не закрывается.
- Проверено на эмуляторе (L2.3 с реальным промахом 2.60): таблица Precision/Luck/Consistency/Pace (Focus скрыт — нет red/blue пар), параграф, подсветка по тапу.

## v12.10 (12 июля) — карточка = похвала, бабл = требование
- CheerUpMessages.AFTER_QUEST/AFTER_LEVEL: обрезаны до «**Next: hit …**» / «**Next quest: hit …**» — только требование, полностью в жирной подсветке. 12 согласованных вводных фраз переехали на карточку (CheerUpMessages.praiseFor).
- QuestCompletionStats: +arrivalLevelId/arrivalTaskId (0 = игра закончена). MainViewModel заполняет их при HOORAY-фиксе.
- GreetingView: над кнопкой TAKE NEXT QUEST — жирная строка praiseFor("Nicely done! Time to raise the bar." и т.п.), скрыта при isGameComplete. Итог: обычный квест = карточка (итоги+похвала) + бабл на главном (цель); один канал вместо двух.

## v12.11 (12 июля) — свайп через HorizontalPager (палец тянет контент)
- Заменён NavHost со slide-переходами на HorizontalPager (androidx.compose.foundation.pager) с 4 страницами. Стандартный Material-контрол: контент СЛЕДУЕТ за пальцем при drag, при отпускании — fling с snap к ближайшей странице. Короткий свайп ниже threshold → возврат назад; быстрый fling → переход.
- BottomNavBar API: currentPage:Int + onSelectPage:(Int)->Unit (было NavController + NavRoutes). MainActivity: goTo = { scope.launch { pagerState.animateScrollToPage(idx) } }.
- MainScreen/StatScreen/SettingScreen/AboutScreen: сигнатура принимает currentPage/onSelectPage вместо navController. CTA «See your progress» в MainScreen: onSelectPage(1). onEarnMore в StatScreen: goTo(0).
- Круговой порядок больше НЕ поддерживается (HorizontalPager линеен). Alex просил «стандартный без затей» — согласовано.
- Проверено: короткий свайп → snap back; быстрый fling → переход; тап по табу → animateScrollToPage через промежуточные страницы. APK play-of-time-v12.11.apk.
