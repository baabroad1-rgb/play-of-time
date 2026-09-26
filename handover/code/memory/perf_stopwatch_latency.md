---
name: perf-stopwatch-latency
description: "Скрытые источники латентности stopwatch-кнопки (v12.22→v12.24, июль 2026) — kernel timestamps, изоляция State, drift-корректный тик"
metadata: 
  node_type: memory
  type: project
  originSessionId: 6670dc80-da6e-49ba-bf09-b8ccfb1261a5
---

# Стопватч-латентность: где прятался gap ~30 мс

Alex 2026-07-14: несколько итераций фиксов, потому что «стало лучше но всё равно чуть-чуть тормозит». Каждый слой убирал ~10 мс, пока не стало «норм».

## Слои латентности между тапом и записью endTime

**Порядок важен: чем ниже в списке, тем позже добрались, тем тоньше проявление.**

1. **Явный `delay(20)` перед `setState` в STOP** ([MainViewModel.kt](app/src/main/java/ru/iflex/timemvp/features/main/ui/MainViewModel.kt)). Был вставлен когда-то как обход race-condition: последний pending-тик StopWatch перезаписывал заморозку. Убрать без сопровождающей защиты нельзя.

2. **`MutableSharedFlow()` без буфера** ([BaseViewModel.kt](app/src/main/java/ru/iflex/timemvp/core/BaseViewModel.kt)). Каждый `setEvent` шёл `viewModelScope.launch { _event.emit(event) }` — rendezvous emit ждёт collect. Плюс лишний dispatcher round-trip. Фикс: `MutableSharedFlow(extraBufferCapacity = 32)` + `tryEmit(event)` синхронно.

3. **Замер `System.currentTimeMillis()` ПОСЛЕ dispatch'а**. Даже перенеся захват в composable (`interactionSource.collect`), read всё ещё в Main-корутине, которая на загруженном Main (тик × 16 мс + метроном + вибрация) просыпается с задержкой 10–20 мс. Причём **асимметрично**: на START Main простаивает, на STOP занят → duration завышена.

4. **Kernel timestamp через `PointerInputChange.uptimeMillis`**. Драйвер сенсора ставит эту метку при регистрации касания, ДО того как Compose нас разбудит. Ловим через `Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial) } }`. Не консюмим — clickable ниже нормально показывает ripple. Из uptime → wall-clock через delta `System.currentTimeMillis() - (SystemClock.uptimeMillis() - down.uptimeMillis)`.

5. **Длительность в uptime, не wall-clock**. `SystemClock.uptimeMillis()` монотонный, не подвержен NTP-скачкам. `attemptDuration = touchUptime_STOP - touchUptime_START`. Wall-clock храним отдельно для БД: `endTime = startTime + attemptDuration`.

6. **Прямой вызов метода вместо event-очереди**. Даже с буферизованным SharedFlow между `tryEmit` и `collect { handleEvents }` — ещё один Main-dispatch. Для горячего пути добавлен `fun onStopwatchPress(w, u)` в MainViewModel, MainScreen вызывает его синхронно из pointerInput. handleEvents(...) остался для случаев где event через flow нужен (TEMP DEBUG auto-stop).

7. **`state.time` в общем `MainContract.State` заставлял пересобирать ВЕСЬ MainScreen 60 раз в секунду**. Причина: `val state = viewModel.viewState.value` на верхнем уровне composable — читатель mutableStateOf. structuralEqualityPolicy инвалидирует всех читателей viewState при смене ЛЮБОГО поля. Даже полей, которые данный composable не читает — потому что читал viewState как объект. Тик менял state.time → полная пересборка 720-строчной composable с 78 `state.*` reads. Фикс: удалить `time` из `MainContract.State`, добавить отдельный `MainViewModel._stopwatchTime = mutableStateOf<String>()`, экспонировать как `ComposeState<String>`, **передавать State<T> объектом** вниз (не `.value`!), читать `.value` только внутри leaf-композиции (StopWatchButton). Тогда инвалидируется только он.

8. **`@Volatile` на `stopWatchState` в StopWatch.kt**. Тик-луп читает поле на Dispatchers.Default, Main пишет `ON_STOP`. Без @Volatile на многоядерном CPU запись может быть не видна тику несколько мс — последний тик успевал уйти в setTime с уже неактуальным временем.
   **ДОПОЛНЕНО (v12.33, 15 июля)**: @Volatile НЕ закрыл гонку полностью — остался TOCTOU между guard-проверкой и setTime. Симптом: на кнопке 3.059, в логе/БД 3.052 (~1 раз на 10 попыток) — опоздавший тик перетирал заморозку. Фикс: общий `tickLock` (synchronized) на «guard + замер + setTime» в тике И на `setState()`. `setState(ON_STOP)` не возвращается, пока тик посреди записи; заморозка пишется ПОСЛЕ setState → всегда финальна. Урок: volatile = видимость, но НЕ атомарность пары «проверил → записал»; для check-then-act нужен монитор.

9. **Drift-корректное расписание тика**. Раньше `while { delay(16); ... }` — если один delay проспал 40 мс, следующие ~ на 16 мс отстают навсегда (дрейф накапливается). Теперь абсолютное расписание: `nextDeadline = startUptime + N * 16 мс; delay(nextDeadline - now)`. Опоздавший тик догоняет расписание, средний шаг = ровно 16 мс независимо от нагрузки на CPU.

## Общие уроки

- **Compose read tracking всегда на уровне мутатора `mutableStateOf`, не на уровне поля**. Читаешь `viewState.value.foo` — Compose видит чтение `viewState.value`, любая смена инвалидирует. Для scoped recomposition — отдельные `mutableStateOf` для hot-path данных.
- **Передавай `State<T>` объектом** от родителя к ребёнку, читай `.value` в ребёнке. Иначе scoping не работает.
- **Для точных input timestamps на Android** — `PointerInputChange.uptimeMillis` через `pointerInput` с `PointerEventPass.Initial`. Это kernel-level, недостижимо через clickable / interactionSource.
- **Монотонное время** — `SystemClock.uptimeMillis()`. Wall-clock (`System.currentTimeMillis()`) дрейфует при NTP-коррекциях.
- **Абсолютные deadline'ы** для периодических задач вместо relative delays — избегает дрейфа под нагрузкой.
- **`@Volatile`** на любом var, читаемом одним потоком и меняемом другим (без синхронизации).

## Не трогать без осторожности

- Хранение `state.startTime`/`state.endTime` (wall-clock) в БД — от них зависит DAO-запрос "clean threes = endTime - startTime == aimTime". Если менять единицы — сломается вся аналитика.
- TEMP DEBUG LEFT-hand auto-stop передаёт синтетический uptime (`SystemClock.uptimeMillis()`) — но autoStopFired перекрывает duration на `currentAimTime`, так что реальные значения не важны.
- `state.time` полностью удалён из MainContract.State. Не возвращать — заменить на `viewModel.stopwatchTime: State<String>` если ещё где-то понадобится дисплей секундомера.
