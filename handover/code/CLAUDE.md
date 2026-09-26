# Play of Time — Project Briefing for Claude Code

## What is this app?
Play of Time is a native Android stopwatch-based training app rooted in Gellerstein's psychotechnics methodology. The user taps RUN, waits ~3 seconds, taps STOP, and tries to hit exactly 3.000 seconds. The app tracks accuracy, calculates a "Serenity Coin Metric" (SCM), and progresses the user through quests.

## Tech Stack
- Native Android, Kotlin
- Jetpack Compose (UI)
- Room (database)
- Koin (dependency injection)
- MVI architecture (BaseViewModel, Contract pattern)
- Project path: `C:\PlayOfTime\time_mvp-quest_dev`
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

## What Was Done (18-19 June 2026)

### Files Modified (8 files):
1. `AnalyticsData.kt` — replaced rangeMaxMs with rangeEarlyMs + rangeLateMs (P3/P97)
2. `AnalyticsUseCase.kt` — P3/P97 range on signed errors, absolute deviations for "All" profile, level-based modes filtering and rounding, 1ms precision for top deviations
3. `StatScreen.kt` — reordered cards (Pace → Range → Modes → Deviation rows → Chance of Win), visual range bar (red/blue), compact summary header, EARN MORE SERENITY button with navigation, title "Analytics", trend arrows, thousands separator, Spectrum card merged into compact deviation rows
4. `MainActivity.kt` — wired onEarnMore navigation callback to StatScreen
5. `MainContract.kt` — removed blind mode (deflection state fields, DeflectionAction events). KEPT Hand toggle and SelectHand.
6. `MainScreen.kt` — removed DeflectionInput UI. KEPT HandToggle.
7. `MainViewModel.kt` — removed DeflectionAction handlers. KEPT SelectHand handler.
8. `DrawerContent.kt` — changed menu label from "Statistics of the Progress" to "Analytics"

### Files Deleted:
- `DeflectionInput.kt` — blind mode UI (no longer needed)

### Formula Fixes Completed:
- Range of Errors: both P3 (early) and P97 (late) bounds on signed errors
- "All" deviation profile: uses absolute errors per parametr_table param #14
- Top Deviations: 1ms precision (was 10ms buckets)
- Top Deviations: filtered by current quest level, rounding matches level accuracy
- Play time format: "17.43 h" instead of h:mm:ss
- Pace of Learning: verified correct (periodScm/periodTotal)

## What Was Done (20 June 2026)

### Analytics Screen — Major Redesign to Match Prototype (`StatScreen.kt`, `AnalyticsUseCase.kt`)

#### StatScreen.kt — full card layout overhaul:
- **StatCard** redesigned: no divider, no `?` button per card; now shows `TITLE / period` (e.g. `RANGE OF DEVIATIONS / session`) with period label right-aligned
- **SummaryHeader**: removed CoinStack Canvas (was rendering as blob); now shows plain number for SERENITY COINS
- **Removed composables**: `CoinStack`, `ActivationSpectrumCard`, `SpectrumRow`, `HelpModal` — all deleted
- **Added** `BigMetric` composable: large 22sp bold value + optional secondary inline + description text below (muted 11sp)
- **New card order** (top to bottom):
  1. SummaryHeader (Total / always ALL_TIME)
  2. Period pill (Session / Day / Month / All time)
  3. CHANCE OF WIN
  4. RANGE OF DEVIATIONS
  5. MODES OF DEVIATION
  6. GEOMETRIC MEAN + VARIABILITY
  7. PACE OF LEARNING
  8. PACE OF PLAY
  9. Sessions tree + EARN MORE SERENITY button
- **CHANCE OF WIN card**: three aligned `CardMetricRow` rows — Hit rate (green), Attempts per win, Lucky Index (green if <1, red if >1); no sparkline
- **RANGE OF DEVIATIONS**: renamed from "RANGE OF ERRORS"; same visual red/blue bar
- **MODES OF DEVIATION**: title now shows `/ session` etc. via `toSlashLabel()`; uses all period attempts (not level-filtered)
- **GEOMETRIC MEAN + VARIABILITY**: renamed from "MEDIAN DEVIATION + VARIABILITY"; Early row shows negative sign (`negated = true`); description updated
- **PACE OF LEARNING / PACE OF PLAY**: split from single PACE card; moved after the deviation cards
- **`DeviationRow`**: added `negated: Boolean = false` parameter — when true, displays value with flipped sign (for Early deviations which are stored as absolute values)
- **Removed**: `HelpModal`, all per-card `onHelp` lambdas, `helpText` state, `mutableStateOf`/`getValue`/`setValue` imports, `TextAlign` import

#### AnalyticsUseCase.kt — calculation fixes:
- **Modes of Deviation bug fixed**: was filtering `periodAttempts.filter { it.accuracy == currentLevelId }` which compared accuracy precision (1/2/3) against quest level ID (1–10) — always wrong for quest levels > 3. Now uses `earlyErrorsMs` / `lateErrorsMs` directly (all period attempts)
- **`computeProfile` → geometric mean**: replaced arithmetic median with geometric mean (`exp(mean(ln(values)))`); spread is now geometric CV% (`(exp(std(ln)) - 1) × 100`). Imports added: `kotlin.math.exp`, `kotlin.math.ln`

#### AnalyticsData.kt — session numbering (done in previous session):
- `SessionTreeData` has `sessionNumber: Int` — global sequential number (oldest = #1) built from all sessions sorted by startTime
- `AnalyticsData` has `luckyIndex: Double` and `toleranceMs: Double`

#### Session numbering (StatsViewModel.kt):
- When period = SESSION, auto-expands the most recent session on load

## What Was Done (21 June 2026)

### SCM Formula — Units Corrected to Milliseconds
- **`CalculateScmUseCase.kt`**: formula now uses raw milliseconds (not seconds). Typical SC per attempt: ~0.05–2.0. Perfect attempt (0 ms error) still gives SC = 99.
- **`RecalculateScmUseCase.kt`**: comment updated; this is now v3 recalculation (correcting v2's seconds mistake).
- **`LocalStorageService.kt`**: migration flag key changed from `scm_recalculated_v2` → `scm_recalculated_v3` so all existing users trigger a fresh DB recalculation on next launch.
- **`TopMetricsBar.kt`**: `formatCoinsTotal` now shows decimals for small values (e.g. "0.85", "5.2", "234", "1 234"); today's delta now shows 2 decimal places instead of `.toInt()`.

### Log Persistence & Session Separator (also 21 June)
- `logBuffer` never resets during app lifetime — only on process exit.
- When a new DB session starts, a divider row is inserted: `─── Session #N · dd/MM/yyyy ───`.
- SC (Serenity Coins) column added to the attempt log, hidden on the last (most recent) row; first row shows "SC" as column header.
- Off-by-one fixed: SC is retroactively applied to attempt N−1 when attempt N arrives (matches how `SaveScmAfterAttemptUseCase` computes).

## What Was Done (21 June 2026 — session 2)

### Quest System Fixes (`Quest.kt`, `MainViewModel.kt`)

#### Verified against `quests_oder.pdf` — 3 bugs found and fixed:

1. **Missing "return to baseline" exit condition** — quests with adaptive N or T were advancing immediately on any win. Design requires winning while N/T is at its initial value (cycling mechanic). Fixed in `checkVictory()`: `onQuestWindowSuccess()` now returns `Boolean`; advancement only happens when both N ≤ `maxAttemptsDefault` AND T ≤ `toleranceMsDefault`.

2. **Quest 3.4 wrong initial N** — was `maxAttemptsDefault = 33`, should be `3` (design: "N:=3").

3. **Quest 3.4 wrong N step** — was ±33, should be ±1 (design: "N:=N+1 / N:=N−1"). Added `adaptiveNStep: Int = 33` field to `Quest` class (default 33 for all quests except 3.4 which uses 1). `onQuestWindowFailure/Success` now use `quest.adaptiveNStep`.

4. **Quest 3.2 missing adaptive T** — `adaptiveT` was `false`, but design says tolerance ±t should appear when user can't hit 3.000 in N attempts. Changed to `adaptiveT = true`.

5. **Congratulations added** to quests 3.1, 3.2, 3.3 (previously null).

### HURRAY! Button Mechanic (`MainContract.kt`, `MainViewModel.kt`, `StopWatchButton.kt`, `MainScreen.kt`)

- After a quest win the result stays visible (green button + time, no immediate transition).
- Button label changes to **"HURRAY!"**.
- User taps HURRAY → GreetingView shows (stats: Level/Quest, Attempts, Wins, Hit Rate).
- User taps anywhere on GreetingView → dismissed → new quest title appears.
- New `awaitingHurray: Boolean` state field controls this.
- `currentQuest`, `taskTitle`, `accuracy` update only after GreetingView is dismissed.

## What Was Done (23–25 June 2026)

### Quest System — Final Indulgence Model (Alex's rules)
- All 13 quests defined in `Quest.kt`: L1.1–L1.3 (tenths), L2.1–L2.5 (hundredths), L3.1–L3.5 (thousandths). PDF-level mapping comments at top of file.
- **Indulgence rule (from L2.2 onwards): only one adaptation at a time, never both.**
  - IN_N quests (TWO_IN_N, THREE_IN_N, ONE_ONLY-as-1-in-N at L3.1) → `N±33` only, `t = 0`.
  - ROW quests (TWO_IN_ROW, THREE_IN_ROW) → `t±1 ms` only, `N = 33` window.
- **Caps (3 levels max)**: `N ∈ {33, 66, 99}`; `t ∈ {0, 1, 2, 3 ms}`. Enforced in `onQuestWindowFailure`.
- **Worst-win contraction** in `onQuestWindowSuccess` (`MainViewModel.kt`):
  - ROW: `new_t = max(0, worstWinErrorMs − 1)`.
  - IN_N: snap last-winning-attempt index up to nearest valid level, then subtract one step.
  - Advancement only when contraction lands at baseline (`N = 33` AND `t = 0`).
- `getEffectiveN()` clamps stored N to `[baseline, baseline + 2·step]` and snaps to multiples of step (auto-migrates legacy values like 3, 4, etc.).
- New tracking fields in `LocalStorageService`: `questWindowMaxErrorMs`, `questWindowLastWinIndex`.
- L3.3 and L3.4 swapped (`THREE_IN_N` now at 3.3, `TWO_IN_ROW` at 3.4). L3.5 added (`THREE_IN_ROW`, final quest of the game). L2.4/L2.5 added (`TWO_IN_ROW`/`THREE_IN_ROW` at hundredths).

### Main Screen
- **HOORAY!** button (renamed from HURRAY!). Icon hidden in HOORAY! / OK states. "OK" text completely removed (button shows RESET instead under fail/session overlays).
- **Audio**: `res/raw/hooray.m4a` plays the moment the green HOORAY! button appears (`MainViewModel.playHoorayIfAvailable`).
- **Stopwatch button**: dark charcoal `#2D2D2D` background, white text; STOP icon/text turn green from 2.0 s onward. No more yellow phase.
- **Session numbering**: always starts at #1 regardless of how previous session ended (quest completed mid-session, manual reset, 33-cap). Continuous 1…N (no cycle).
- **Session persistence**: `MainViewModel.init` reloads attempts of the latest unfinished session from DB so navigation away and back keeps you at the right attempt number.
- **Session length** auto-closes at the quest's **maximum possible N** (`baseline + 2·step = 99` for adaptive quests; 33 default otherwise), not at the current effective N. So an indulged session runs the full 33 → 66 → 99 ride before ending.
- **Distribution chart**: 71 buckets (was 41); pillars fill the entire bucket slot (no inter-bar gap → continuous skyline). Centre bin lands exactly on 0 ms.
- **Dynamic x-axis scale**: histogram + per-row error bars + GeomMeanRow all share a `dynamicScaleMaxMs` derived from currently-visible attempts (recomputes on scroll).
- **Twin shot detection**: two consecutive non-win attempts with same **signed** error (both early or both late, equal magnitude) → common green outline spanning `#` + time columns only (skips error-bar + SC columns), 4 dp symmetric padding, shifted 3 dp up-left for visual balance.
- **Error Pattern chip** (orange/yellow strip below stopwatch button) now hosts transient signals too:
  1. `wow, twins!` (green, 1 sec) — twin-shot fired
  2. `it was close!` (yellow, 1 sec) — Level-3 attempt exactly 2.999 s or 3.001 s
  3. Sustained patterns: `Systematically early`, `Systematically late`, `Errors growing — drift`, `approaching to the balance` (renamed from "Jagged rhythm")
- **Σtries / ΣSC totals row**: pinned over the histogram chart, vertically centred (~14 dp from top), labels centred above values. Σtries = visible attempts count, ΣSC = sum of SC for those attempts.
- **Range bar (analytics)**: green beak + green divider centred at zero, labelled "serenity point" (was "point of balance").

## What Was Done (26 June 2026 — cheer-ups & Error-Pattern chip refactor)

### Cheer-ups (bubble) — post-event only
The 6 in-session pattern-detection cheer-ups (`NINE_RED`, `NINE_BLUE`, `GROWING_ERROR`, `TRIPLE_IDENTICAL`, `PAUSE_SHORT`, `PAUSE_LONG`) were removed from the cheer-up bubble pipeline and moved into the Error-Pattern chip (see next section).

`CheerUpTrigger.kt` now lists only post-event values: `AFTER_SESSION`, `AFTER_QUEST`, `AFTER_FAIL`, `AFTER_LEVEL`, `AFTER_GAME`, plus `NONE`.

`CheerUpMessages.kt` correspondingly trimmed; `hasGeneralPart()` always returns false (the rotating coaching tips are no longer part of the post-event bubble).

`DetectCheerUpTriggerUseCase.kt` deleted. Its detection logic is merged into `DetectErrorPatternsUseCase`.

**6 post-event bubble copies** — content optimised for "numbers first, prose minimal":

1. `AFTER_SESSION` — session-done card: attempt count, better/worse vs previous, median blue/red (with deltas), spread %, SC pace, best trio, above-avg streak, optional "🏆 new record" line.
2. `AFTER_QUEST` — quest-done card: time (d + min pure), SC earned, early/late medians (before → after), clean-three probability, one of three closing tags (improved / mixed / persistence).
3. `AFTER_FAIL` — *"Quest paused · simplifying — spent x d / y min pure time. You ran out of Serenity to beat this quest. No worries — let's ease the conditions, build up your Serenity, and come back to the unbeaten quest with new strength."*
4. **NEW** trigger `AFTER_SIMPLIFIED_DONE` — fires when the indulgence cycle contracts back to baseline after a win at relaxed conditions: *"✓ Simplified quest beaten · back to the real thing. You cracked the easier version. Now it's time to face the original quest — no concessions this time."*
5. `AFTER_LEVEL` — quest-done block + level-up paragraph ("Level 2/3 of 3 unlocked · Stopwatch precision: hundredths/thousandths"). The "send Excel" hint removed.
6. `AFTER_GAME` — short closer + "share Excel → playoftime@gmail.com (anonymised) · chat @champions · news www.playoftime.lol".

Note: `AFTER_SESSION` is still defined in the enum but is currently not fired from `MainViewModel` — the session-end UX uses the yellow `SessionSummaryOverlay` instead. Wire it later if needed.

### Error-Pattern chip (orange/yellow strip under stopwatch)
`ErrorPattern.kt` enum expanded from 4 → 7 values:

| Pattern | Chip text | Detection threshold |
|---|---|---|
| `SYSTEMATIC_EARLY` | `← too tense — try to relax` | 9 attempts in a row, all early |
| `SYSTEMATIC_LATE` | `→ too relaxed — try to focus` | 9 attempts in a row, all late |
| `INCREASING_DRIFT` | `↗ errors growing — breathe and refocus` | 6+ consecutive, `\|error\|` strictly grows |
| `TRIPLE_IDENTICAL` | `✦ three identical — now improve accuracy` | 3 identical errors + 4th different |
| `PAUSE_SHORT` | `← too tense — lengthen the pause` | last 3 pauses < 300 ms AND all 3 early |
| `PAUSE_LONG` | `→ too relaxed — shorten the pause` | last 3 pauses > 300 ms AND all 3 late |
| `JAGGED_RHYTHM` | `↯ approaching to the balance` | last 4 alternating sign |

Plus the two transient signals (1-sec banners overlaid in the chip area, higher priority):

| Transient | Chip text | Trigger |
|---|---|---|
| Twin shot | `✦ wow, twins!` | Two consecutive non-win attempts with same `\|error\|` |
| It was close | `✦ it was close!` | Level-3 attempt at exactly 2.999 s or 3.001 s |

`DetectErrorPatternsUseCase.kt` now accepts `errors: List<Double>` + `pausesMs: List<Long>` and emits one `ErrorPattern` per call. MainViewModel computes `pausesMs` from attempt timestamps and passes both to the detector.

### Build / Versioning
- **Auto-versioning**: `app/version.properties` (versionCode + versionBase + versionPatch) auto-bumps after every successful `assembleDebug` / `assembleRelease`. Only the patch digit increments; edit `versionBase` manually for major bumps.
- **APK name**: `play-of-time-v<versionName>.apk` (the `-debug` suffix removed).
- **Room upgraded 2.6.1 → 2.7.2** in `gradle/libs.versions.toml` — fixes a KSP2 / Room "unexpected jvm signature V" crash.

### Analytics Screen
- Quest tree shows `early / late` and `att / wins` columns; all column slashes vertically aligned via the `SlashCell` micro-grid composable.

## What Was Done (27 June 2026 — long session, v0.10.69 → v11.11)

### Versioning
- `app/version.properties`: bumped `versionBase` from `0.10` to `11`. After 0.10.99 / 0.10.100 the auto-bumper now produces 11.01, 11.02, …
- `build.gradle.kts`: patch is now formatted with `%02d` so single-digit patches show as `01..09`, double-digit as `10, 11, …`.

### Quest system

**1. New L1 structure (5 quests, mirrors L2/L3).** Previously L1 had only 3 quests (ONE_ONLY, TWO_IN_ROW, THREE_IN_ROW) with no indulgence. User asked for parity with L2/L3. `Quest.kt`:
- L1.1 ONE_ONLY (unchanged)
- **L1.2 TWO_IN_N** (new — N=33 baseline, ±33 indulgence)
- **L1.3 THREE_IN_N** (new — N=33, ±33)
- L1.4 TWO_IN_ROW (was L1.2)
- L1.5 THREE_IN_ROW (was L1.3, congratulation "You are at Level 2 Now!")

**2. Subquest-return rule fixed (`onQuestWindowSuccess`).** Old code reduced N/t by exactly one step regardless of how well the player performed, then "advanced if landed at baseline". This rewarded a player who won at attempt 80 of a 99-window the same as one who won at attempt 5. New rule: `newT`/`newN` = the *minimum* indulgence level at which the player's actual performance fits.
- ROW: `newT = ceil(worstWinErrorMs / stepMs) × stepMs` (rounded up to a whole tick). If `worst_win = 0` → `newT = 0` → advance.
- IN_N: `newN = baseN + ceil((lastWinIndex − baseN) / step) × step`. If `lastWinIndex ≤ baseN` → `newN = baseN` → advance.
- Then `cappedT/N = min(newT/N, currentT/N)` — never *raise* a parameter.
- Advance ONLY when contracted to baseline AND that contraction means the player's results were truly baseline-quality.

**3. `awaitingFailResult` flow restored to end the session.** When the N-window runs out without enough wins, the session closes; the next session starts with the indulged N/t. This had been accidentally collapsed in a prior edit.

**4. `autoClose` path restored.** A session can never exceed `currentN` attempts (33 / 66 / 99 depending on indulgence). Removing this earlier had produced sessions of 65+ attempts after mid-session sub-quest contractions reset `windowAttempts`.

**5. `reEvaluateAttemptsIsWin` removed.** Per Alex's rule, each attempt's `isWin` flag (and its visual colour) is frozen at the moment of recording — under the conditions that were active then. A previously-green 2.99 stays green even after a contraction back to baseline t=0.

### Phase-transition messages (yellow plate, like cheer-ups, English)

`State` got a new field `transitionMessage: String`. It's rendered in `MainScreen` as a rounded yellow plate (`#F5C02E` background, dark text, 12 dp radius) **above** the quest title. Cleared on the next Run press. Five message types:

| Trigger | Message |
|---|---|
| Window failure (quest → harder subquest) | `Subquest 3.00 ± 0.01 ended: 33 attempts, 1 wins. Moving to subquest 3.00 ± 0.02.` |
| Sub-quest contraction (return to less-indulged parent) | `Subquest 3.00 ± 0.02 ended: 50 attempts, 2 wins. Moving to subquest 3.00 ± 0.01.` |
| Quest cleared at baseline (intra-level) | `Quest L1.2 cleared (20 attempts, 2 wins). Moving to quest L1.3.` |
| Level transition | `Quest L1.5 cleared (15 attempts, 3 wins). ★ Level 1 complete. Precision tightens from tenths to hundredths of a second. Moving to quest L2.1.` |
| Game complete (after L3.5) | `Quest L3.5 cleared (12 attempts, 3 wins). ★ All levels complete — game finished!` |

Phase-parameter formatting (`formatQuestParamsAt` / `formatCurrentPhase`) now includes both `± t` and `in N attempts` when the quest is adaptive, so IN_N indulgence is visible in the message (e.g. `3.0 in 66 attempts`).

### Bug fixes

**1. Session-restore numbering bug.** `init {}` restore was writing `state.attempts` in *oldest-first* order while the rest of the code (every `attempts.reversed()` call) assumes *newest-first*. Each subsequent `addAttemptToList` reversed the already-reversed list, scrambling the order over time — visible as `40, 39, 38, 37, 1, 2, 3, … 10` after a few attempts post-restore. Fixed: restore now writes `restoredAttempts` in newest-first and numbers them with `(total − newestFirstIdx)`, so oldest = 1, newest = N.

**2. Twin-shot not marked in Today / Month / All-time views.** `GetLogAttemptsUseCase` never set `twinShot` on DB-loaded attempts (the DB doesn't store it), so only the in-memory log (Session filter) showed the ovals. Added a post-process pass: for each pair of adjacent non-win attempts in the same session, if `||error₁| − |error₂|| < 0.0005`, both get `twinShot = true`. Also passes through `startTimestamp` / `endTimestamp` from the DB so the oval's randomised shape is deterministic per session.

**3. Numbering jump across phases — caused by missing autoClose.** See (4) under quest system above.

**4. SC column blank under Session filter.** Retroactive SC for attempt N−1 was being applied only to `logBuffer`; the Session filter draws from `attempts`. Now applied to `attempts` too.

**5. HOORAY sound was silent.** A prior "always return false" in `onQuestWindowSuccess` blocked the at-baseline advance path. Reverted — sound plays when a contraction wins lands at baseline, and the proper advance flow runs.

## What Was Done (28–29 June 2026 — very long session, v11.11 → v11.35)

### Окружение и автоматизация сборки
- Раньше Claude не собирал APK сам — на машине отсутствовал работающий JDK, а JBR из Android Studio (`C:\Program Files\Android\Android Studio\jbr`) был усечён без `jvm.cfg`. Установлен **Temurin OpenJDK 21.0.5 LTS** в `C:\jdk-21.0.5+11`, `JAVA_HOME` и `PATH` прописаны персистентно для пользователя. С этого момента Claude собирает и устанавливает APK на эмулятор сам.
- Запущен Android-эмулятор `Medium_Phone_API_36.1`. Package на эмуляторе: `ru.iflex.timemvp.quest`.

### Главный экран

#### 1. Совместный фильтр периода (`Session/Today/Month/All time`)
До правки: `MainViewModel.periodFilter` и `StatsViewModel.selectedPeriod` хранились **независимо**. Пользователь выбирал `Today` на главном — на аналитике оставалось `All time`. Σtries разные, геом средние не совпадали попыточно.

- В `LocalStorageService` добавлены `getPeriodFilter()` / `putPeriodFilter()` (ключ `periodFilter`, default = `SESSION`).
- `MainViewModel.init{}` читает фильтр из storage. `Event.SelectPeriod` пишет в storage. Стартовая `loadMetrics(...)` использует тот же сохранённый фильтр (раньше был хардкод `ALL_TIME`).
- `StatsViewModel` (через DI получил `LocalStorageService`): то же — `init{}` загружает аналитику под общий фильтр, `Event.SelectPeriod` пишет в storage.
- Результат: переключился на одном экране — на втором при следующем открытии тот же период активен.

#### 2. Σtries vs метрики рассинхрон
В `MainViewModel.init{}` `loadMetrics(filter, updateLog = false)` подавлял обновление лога. Метрики (гистограмма, геом средние) считались по сегодняшним 444 попыткам, а `displayedAttempts` подхватывался из restore (1 попытка). Σtries показывал 1, а сами числа в `Geom. Mean` — по 444. Поправил `updateLog = true`.

#### 3. Лог в SESSION фильтре после возврата с TODAY/MONTH
`loadMetrics` для SESSION делал `logAttempts = viewState.value.displayedAttempts`. Если до этого был TODAY, там лежали попытки за день, и они оставались в логе при переключении на SESSION (хотя метрики уже считались по сессии). Поменял на `viewState.value.attempts` — текущая in-memory сессия.

#### 4. Геом средние — Early/Late унифицированы, Absolute знаковое
**Early/Late** на главном теперь = модульное геом среднее по сырым ошибкам — совпадает попыточно с `deviationEarly.medianMs` / `deviationLate.medianMs` на аналитике. Раньше главный считал на pre-rounded errors (центи), а аналитика на raw — расхождения на L2.

**Absolute** **знаковое смещение центра масс длительностей**:
```kotlin
geomMeanAbsOffset = geometricMean(durations) − aim
```
- `< 0` при перекосе early, `> 0` при перекосе late, `≈ 0` при балансе.
- Это индикатор баланса, белая пунктирная линия на гистограмме движется в сторону перекоса.
- **Сознательно** расходится с `deviationAll.medianMs` на аналитике (которое модульное `geomMean(|errors|)`).
- Откатил после жалобы 28 июня: модульное `geomMean(|errors|)` всегда давало положительное число и линию всегда правее центра, это было визуально хуже как «индикатор баланса».

#### 5. Touching-sessions набор попыток на TODAY/MONTH
`AnalyticsUseCase` раньше для TODAY/MONTH брал `tryDao.getAttemptsSince(fromTime)` — только попытки после полуночи. `MainMetricsUseCase` брал ВСЕ попытки сессий, «затронувших» период (включая попытки той же сессии, начавшейся до периода). Сессия от 22:00 вчера, продолжившаяся в 01:00 сегодня — на главном вся, на аналитике только послеполуночная часть.

Аналитика теперь считает `touchingSessionIds = getAttemptsSince(fromTime).map { sessionId }.toSet()`, потом загружает по этим session-ID. Совпадает с поведением `MainMetricsUseCase.attemptsForSessionsTouching`.

### Квестовая система

#### 6. Subquest exit logic — три правила в `onQuestWindowSuccess`
Раньше после моего фикса 27 июня победа в субквесте всегда означала шаг на одну ступень назад к родителю; advance только при победе на baseline. Это слишком сурово — игрок, выигравший идеально (все попытки 3.000) ничем не вознаграждался кроме шага назад.

Вернул baseline-quality advance, но в новой формулировке:

- **Rule 1.** На момент победы УЖЕ на baseline → advance к следующему квесту.
- **Rule 2.** Победа в субквесте с **baseline-quality** результатом → прыжок к baseline + advance:
  - ROW (адаптивный t, L2.4/L2.5/L3.4/L3.5): `worstWinErrorMs ≤ baseT` — все 2-3 победные попытки уложились в исходный допуск (`baseT = 0` → строго 3.00/3.000).
  - IN_N (адаптивный N, L1.2/L1.3/L2.2/L2.3/L3.1/L3.2/L3.3): `lastWinIndex ≤ baseN` — N-я (финальная) победа пришлась в первых baseN попытках окна. Если бы окно не было расширено, баseline-квест прошёл бы.
- **Rule 3.** Победа в субквесте, не дотянувшая до baseline-quality → шаг назад на одну ступень. Остаёшься на квесте, нужно ещё одно окно для advance.

#### 7. Заголовок-плашка при провале не зовёт родителя «субквестом»
До: `"Subquest 3.000 ended: ... Moving to subquest 3.000 ± 0.001"` — даже когда `prevParams` был baseline-квест. Поправил `onQuestWindowFailure`: фиксирует `wasAtBaseline` ДО бампа, и плашка теперь `"Quest 3.000 ended: ... Moving to subquest 3.000 ± 0.001"` если до этого был baseline.

#### 8. Заголовок "SubQuest of Y of Z" в логе
- Добавлен ресурс `task_and_goal_for_subquest` в `strings.xml`.
- `MainViewModel.taskDescription()`: helper `isInSubquest()` возвращает true, если `getEffectiveN() > baseN` или `getEffectiveToleranceMs() > baseT`. Title выбирает между обычным/субквестным форматом.
- Session-separator-метка в логе формируется аналогично.
- В `GetLogAttemptsUseCase` (для TODAY/MONTH/ALL_TIME) проверяет `attempts.any { it.toleranceMs > 0 }` — детектит ROW-субквесты по записанному `toleranceMs`. Для IN_N не работает (там `toleranceMs = 0`, изменяется `N`) — это ограничение, ROW-кейс закрыт.

#### 9. Сессия закрывалась на 32-й, а не на 33-й (off-by-one)
Жалоба Alex: квест L3.4 завершался плашкой "32 attempts, 0 wins", хотя по дизайну должно быть 33. Корень — `LocalStorage.getQuestWindowAttempts()` (counter) уходил на 1 вперёд от `state.attempts.size` после navigate/restore или из-за несостоявшегося DB-write.

Два фикса в `MainViewModel`:
- В `init{}` restore синхронизирует `putQuestWindowAttempts(restoredAttempts.size)`, `putQuestWindowWins(restoredAttempts.count { it.isWin })`. Дополнительно пересчитывает `worstWinErrorMs` (max abs error победных попыток) и `lastWinIndex` — иначе baseline-quality в Rule 2 может ошибиться из-за stale значений.
- В `checkVictory` failure-check теперь `min(attemptsCount, storedCount)` — доверяем меньшему из двух источников, чтобы failure не сработал раньше чем игрок увидит N попыток в логе.

#### 10. Race condition между addSession и addAttempt
В Press-обработчике `ON_START` запускался асинхронно `viewModelScope.launch { addSession(...) }`. Если игрок жал STOP **до** того как `addSession` зафиксировал новую сессию в БД, фоллбэк в STOP-обработчике `getCurrentSessionId()?.id` возвращал ПРЕДЫДУЩУЮ сессию. Попытка писалась в БД с sessionId старой сессии, а в `state.attempts` уже шла в новую. Жалоба пользователя «одна попытка в конце старой сессии и одновременно в начале новой».

На L2/L3 это срабатывало чаще — там субквестные контракции сбрасывают `currentSession=null` и тут же стартует новая сессия. На L1 (где почти нет адаптивных квестов с контракцией) реже.

Фикс: Job создания сессии сохраняется в `private var pendingAddSession: Job?`. STOP-обработчик делает `pendingAddSession?.join()` перед чтением `sessionId` — дожидается addSession и setState (`currentSession = NEW`). Гонка устранена.

#### 11. `awaitingContractionResult` — четвёртое awaiting-состояние
Победа в субквесте через Rule 3 (шаг назад) сбрасывала state мгновенно: `attempts=[]`, `currentSession=null`, `time=timePlaceholder()`, `stopWatchState=ON_START`, `buttonResultType=NONE`. Игрок видел свой зелёный 3.00x на 1 кадр и больше не мог его рассмотреть.

Добавлен `awaitingContractionResult: Boolean` в `MainContract.State`. В Rule 3 не сбрасываем state, ставим этот флаг и `taskTitle/transitionMessage`. Кнопка остаётся в `ON_STOP` с зелёным результатом. По тапу RESET — обработчик `awaitingContractionResult` в `ON_STOP` ветке Press-handler'а чистит state и стартует новую сессию на родительском уровне. Auto-close check тоже исключает этот флаг.

Теперь все 5 «терминалов» унифицированы: HOORAY, fail, session-end, subquest-contraction (новое), advance — все держат результат на кнопке до тапа.

### Анализ и логика логов

#### 12. Цвет строки лога в субквесте — миграция БД 5→6
Жалоба: в субквесте L2.4 ±0.01 победная 3.01 в момент попытки красила кнопку зелёным, но в логе при фильтре Today показывалась синей (LATE). Корень — `GetLogAttemptsUseCase` определял isWin как `totalErr == 0.0`, не учитывая допуск.

- Миграция Room **5→6**: добавлена колонка `toleranceMs INTEGER NOT NULL DEFAULT 0` в `TryDatabaseModel`.
- `MIGRATION_5_6` в `KoinModule.kt`.
- `AddAttemptUseCase` принимает `toleranceMs: Int = 0` и сохраняет в БД.
- `MainViewModel.addAttempt(...)` передаёт `toleranceMs = getEffectiveToleranceMs()` — допуск в момент попытки.
- `GetLogAttemptsUseCase`: `isWin = |totalErr × 1000| ≤ attempt.toleranceMs + 0.0005`.
- Старые попытки до миграции имеют `toleranceMs = 0` (default) → для них только exact-нулевые считаются победой (совпадает с прежним поведением). Новые попытки — корректно зелёные в логе.

#### 13. Modes of Deviation — фикс трёх багов разом в `AnalyticsUseCase.topDeviations`
До:
1. Раундинг от `currentLevelId` (текущий уровень игрока), а не от `att.accuracy` (точность попытки) — L2-сессия при L3 показывала фейковые модусы `−47ms` / `−33ms`.
2. После раундинга нули попадали в Early/Late как `"−0ms"` / `"+0ms"` — паразит.
3. Не требовалось `count ≥ 2` — в коротких сессиях top-3 были три случайные попытки с count=1, не модусы.

После:
- Раундинг персональный per attempt: `accuracyRoundingMs(att.accuracy)` (100/10/1 мс для DECISECONDS/CENTISECONDS/MILLISECONDS).
- Нули отбрасываются: `rounded.takeIf { it > 0.0 }`.
- В `topDeviations`: `.filter { it.value >= 2 }` — модус = повтор.

`StatScreen.TopDeviationsCard`: когда `topEarly/topLate` пуст — рисует три прочерка `—` вместо текста `"No repeating early/late deviations"`, форма карточки не плавает.

#### 14. Range of Deviations card redesign
- Убран зелёный «клювик» (downward triangle) над стрипом — отмечал serenity point.
- Убраны белые засечки на стрипе в позициях P3/P97.
- Убрана подпись `serenity point` под стрипом.
- Числа `−116ms` / `+113ms` перенесены к **соответствующему** стрипу (range of deviation) — слева у красного, справа у синего.
- Центр градиента стрипа (зелёная зона) **двигается** пропорционально соотношению `|earlyMs| / (|earlyMs| + |lateMs|)`. При балансе — около середины. При перекосе early — зелёное правее (краснота занимает больше). При перекосе late — зелёное левее.
- Стрип компактнее (18dp вместо 26dp + клювик).

#### 15. Звук разочарования при провале
Файл `res/raw/sorry.m4a` обновлён (новая запись от пользователя, 22 КБ вместо 35).

Триггер перенесён: раньше `playSorryIfAvailable()` дергался в `Event.DismissCheerUp` (при закрытии AFTER_FAIL bubble). Теперь — в момент **появления** статистики проваленного квеста: в обработчике `awaitingFailResult` ON_STOP ветки Press handler, под условием `cheerEnabled`. Звучит ровно одновременно с показом cheer-up bubble.

### Главный экран — UI

#### 16. FaceIcon / ResetFaceIcon — эксперимент и финальное решение
Серия итераций по смайлику-индикатору тренда:
- **Изначально**: `FaceIcon(NICE/SAD/READY, 26dp)` под кнопкой «?» возле заголовка квеста.
- **Эксперимент 1**: совмещённая иконка ResetFaceIcon (стрелка reset + лицо внутри) на кнопке секундомера в ON_STOP. Не пошло — стрелка терялась за лицом.
- **Эксперимент 2**: единый цвет стрелка+лицо (как один элемент). Не пошло.
- **Финал (v11.34)**: вернули стандартный `ic_reset` на кнопке. `FaceIcon(28dp)` вынесен **в левый нижний угол кнопки** через Box + `Alignment.BottomStart`. Показывается ТОЛЬКО в `ON_STOP` (когда кнопка = RESET), не во время `awaitingHurray`. Под «?» отдельного лица больше нет.
- Логика: NICE если `|err|` последней попытки `≤` предыдущей или первая, иначе SAD. READY больше не используется.

### Импорт исторических данных из Excel (фича добавлена и затем удалена в v11.36)

Жалоба: Excel-экспорт приложения хочется загрузить обратно на чистый телефон.

**Статус**: фича была реализована, протестирована на эмуляторе (импорт 20104 попытки / 152 сессии прошёл успешно), затем по решению пользователя **удалена** из приложения в v11.36 — `MainActivity.tryRestoreDatabaseFromExternal()` и вызов вычищены. Скрипт `scratchpad/excel_to_db.py` остаётся как инструмент: при желании можно вернуть фичу — добавить один метод в `MainActivity.onCreate()`. Описание ниже сохраняю для контекста.

#### 17. Python-скрипт `excel_to_db.py`
- Парсит формат экспорта (7 колонок: `Date, Time, Session number, Attempt number, Attempt result, ms, Error time, ms, Accuracy`).
- Группирует попытки по `Session number`, строит `SessionDatabaseModel` (startTime=начало первой попытки, endTime=конец последней).
- Заполняет `TryDatabaseModel` для каждой попытки.
- Создаёт SQLite-файл со схемой Room v6 и зашитым `room_master_table.identity_hash = 64b62cd405d8517cdcd6d062a4c1e14e` (вытащен с эмулятора через `adb exec-out cat`).

Поля, которые НЕ восстанавливаются (нет в Excel):
- `hand` → дефолт `RIGHT`.
- `deflection` → NULL.
- `scm` → NULL (RecalculateScm пересчитает).
- `toleranceMs` → 0 (исторические субквестные победы в логе будут не зелёные).
- `Session.questLevelId/questTaskId/isQuestComplete` → 0 (метка квеста в логе и аналитике пустая).

Файл в скретчпаде: `scratchpad/excel_to_db.py`.

#### 18. Встроенный импорт в приложение (`MainActivity.onCreate`)
Чтобы не возиться с adb, добавлено:
- `tryRestoreDatabaseFromExternal()` проверяет на старте два пути:
  1. **Внешний** `getExternalFilesDir(null)/restore.db` → `/sdcard/Android/data/<pkg>/files/restore.db` — основной путь для пользователя без adb, доступен файловому менеджеру, не требует permissions.
  2. **Внутренний** `filesDir/restore.db` → `/data/data/<pkg>/files/restore.db` — для adb-доставки через `run-as` (debug-сборка).
- Если файл найден — копируется в `databases/time_database.db`, предварительно удаляются `-shm/-wal`, исходник удаляется. Импорт работает один раз, повторного запуска не требует.
- Вызывается **до** `setContent` чтобы Room открыл уже импортированную БД.

Проверено на эмуляторе: pushed 720KB БД через `run-as`, после запуска приложение показало `SESSIONS: 153, ATTEMPTS: 20106, WINS: 279` (на 1 сессию/2 попытки больше из-за пробного запуска до импорта). Все аналитические метрики ожили под 81 день истории.

### Кодовая статистика сессии
- Изменены: `MainViewModel.kt`, `MainContract.kt`, `MainScreen.kt`, `MainMetricsUseCase.kt`, `AnalyticsUseCase.kt`, `StatScreen.kt`, `StatsViewModel.kt`, `GetLogAttemptsUseCase.kt`, `LocalStorageService.kt`, `MainActivity.kt`, `Quest.kt`, `AddAttemptUseCase.kt`, `TimeDatabase.kt`, `KoinModule.kt`, `StopWatchButton.kt`, `FaceIcon.kt`, `strings.xml`, `res/raw/sorry.m4a`.
- Создан: `ResetFaceIcon.kt` (не использовался в финале, но оставлен).
- Версии собраны: v11.12 → v11.35.
- Скрипты в `scratchpad`: `excel_to_db.py`, `inspect_excel.py`, `check_template.py`.

### Известные нерешённые / отложенные
- IN_N-субквесты (L1.2, L1.3, L2.2, L2.3, L3.1, L3.2, L3.3) — в логе при TODAY/MONTH фильтре всё ещё показываются как `Quest`, не `SubQuest of`. `GetLogAttemptsUseCase` детектит субквест по `toleranceMs > 0`, для IN_N это не работает (там адаптируется N, а не t). Для полного покрытия нужно добавить поле `maxAttemptsAtStart` или `isIndulged` в `SessionDatabaseModel` (миграция 6→7).
- Quest 2.2 first-attempt bug (из предыдущего бэклога) — не воспроизведено, не исправлено.
- TEMP DEBUG левой руки auto-stop — не удалён (нужен для тюнинга квестов).
- `worstWinErrorMs`/`lastWinIndex` рассчитываются на restore из `restoredAttempts.isWin`. Эти `isWin` берутся из `GetLogAttemptsUseCase` который считает по `toleranceMs` из БД. Цепочка корректна для попыток после миграции 5→6.

## What Was Done (29 June — 1 July 2026 — v11.36 → v11.38)

### Range of Deviations card — переработка

Изначально была отдельная карточка `MODES OF DEVIATION` с двумя колонками (Early/Late top-3). По просьбе Alex — прибита, а её данные встроены в карточку `RANGE OF DEVIATIONS`.

#### 1. Прибитие TopDeviationsCard
В [StatScreen.kt](app/src/main/java/ru/iflex/timemvp/features/stats/ui/StatScreen.kt) убран вызов `item { TopDeviationsCard(data, selectedPeriod) }`. Само определение `TopDeviationsCard(...)` пока оставлено в файле как мёртвый код (не вызывается ниоткуда) — если понадобится вернуть, доступно.

#### 2. Чёрный текст счётчика в зелёном сегменте attempts ratio
До: счётчик `greenCount` (сколько побед из общего кол-ва) рисовался белым текстом на зелёном фоне — плохо читался. Поправил через новый `greenCountStyle = countStyle.copy(color = Color.Black)`, применён только к зелёному сегменту. Красный и синий счётчики остались белыми.

#### 3. Модусы (top deviations) на градиентном стрипе
`RangeBar` теперь принимает `topEarly: List<TopDeviation>` и `topLate: List<TopDeviation>`. Вызывающий код в StatScreen.kt пробрасывает `data.topEarly` / `data.topLate`.

- **Засечки**: белые вертикальные линии поверх стрипа в позициях каждого модуса. Координата: `frac = (errorMs − rangeStart) / totalSpan`, где `rangeStart = −|earlyMs|`, `totalSpan = |earlyMs| + |lateMs|`. Значения зажаты в `[0..1]` чтобы засечка не вылезла за края стрипа.
- **Подписи**: отдельный Canvas 14dp под стрипом. Каждый модус подписан значением в мс (`−40`, `+22`) — красным для early, синим для late. Центрирование по x-позиции засечки; координаты зажаты по краям чтобы подписи не убегали за пределы стрипа.

Если модусов вообще нет (пустые `topEarly` и `topLate`) — Canvas подписей не рендерится.

#### 4. Чёткая зелёная линия serenity point + подпись
Заменил размытый градиентный переход через зелёный на **чёткую яркую вертикальную зелёную линию** в позиции нулевого отклонения:

- Градиент упростил до `0.00 → resultRedColor, 1.00 → resultBlueColor` (без стопов через зелёный).
- Отдельно рисуется `drawLine` толщиной 2.8dp цветом `resultGreenColor`, x = `size.width × centerFrac`, чуть выше и ниже стрипа для заметности.
- Под стрипом добавлен ещё один Canvas 12dp с подписью `serenity point` в captionStyle, центрирована по x зелёной линии, координаты зажаты в границах.

Итоговая композиция снизу карточки:
```
attempts ratio                              [метка]
[red · green(text black) · blue]            [полоска долей]
range of deviation                          [метка]
−116ms                        +113ms        [значения P3/P97]
[red → blue gradient + serenity green line + mode ticks]
    −40  −20     +10  +50                   [подписи модусов]
              serenity point                [подпись под зелёной линией]
```

### Сборки и файлы
- v11.36 — убрал импорт БД из Excel (был в v11.34, «не нужен»).
- v11.37 — привёл модусы на стрип, чёрный текст в зелёном сегменте, убрал `TopDeviationsCard` из UI.
- v11.38 — чёткая зелёная линия serenity point + подпись, градиент упрощён до red→blue.
- APK на Desktop: `play-of-time-v11.38.apk`.

### Импорт из Excel — окончательный статус
По решению Alex функция `MainActivity.tryRestoreDatabaseFromExternal()` **удалена** из приложения в v11.36. Python-скрипт `scratchpad/excel_to_db.py` остаётся как разовый инструмент; при желании вернуть — добавить один вызов в `MainActivity.onCreate()` (см. предыдущий раздел «Встроенный импорт в приложение»).

## What Was Done (2 July 2026 — v11.39 → v11.54)

Единая длинная сессия. Разбита на три большие темы: анимация смайлика на главном, глубокая переработка карточки RANGE OF DEVIATIONS + дерева сессий на аналитике, и полный редизайн cheer-up системы под сущность субквеста.

### Главный экран

#### Анимированный смайлик на кнопке секундомера
Раньше смайлик появлялся только в `ON_STOP` и торчал статичный (SAD снизу, NICE сверху). Логика переписана в `MainScreen.kt` (`FaceIcon` блок вокруг stopwatch-кнопки):
- Появляется после ПЕРВОЙ попытки сессии, дальше **всегда виден** (в `ON_START` / `IS_RUNNING` / `ON_STOP`), пока не начнётся новая сессия.
- **Плавно скользит** между верхней и нижней позицией при смене эмоции (`Animatable<Float>`, `FastOutSlowInEasing`, 650 ms).
- При **повторе эмоции**:
  - NICE → короткое подмигивание правым глазом (`winkFactor` 0..1, глаз превращается в горизонтальную линию на 120+220 ms).
  - SAD → рот дополнительно изгибается вниз (`mouthStretch` 0..1, ctrlY смещается сильнее вверх, endpoints уходят ниже — 220+420 ms).
- SAD-outline изменён с красного на светло-серый `#B8B8B8` (fill `#F2F2F2`) — грустная физиономия читается как «нейтральная», а не тревожная.

`FaceIcon.kt` расширен параметрами `winkFactor: Float = 0f`, `mouthStretch: Float = 0f`. Обратная совместимость сохранена.

#### Мелкая шлифовка топ-панели и лога
- Top-bar labels («Balance», «True three hit rate», «hand», «Serenity coins») стали ярче (alpha 0.75, Medium).
- **True Three Hit Rate** отображается в формате `x.xx` (было `0%` целочисленное).
- **Σtries / ΣSC** над гистограммой ошибок теперь того же размера что и цифры в бар-строчках лога; правый padding подтянут чтобы ΣSC не наезжал на область гистограммы.
- Убрана серая пунктирная линия в центре гистограммы (продолжала столбец побед в 0 вниз) — зелёный столбик побед теперь единственная вертикаль в центре.

### Аналитический экран — большой рефакторинг карточки RANGE OF DEVIATIONS

Итерационная работа с плотной обратной связью Alex. Финальный вид карточки:

```
[caption] attempts ratio
[ratio bar: red · green · blue]        ← счётчики внутри, thousands separator
[27 dp зазор: «Serenity point» beak по центру, «range of deviation» подпись слева-внизу]
[gradient strip: red → BLUE, резкий переход в центре (0.45..0.55)]
                                        ← −59 ms / +56 ms белым ВНУТРИ strip
[thin П-shape callouts от x-позиций модусов вниз]
[outlined row: «Modes 0.006 x 350 · 0.001 x 371»]
[description text]
```

**Ключевые решения после серии итераций:**
- Клювик — **два продолговатых треугольника** (halfBase 2 dp, triH 11 dp, соотношение 4:11), соединённых тонкой вертикальной линией. Острие каждого треугольника упирается точно в свою полоску.
- Клювик СТРОГО по центру 27 dp-зазора между полосками. Никаких элементов между клювиком и полосками — иначе визуально смещается.
- Подпись **«Serenity point»** — СПРАВА от клювика (было слева, потом справа — окончательное решение).
- Подпись **«range of deviation»** — внутри того же Canvas что и клювик, СЛЕВА, прижата к нижней грани (т.е. непосредственно над нижней полоской).
- Полоска range of deviation: только градиент (0.00 → red, 0.45 → red, 0.55 → blue, 1.00 → blue), никаких засечек/линий на самой полоске. Значения −59 ms / +56 ms **внутри** strip, белым.
- **Modes** — оставили ровно 2 моды (самая частая red + самая частая blue), формат `z.zzz x count` без знака `±` (знак = цвет). Обведены тонкой рамкой (alpha 0.08 — почти прозрачная). Слева на строке — подпись «Modes».
- **Callouts** — строго вертикально-горизонтальные (П-образные): вниз от tick на полоске, горизонталь на середине высоты, вниз до центра подписи. Спред включается только при реальном перекрытии подписей; расстояние ~30 dp (≈ 5 mm, «комфортное для чтения»).
- **Sticky period pill** — `stickyHeader { PeriodPill(...) }` в LazyColumn, всегда пришпилен наверху при скролле.
- **Thousands separator пробелом** для всех чисел > 999 (счётчики попыток в attempts ratio, count модусов, атт4вин и т.д.). Helper `formatCount(n)` в `AnalyticsUseCase.kt` не понадобился — форматирование делается прямо в UI.

### Аналитический экран — дерево сессий

Столбцы полностью перекроены. Было: `SESSIONS | early / late | att / wins`. Стало:

```
SESSIONS | lucky | mean | att4win
2026       0.17    -18 / +15    85.2
  June     0.16    -19 / +15    100.0
    Sun 28 —      -604 / +336   —
    ...
```

- **Lucky** — новый первый data-столбец, per-node lucky index. Вычисляется `luckyIndexOf(bars)`:
  ```
  slotMs = 100 / 10 / 1 (по большинству попыток)
  outcomeSlots = (range / slotMs) + 1
  dispersion = 1 / outcomeSlots     — теоретическая вероятность попасть в 0 наугад
  empirical  = trueThreeWins / bars.size
  lucky      = empirical / dispersion  ← БОЛЬШЕ = ЛУЧШЕ
  ```
  Раньше формула была перевёрнута (`dispersion / empirical`) — я красил > 1.0 красным, Alex справедливо возмутился. Формула исправлена и в `AnalyticsUseCase.kt`, и в `luckyIndexOf(bars)`. Красная окраска убрана; в CHANCE OF WIN карточке под лейблом появилось пояснение `(empirical / theoretical probability — higher is better)`.
- **mean** — переименован из `geom. mean` (было `early / late`). Показывает `−18 / +15` (гео-среднее ранних/поздних ошибок, мс). Заголовок центрирован над двумя цветовыми ячейками.
- **att4win** — переименован из `att / p.win`. Один единственный value (attempts на одну чистую тройку). Заголовок центрирован.
- `SessionDayNode` / `SessionMonthNode` / `SessionYearNode` расширены полем `luckyIndex: Double`, `buildTree()` считает через `luckyIndexOf(dBars/mBars/yBars)`.

### Cheer-up система — полная переработка

Большая многосерийная задача. Итог:

#### Enum (`CheerUpTrigger.kt`)
- **Удалён** `AFTER_FAIL`.
- **Добавлены** 7 субквестных типов:
  - `AFTER_SUBQUEST_ENTER` — первый уход с baseline в субквест
  - `AFTER_SUBQUEST_DEEPER` — уже в субквесте, ещё одна ступень индульгенции
  - `AFTER_SUBQUEST_CAPPED` — только что упёрлись в потолок (N=99 или t=3 ms)
  - `AFTER_SUBQUEST_STALLED` — уже на дне, окно ещё раз пустое
  - `AFTER_SUBQUEST_STEPBACK` — победа в субквесте, но не baseline-quality → Rule 3 шаг назад
  - `AFTER_SUBQUEST_TO_BASELINE` — Rule 2: baseline-quality в субквесте → прыжок к baseline + advance
  - `AFTER_SIMPLIFIED_QUEST_DONE` — через ступени возврата дошли до baseline и взяли его

`AFTER_SESSION` оставлен в enum как есть (не срабатывает — `SessionSummaryOverlay` в дизайне session end).

#### Тексты (`CheerUpMessages.kt`)
Все переписаны в терминах игры (precision / focus / threes / rhythm) — БЕЗ технических «window», «attempt», «adaptive parameter». Короткие, конкретные:

| Trigger | Текст |
|---|---|
| `AFTER_QUEST` | Quest cleared. The focus that landed the threes here — carry it into the next. |
| `AFTER_LEVEL` | Level {N} cleared: 5/5 quests, {attempts} attempts, {trueThrees} clean threes. Same focus — now at {nextPrecision} of a second. |
| `AFTER_GAME` | All 15 quests cleared: {attempts} attempts, {trueThrees} clean threes, {totalTime}. Rare precision, rare persistence. Share the Excel — anonymised — at playoftime@gmail.com · chat @champions · news www.playoftime.lol |
| `AFTER_SUBQUEST_ENTER` | Precision slipped this pass. We widen the mark one step — land the threes there, and the original quest returns. |
| `AFTER_SUBQUEST_DEEPER` | Precision still scatters. One more notch of ease — steady the focus and cluster the threes here. |
| `AFTER_SUBQUEST_CAPPED` | Maximum ease reached. From here it's pure focus — slow the breath, steady the aim, let the threes land. |
| `AFTER_SUBQUEST_STALLED` | Floor reached and the threes still scatter. Nothing left to soften. Rest — sleep on it if you need — the same 99 attempts wait fresh. |
| `AFTER_SUBQUEST_STEPBACK` | Good pass. Focus tightens one notch — the threes need to cluster closer next time. |
| `AFTER_SUBQUEST_TO_BASELINE` | Clean cluster in the softer version. You skipped the whole ladder back — the original quest is already yours. |
| `AFTER_SIMPLIFIED_QUEST_DONE` | You worked all the way back to the original quest — and took it clean. Precision you built through the ladder is yours to keep. |

`AFTER_LEVEL` и `AFTER_GAME` — параметризованы: базовый текст ставится сразу, затем `viewModelScope.launch { … }` дописывает реальные `{attempts} / {trueThrees} / {totalTime}` из БД. Числа отформатированы `formatCount(n)` c thousands separator пробелом. `specificText(trigger, levelId, attempts, trueThrees, totalTimeStr)` — новый вариант, старый без параметров тоже есть для обратной совместимости.

**Analytics-link опция:** `CheerUpMessages.offersAnalyticsLink(trigger)` возвращает true для 5 «победных» триггеров (`AFTER_QUEST`, `AFTER_LEVEL`, `AFTER_GAME`, `AFTER_SUBQUEST_TO_BASELINE`, `AFTER_SIMPLIFIED_QUEST_DONE`) — на бабле у них появляется ссылка «See your progress →».

#### Диспатч триггеров (`MainViewModel.kt`)

**Fail-путь** (`onQuestWindowFailure`). Раньше был единый `AFTER_FAIL`. Теперь выбирается один из четырёх:

```
wasAtCap                 → AFTER_SUBQUEST_STALLED
!wasAtCap && nowAtCap    → AFTER_SUBQUEST_CAPPED
wasAtBaseline            → AFTER_SUBQUEST_ENTER
иначе                     → AFTER_SUBQUEST_DEEPER
```

где `wasAtCap` / `nowAtCap` = `N >= nMax || t >= tMax` до/после бампа, `wasAtBaseline` = игрок был на baseline до этого fail-а.

**Success-путь** — три разных пути:
- **Rule 3** (стeпбэк, `onQuestWindowSuccess()` вернул `false`): `pendingCheerUpTrigger = AFTER_SUBQUEST_STEPBACK`, фаер по тапу RESET из `awaitingContractionResult` (раньше там чирапа вообще не было — тихо схлопывалось).
- **Rules 1/2** (advance, `onQuestWindowSuccess()` вернул `true`): проходит через `awaitingHurray → GreetingView.Close`. Триггер выбирается по приоритету:
  ```
  isAllDone              → AFTER_GAME
  isLastTask             → AFTER_LEVEL
  !wasAtBaselineBeforeWin → AFTER_SUBQUEST_TO_BASELINE   (Rule 2 прыжок)
  wasIndulgedBeforeWin    → AFTER_SIMPLIFIED_QUEST_DONE   (Rule 1 после хождения по субквестам)
  иначе                    → AFTER_QUEST                  (Rule 1 с первого раза)
  ```

**Новый флаг `questWasIndulged`** (`LocalStorageService.kt`):
- `putQuestWasIndulged(true)` — вызывается в `onQuestWindowFailure` при первом бампе N/t.
- `putQuestWasIndulged(false)` — вызывается в `resetQuestAdaptiveParams(nextQuest)` при advance на следующий квест.
- Нужен чтобы отличить «взял квест с первого раза» (AFTER_QUEST) от «взял квест после хождения по субквестной лестнице» (AFTER_SIMPLIFIED_QUEST_DONE).

**Обогащение статистики:** для AFTER_LEVEL и AFTER_GAME после показа бабла запускается `viewModelScope.launch` — считает `attempts` / `trueThreesCount` / `totalTimeStr` через `timeDatabase.sessionDao().getSessionsByLevel()` и `timeDatabase.tryDao().getAllAttempts()`, дописывает в `cheerUpSpecificText`.

**Инъекция `TimeDatabase`** — MainViewModel теперь получает `timeDatabase: TimeDatabase` через конструктор (Koin `viewModelOf` резолвит из существующего single). Новый DAO-запрос `SessionDao.getSessionsByLevel(levelId)`.

**cheerUpAttemptsLeft** — везде `= 3` заменён на `= 1`. Бабл автоматически исчезает на СЛЕДУЮЩЕЙ же попытке, если игрок не закрыл его крестиком/тапом раньше.

#### Bubble UI (`CheerUpBubble.kt` + `MainScreen.kt`)
Полный редизайн по согласованному макету:

- **Фон:** `Color(0xA6F5C02E)` = `rgba(245, 192, 46, 0.65)` — 65% амбер.
- **Text color:** `Color(0xFF2A1E00)` — тёмно-коричневый.
- **Скругление:** 14 dp corners.
- **Blur под баблом** — квест-текст под баблом размывается (radius 10 px) через `RenderEffect.createBlurEffect(...)` в `Modifier.blurBackdrop()`. На API < 31 (Android 12) — no-op fallback: сам фон бабла достаточно плотный (65%), текст всё равно проступает слабо.
- **Крестик (×) в правом верхнем углу** — 22 sp, dark-brown, `Alignment.TopEnd`, тап-таргет 28 dp. По тапу — `setEvent(Event.DismissCheerUp)`.
- **«See your progress →»** — опциональный линк внизу бабла (жирный подчёркнутый). Показывается только если `CheerUpMessages.offersAnalyticsLink(trigger) == true`. По тапу закрывает бабл и делает `navController.navigate(NavRoutes.StatScreen)`.
- **Оверлей:** Бабл лежит в `Box` вокруг quest-title-`BasicText` через `matchParentSize()`. Текст квеста получает `blurBackdrop` модификатор при `bubbleActive`, что размывает его на нужной высоте.

Комментарий про удалённый чирап («Cheer-up bubble удалён 2026-06-27») в MainScreen убран — бабл вернулся.

### Итоговый статус кода
- Изменены: `CheerUpTrigger.kt`, `CheerUpMessages.kt`, `MainViewModel.kt`, `MainScreen.kt`, `MainContract.kt` (unchanged, но `pendingCheerUpTrigger` активно используется), `CheerUpBubble.kt`, `LocalStorageService.kt`, `TimeDatabase.kt`, `FaceIcon.kt`, `AnalyticsUseCase.kt`, `AnalyticsData.kt`, `StatScreen.kt`, `TopMetricsBar.kt`, `SessionStatisticItem.kt`, `DistribChart.kt`, `KoinModule.kt` (не трогали — Koin сам резолвит новый параметр).
- Собранные версии: v11.39 → v11.54. APK на `Desktop`: `play-of-time-v11.54.apk`.

### Известные нерешённые / отложенные
- Тексты чирапов пока проверены только сборкой; полное поведение (все триггеры + blur + analytics-ссылка) на устройстве не гонялось — Alex собирается тестировать сам.
- Enrichment для AFTER_LEVEL / AFTER_GAME считает clean threes как `endTime - startTime == aimTime` (точное попадание). Не учитывает tolerance-победы (это правильно — clean threes = только точные 3.00).
- API 30 и ниже — blur не работает (no-op fallback). На реальном тест-устройстве это увидим только когда до него дойдут руки.
- Из плана 27 июня: `AFTER_SIMPLIFIED_DONE` из тех записей = `AFTER_SIMPLIFIED_QUEST_DONE` реализованный сейчас. Совпадает по семантике.

## What Was Done (2 July 2026 — session 2 — v11.54 → v11.63)

Продолжение того же дня — вторая длинная сессия. Три большие темы: бабл-редизайн + фиксы displayedAttempts, дефолтный фильтр TODAY, и новый визуальный компонент **quest progress с метафорой лучника** (мишени + пучок стрел + инфо-строка), заменивший plain-text quest title.

### Cheer-up бабл — доработка UI

После сборки v11.54 у Alex было два больших замечания:
- «Обводка бабла слабая, крестик виден плохо, ссылка на аналитику поехала — сделай нормально».
- «Ссылка выглядит как говно, надо чтобы читалось CTA».

Полная переработка [CheerUpBubble.kt](app/src/main/java/ru/iflex/timemvp/ui_lib/CheerUpBubble.kt):
- **Обводка** 2 dp насыщенно-янтарным `Color(0xFFFFD35E)` вокруг всего бабла, скругление 16 dp.
- **Крестик**: 30 dp тап-таргет, `24 sp` символ (был 22).
- **«See your progress →»** — вместо подчёркнутого текста стал pill-кнопкой:
  - `RoundedCornerShape(999.dp)` + solid amber заливка `#F5C02E`
  - Обводка 1 dp тёмно-коричневым `#2A1E00`
  - Padding 14 × 7 dp
  - Отдельная строка внутри бабла с right-align
- Прозрачность заливки бабла сохранена (`Color(0xA6F5C02E)` = 65%).
- Убрана предыдущая транзиционная жёлтая плашка (`transitionMessage` — старый жёлтый прямоугольник над квестом): она дублировала cheer-up бабл. Поле state остаётся, но UI больше не рендерит.

### `displayedAttempts` — регрессия «пропадала история между квестами»

Мой предыдущий фикс clear-attempts на границах квестов (в awaitingHurray/awaitingFailResult/awaitingContractionResult/awaitingSessionResult) чистил `displayedAttempts = listOf()` **безусловно**. Это было неправильно:
- При фильтре SESSION лог показывает in-memory attempts → надо чистить.
- При фильтре TODAY/MONTH/ALL_TIME лог показывает данные из БД → чистить НЕЛЬЗЯ, иначе история пропадает при переходе между квестами / после провала окна.

Фикс — во всех четырёх местах теперь:
```kotlin
displayedAttempts = if (viewState.value.periodFilter == PeriodFilter.SESSION)
                     listOf()
                   else viewState.value.displayedAttempts
```

Пользователь описал симптом как «кнопка переключается на session» — это была визуальная иллюзия: фильтр держался на TODAY, но лог был очищен.

### Главный экран — дефолт фильтра TODAY

Раньше `MainViewModel.init` подтягивал сохранённый `PeriodFilter` из LocalStorage (был SESSION по умолчанию). Alex попросил: «на главном по умолчанию всегда показывай статистику за день».

Правка в `MainViewModel.init`: на каждом открытии главного экрана принудительно ставим TODAY и пишем в общий LocalStorage (StatsViewModel увидит тот же период). Игрок может переключить в пределах сессии — но при следующем открытии главного снова TODAY.

### Cheer-up тексты — full сверка row-by-row

Alex провёл **полный редизайн cheer-up таблицы** через row-by-row подтверждение. Мы согласовали заново все 12 текстов + 5 новых субквестных типов. Ниже финальная таблица (уже в коде v11.54):

| Trigger | Текст |
|---|---|
| `AFTER_SESSION` | не срабатывает (defined в enum) |
| `AFTER_QUEST` | Quest cleared. The focus that landed the threes here — carry it into the next. |
| `AFTER_LEVEL` | Level {N} cleared: 5/5 quests, {attempts} attempts, {trueThrees} clean threes. Same focus — now at {nextPrecision} of a second. |
| `AFTER_GAME` | All 15 quests cleared: {attempts} attempts, {trueThrees} clean threes, {totalTime}. Rare precision, rare persistence. Share the Excel — anonymised — at playoftime@gmail.com · chat @champions · news www.playoftime.lol |
| `AFTER_SUBQUEST_ENTER` | Precision slipped this pass. We widen the mark one step — land the threes there, and the original quest returns. |
| `AFTER_SUBQUEST_DEEPER` | Precision still scatters. One more notch of ease — steady the focus and cluster the threes here. |
| `AFTER_SUBQUEST_CAPPED` | Maximum ease reached. From here it's pure focus — slow the breath, steady the aim, let the threes land. |
| `AFTER_SUBQUEST_STALLED` | Floor reached and the threes still scatter. Nothing left to soften. Rest — sleep on it if you need — the same 99 attempts wait fresh. |
| `AFTER_SUBQUEST_STEPBACK` | Good pass. Focus tightens one notch — the threes need to cluster closer next time. |
| `AFTER_SUBQUEST_TO_BASELINE` | Clean cluster in the softer version. You skipped the whole ladder back — the original quest is already yours. |
| `AFTER_SIMPLIFIED_QUEST_DONE` | You worked all the way back to the original quest — and took it clean. Precision you built through the ladder is yours to keep. |

Ключевое правило текстов (запомнить):
- **Никакого «window», «attempt limit», «adaptive parameter»** и прочих технических терминов в user-facing тексте. Только in-domain словарь: precision, focus, threes, cluster, land, aim, rhythm.
- Числа > 999 форматируются с пробелом-разделителем разрядов.

### Quest Progress — метафора лучника

Замена plain-text `state.taskTitle` («Level 2. Quest 2 of 5. Run and Stop exactly at 3.00, two times in 99 attempts») на визуальный компонент с тремя частями:

**1. Мишени (targets):**
- Классический bullseye (кремовый, чёрный, синий, красный, жёлтый центр).
- Диаметр 32 dp.
- Победа (clean three landed) = зелёный wash `0x613FA864` поверх всей мишени + бейдж-круг `#3FA864` в правом верхнем углу с белой обводкой + белый чек.
- Пораженные мишени рисуются ПОСЛЕ непоражённых, чтобы их бейдж не резался соседями (z-index через порядок draw).
- Компоновка:
  - **IN_N / ONE_ONLY** → мишени с gap = 4 dp (толщина внешнего кремового кольца) → «стоят отдельно».
  - **ROW** → перекрытие 25% (сдвиг = −8 dp = ¼ диаметра) → «плечом к плечу, в цепочке».

**2. Пучок стрел:**
- Все стрелы **вертикальные, параллельные** (не веером, не крестом — «просто наваленные»).
- Каждая стрела: наконечник ▲ (серый `#B0B0B0`), древко (коричневое `#8A6A38`, шириной 5 из 16 vb-единиц), оперение (красное `#D14848` с белым разрезом посередине), тёмное ушко.
- **Все контуры белые 1 dp** — «чтобы читалось» на тёмном фоне и не сливалось с древком.
- **Динамика количества по остатку окна:**
  - `remaining >= 11` — полный пучок 11 стрел.
  - `remaining 4..10` — префикс из первых `remaining` позиций (плотность уменьшается видимо).
  - `remaining ≤ 3` — **раздельные стрелы в 3-слотной сетке**, каждая тает по мере расхода: остаётся ровно `remaining` штук.
  - `remaining = 0` — пусто (return @Canvas).
  - Квесты без лимита (L1.1/L1.4/L1.5/L2.1) — всегда полный пучок 11 (условно «неограниченно»).

**3. Инфо-блок (справа от стрел):**
- Строка 1 (10 sp, alpha 0.5): `Level N · quest Y of 5` или `Level N · subquest Y of 5`.
- Строка 2 (12 sp, medium): `aim: <yellow aim time> · <green N/M> · left <green remaining>/<dim N>` или `... · no limit`.
- `(mechanic)` метка в скобках — раньше была `(two in N)` / `(three in row)` и т.п. **Убрана** по просьбе Alex — механика читается по компоновке мишеней.

**Компонент**: [QuestProgress.kt](app/src/main/java/ru/iflex/timemvp/features/main/ui/QuestProgress.kt). Всё через Compose Canvas, никаких XML drawable — 100% векторно.

**Данные из State**: добавлены 4 новых поля в `MainContract.State`:
- `questAimStr: String` — форматированный aim под точность уровня (`"3.0"` / `"3.00"` / `"3.000"`).
- `questToleranceStr: String` — `""` (baseline) или `"± 0.001"` (в субквесте с t > 0).
- `questIsSubquest: Boolean` — сейчас в индульгентной версии.
- `questWindowLimit: Int?` — актуальное N (33/66/99) или null (без лимита).

Populate в `MainViewModel` через новые helper-функции `aimTimeFormatted()`, `toleranceFormatted()`, `currentWindowLimit()`, `isInSubquest()`. **Важно**: они читают из `quest()` (свежий из LocalStorage), а НЕ из `viewState.value.currentQuest` — на init state.currentQuest ещё содержит questList[0] (L1.1), из-за чего был баг «показывало Level 2 · quest вместо subquest».

Обновляются везде где обновляется `taskTitle` — 6 setState блоков (init, GreetingView.Close, awaitingFailResult, awaitingContractionResult, onQuestWindowFailure, awaitingHurray path).

Landed/streak считаются **live в MainScreen** прямо из `state.attempts`:
- IN_N: `state.attempts.count { it.isWin }`.
- ROW: `state.attempts.takeWhile { it.isWin }.size` (стрик от новейших вниз).

### Итеративный дизайн-процесс

За сессию было **8 круговых итераций** через мокапы в `visualize` инструменте, прежде чем найти финальную композицию:
1. Первый макет с колчаном + мишенями с чеком поверх — «нет, не такой подробной, символичной»
2. Замена overlap → connectors (стрелки-соединители) — «фигня, попробуй перекрытие»
3. Перекрытие 25% + модусы промахов на ленте — «зачем лента? а стрелы?»
4. Стрелы поверх + счётчик — «выглядит как говно, нормально»
5. Три компоновки пучка (fan / stack / cross) — «параллельно, не сходятся»
6. Параллельно горизонтально — «сделай вертикально + больше поверх»
7. Вертикальные с оперением — «без чёрных гэпов, только белый outline»
8. Финал: вертикальные, параллельные, белые 1 px обводки, 11 штук, свободно перекрывают

Работа rows-by-row (тексты чирапов, потом варианты мокапов) — **сильно подтверждена** как работоспособная методика с Alex. Никаких «полных пакетов на согласование одним махом» — только по одной единице за раз.

### Изменённые файлы

- `CheerUpBubble.kt` — редизайн pill-кнопки + жирная обводка
- `MainScreen.kt` — замена BasicText(taskTitle) на QuestProgress
- `MainViewModel.kt` — +4 helper-функции + populate новых полей State в 6 местах + conditional clear displayedAttempts + принудительный TODAY при init
- `MainContract.kt` — +4 поля State
- **QuestProgress.kt** — новый файл, полный композит с Canvas-drawing мишеней/стрел
- CLAUDE.md — этот раздел

### Собранные версии
v11.54 → v11.63. APK: `app/build/outputs/apk/debug/play-of-time-v11.63.apk`.

### Известные нерешённые / отложенные
- **Тексты квестов** — обсуждали структурную переработку (двухуровневая иерархия `L2.2 · quest 2 of 5` + `Land 3.00 twice in 99`), сокращение поздравлений, удаление «Run and Stop exactly at» преамбулы. **Не начато** — Alex переходит на следующую сессию.
- **Структурная переработка `taskDescription()`** — сейчас функция генерирует plain text для logs / session separator. Компонент QuestProgress использует свои поля, но старый taskTitle всё ещё генерируется и может кое-где дублировать (log session-separator, GreetingView, transitionMessage). Alex сказал «оставь пока», проверим в следующей сессии.
- **Тестирование на реальном устройстве**: cheer-up бабл + blur + analytics-ссылка не гонялись на API 30 и ниже (blur no-op fallback).
- Из отложенных прошлой сессии остаются: Main Screen Design Alignment, Sparkline Logic Per Period, Quest 2.2 first-attempt bug, TEMP DEBUG hack (левая рука auto-stop).

## What Was Done (3 July 2026 — v11.63 → v11.74)

Длинная сессия. Три большие темы: полная переработка cheer-up бабла (текст + layout + wrap + full-width), защитные фиксы numbering-бага между сессиями/квестами, и переработка правила baseline-quality для ROW-субквестов + max-streak в QuestProgress.

### Cheer-up бабл — тексты, layout, wrap

#### `describePhase()` helper + `composeBubbleText()` в MainViewModel
Единая точка сборки текста бабла. Раньше 6 разных мест дёргали `CheerUpMessages.specificText(trigger)` с разными наборами параметров. Теперь:

```kotlin
// core/utils/CheerUpMessages.kt
fun describePhase(quest: Quest, effectiveN: Int?, effectiveTMs: Int): String
// пример: describePhase(L2.4, N=33, t=10ms) → "3.00 ± 0.01, twice in a row"
```

`describePhase()` — top-level функция. Читает `quest.accuracy`, `quest.victory.victoryCondition/victoryCount`. Возвращает plain-English описание фазы (aim + tolerance + количество раз + условие). Все пробелы внутри — **NBSP ( )**, чтобы значение не разрывалось при wrap: `"3.00 ± 0.01, twice in 33 attempts"` — одна неразрывная строка.

`CheerUpMessages.specificText(trigger, nextQuestLabel, phaseDescription, ...)` — новая сигнатура. Принимает placeholder'ы, возвращает готовый текст.

`MainViewModel.composeBubbleText(trigger, completedStats?, enrichedAttempts, ...)` — обёртка, читает `quest()` / `currentWindowLimit()` / `getEffectiveToleranceMs()` из LocalStorage и подставляет в `specificText`.

Все 6 сайтов, где раньше был прямой вызов `CheerUpMessages.specificText(...)`, теперь используют `composeBubbleText(...)`.

#### Тексты переписаны — plain English без идиом
Alex попросил "переформулируй попроще без идеоматических выражений". Все 11 текстов заменены. Итоговая таблица:

| Trigger | Новый текст |
|---|---|
| `AFTER_QUEST` | Quest done. Next: hit {phase}. |
| `AFTER_LEVEL` | Level {N-1} done: 5/5 quests, {attempts} attempts, {trueThrees} clean threes. Precision now moves to {nextPrecision} of a second. Next: hit {phase}. |
| `AFTER_GAME` | All 15 quests done: {attempts} attempts, {trueThrees} clean threes, {totalTime}. Well done. Share the Excel (anonymised) at playoftime@gmail.com · chat @champions · news www.playoftime.lol |
| `AFTER_SUBQUEST_ENTER` | Not enough wins in the last window. The task is eased. Now: hit {phase}. Beat this and the original quest returns. |
| `AFTER_SUBQUEST_DEEPER` | Still not enough wins. Task eased one more step. Now: hit {phase}. |
| `AFTER_SUBQUEST_CAPPED` | The task is as easy as it can get. Now: hit {phase}. Slow down, focus, land them. |
| `AFTER_SUBQUEST_STALLED` | Still not landing at the easiest setting. Take a break — even sleep on it. When you come back, task waits: hit {phase}. |
| `AFTER_SUBQUEST_STEPBACK` | Good pass. Task tightens one step. Now: hit {phase}. |
| `AFTER_SUBQUEST_TO_BASELINE` | You hit the original goal even in the eased version. Original quest counted as done. Next: hit {phase}. |
| `AFTER_SIMPLIFIED_QUEST_DONE` | You climbed back to the original goal and took it. Next: hit {phase}. |

**Убрано:** `L2.1`-стиль внутренние идентификаторы квестов. Раньше было `"Next quest L2.4: hit ..."`, стало `"Next: hit ..."`. Alex: "убери из текстов всех баблов внутренюю идентификацию квестов типа L2.1".

**NBSP-склейки** — `Next:`/`Now:`/`waits:` + `hit` + `{phase}` — весь блок неразрывный. Wrap возможен только между вводной фразой и меткой `Next:`. Пример:
```
Line 1: Quest done.
Line 2: Next: hit 3.0, twice in 33 attempts.
```

### Cheer-up бабл — layout

#### Wrap-fix для длинного текста
Первый рендер бабла обрезал текст: `"You hit the original goal even in the"` — конец обрывался. Две причины:

1. `BasicText` внутри `Row` без `Modifier.weight(1f)` — unbounded width, soft-wrap не срабатывал. **Фикс:** добавлен `weight(1f)` на текстовый `BasicText` + `fillMaxWidth()` на его `Row`.

2. Бабл в MainScreen вызывался с `Modifier.matchParentSize()`, а parent Box был высотой QuestProgress (~46 dp). Wrapped 2-я строка обрезалась clip'ом. **Фикс:** заменили на `Modifier.fillMaxWidth()`, бабл теперь растёт по контенту.

#### Full width, включая иконку "?"
Alex: "бабл должен быть во всю ширину экрана, включая пиктограмку вопроса".

Раньше бабл сидел внутри `Column(weight=1f)` внутри Row, а `?` — соседний Box. Теперь Row обёрнут во внешний Box, бабл — оверлей на том же уровне что и Row с `fillMaxWidth()`. Blur применён к самой Row → QuestProgress + "?" оба блюрятся под баблом.

```kotlin
Box(fillMaxWidth) {
    Row(.blurBackdrop(40f) if bubbleActive) {
        Column(weight=1f) { QuestProgress(...) }
        Box(size=26.dp) { "?" }
    }
    if (bubbleActive) CheerUpBubble(fillMaxWidth)
}
```

Blur radius: 10 → 24 → 40 (постепенно наращивали).

#### Крестик и CTA
- Крестик `×`: 24 sp → 30 dp тап-таргет → нумбер бабла отодвинут через `padding(end = 28.dp)` на Column'е. Alex попросил "чуть более в углу и обведи его кружочком" → `.offset(x = 6.dp, y = (-4).dp)`, `.size(28.dp)`, внутри `Box(22.dp).border(1.dp, CheerUpTextColor, CircleShape)`, символ `×` 16 sp.
- Pill "See your progress →": солидный янтарь, 999 dp скругление, 14×7 padding, right-align. Показывается только для 5 success-триггеров через `offersAnalyticsLink()`.

### Numbering-баг между сессиями — защитные фиксы

**Жалоба Alex** (в двух заходах):
1. "начало новой сессии не обнуляет счетчик попыток" — после advance L2.5 → L3.1 → L3.2, все 5 попыток лежали в БД с `sessionId=16` (сессия L2.5), а не разбились на #17 (L3.1) и #18 (L3.2).
2. "на первом уровне второй квест, выбить две тройки, засчитывает в качестве первой тройки тройку из предыдущего квеста" — на L1.2 (TWO_IN_N) QuestProgress показывал `1/2` до первой попытки, потому что `state.attempts` содержал L1.1's win.

Root causes:
- `updateSession(currentSession)` в ON_STOP handler пишет `currentSession` с `isQuestComplete=false` из state, перезаписывая `markSessionQuestComplete()` async-write, если тот не успел зафиксироваться. Session остаётся `isQuestComplete=false`, init restore подтягивает её попытки в `state.attempts`.
- При быстрых тапах через HOORAY → TAKE NEXT → RUN между awaiting-состояниями `currentSession` могла не обнулиться → следующая попытка писалась в старую сессию.

**Три защитных фикса** (belt-and-suspenders, каждый закрывает свой угол):

**1.** [`MainViewModel.kt` init — `questMatch` перед restore](app/src/main/java/ru/iflex/timemvp/features/main/ui/MainViewModel.kt):
```kotlin
val questMatch = latest != null &&
    latest.questLevelId == localStorage.getCurrentLevelId() &&
    latest.questTaskId  == localStorage.getCurrentTaskId()
if (latest != null && !latest.isQuestComplete && startedToday && questMatch) {
    // restore state.attempts, counters, worstWin/lastWin
}
```

Восстанавливаем только если сессия принадлежит **текущему** квесту. Страхует от бага, когда `updateSession` перезаписал `isQuestComplete=false` для уже пройденной сессии.

**2.** ON_START handler — mismatch check:
```kotlin
val sess = viewState.value.currentSession
val questMismatch = sess != null && (
    sess.questLevelId != localStorage.getCurrentLevelId() ||
    sess.questTaskId  != localStorage.getCurrentTaskId()
)
if (questMismatch) {
    setState { copy(currentSession = null, attempts = listOf()) }
}
```

При каждом RUN — если `currentSession` относится к ДРУГОМУ квесту, обнуляем её и стартуем новую сессию. Защита от race'а awaiting-состояний.

**3.** init accuracy sync:
```kotlin
val questNow = getCurrentQuest(getCurrentLevelId(), getCurrentTaskId())
if (localStorage.getAccuracy() != questNow.accuracy) {
    localStorage.putAccuracy(questNow.accuracy)
}
```

Форсируем `localStorage.accuracy = quest.accuracy` при старте. Раньше accuracy могла остаться CENTI после L2 (например при импорте БД или после сбоя advance flow), а квест текущий — L1.x. Из-за рассинхрона лог рендерил в сотых `2.97` вместо десятых `3.0`, гистограмма/дистриб-чарт тоже были в чужой точности.

### ROW-квесты — Rule 2 фикс + max-streak в QuestProgress

**Жалоба** #1 (max streak): "выбивания тройки не защитались вообще". Alex сделал 2 попадания подряд на L2.5 subquest ± 0.01, потом промах, потом ещё промах. QuestProgress показывал `0/3` вместо `2/3`. Раньше `landed = state.attempts.takeWhile { it.isWin }.size` — только **текущий** streak от новейшей попытки. После промаха → 0. Игрок видел зелёные 3.00 в логе, но targets 0/3 — "не защитались".

**Фикс** [`MainScreen.kt`](app/src/main/java/ru/iflex/timemvp/features/main/ui/MainScreen.kt): переключил ROW-подсчёт `landed` на **максимальный** streak в окне — та же логика что в `checkVictory.maxRow`. Теперь как только были 2 подряд, targets показывают `2/3` и не сбрасываются на промах.

**Жалоба** #2 (Rule 2 не срабатывал): "перевел на оригинальный квест но поскольку я выполнил условия оригинального квеста в сабквесте должен был перевести на следующий по отношению к оригинальному квесту квест". Alex сделал в субквесте L2.5 ± 0.01 много попыток, в конце три чистых 3.00 подряд. Ожидал Rule 2 → advance к L3.1. Получил Rule 3 → stepback (t=10 → t=0).

**Root cause:** `worstWinErrorMs` в `checkVictory` копил max ошибки по **всем** победам окна за всё время. Ранние победы 3.01 (err=10мс) навсегда зафиксировали `worstWinErrorMs=10`. Потом 3 чистых 3.00 (err=0), но проверка `worstWinErrorMs (10) ≤ baseT (0) = false` → Rule 2 не срабатывал.

**Фикс в двух местах** [`MainViewModel.kt`](app/src/main/java/ru/iflex/timemvp/features/main/ui/MainViewModel.kt):

1. `checkVictory` — при **промахе** (`!isWin && adaptiveT`) сбрасываем `putQuestWindowMaxErrorMs(0)`. Теперь трекер отражает только текущий непрерывный streak, а не всё окно.
2. Init restore — worst-win считается **только по trailing streak** (от новейшей назад до первого промаха), не по всем победам окна:
```kotlin
val trailingStreak = restoredAttempts.takeWhile { it.isWin }
val worstErrMs = trailingStreak.maxOfOrNull { abs(it.error * 1000.0).toInt() } ?: 0
```

Rule 2 теперь работает корректно: если последние N попыток формируют streak со всеми err=0, `worstWinErrorMs=0 ≤ baseT=0` → jump-to-baseline + advance.

### QuestProgress info-строка — NBSP для label/value пар

Alex: "видно что left на одной строчке, а количество попытое на другой. это ошибка исправь".

В info-строке `"aim: 3.000 · 0/2 · left 29/33"` label `left` уходил на одну строку, `29/33` — на следующую. Аналогично для `aim:` + значения, `no limit`, и `3.00 ± 0.01`.

Замена всех breakable-пробелов на NBSP в [`QuestProgress.kt`](app/src/main/java/ru/iflex/timemvp/features/main/ui/QuestProgress.kt):
- `"Level $levelId"` — Level и номер вместе.
- `"$kindLabel $taskId of $totalTasks"` — `quest 2 of 5` целиком.
- `"aim: "` + value — вместе.
- `"$aimTimeStr $toleranceStr"` — `3.00 ± 0.01` вместе.
- `" · left "` — `left` + значение вместе.
- `"no limit"` — вместе.

Логика единая для всех квестов (ONE_ONLY, IN_N, ROW, subquest) — QuestProgress строится по одному шаблону.

### Файлы

- `CheerUpMessages.kt` — `describePhase()` top-level функция, `specificText()` новая сигнатура, все 11 текстов переписаны, NBSP везде.
- `MainViewModel.kt` — `composeBubbleText()` helper, 6 сайтов рефакторнуты, `checkVictory` reset worstWin на miss, init accuracy sync + questMatch restore, ON_START mismatch defense.
- `MainScreen.kt` — full-width bubble layout, max-streak `landed` для ROW, blur radius 40f.
- `CheerUpBubble.kt` — `weight(1f)` на BasicText для wrap, `×` в углу с обводкой кругом, pill CTA.
- `QuestProgress.kt` — NBSP на всех label/value стыках info-строки.

### Собранные версии
v11.64 → v11.74. APK: `app/build/outputs/apk/debug/play-of-time-v11.74.apk`.

### Известные нерешённые / отложенные
- **CTA "See your progress →" на L1 квестах** — Alex говорит "появляется только на L2.2". По коду `offersAnalyticsLink()` возвращает `true` для всех 5 success-триггеров (`AFTER_QUEST`, `AFTER_LEVEL`, `AFTER_GAME`, `AFTER_SUBQUEST_TO_BASELINE`, `AFTER_SIMPLIFIED_QUEST_DONE`), должен показываться на любом advance. Возможно Alex быстро проскакивал L1 бабблы через тапы, `cheerUpAttemptsLeft=1` → autoDismiss на следующей попытке. **Не воспроизведено на устройстве**, ждём подтверждения от Alex после v11.74.
- **Numbering-баг между сессиями** — три защитных фикса добавлены, но root cause (updateSession перезаписывает isQuestComplete=false) не удалён. Прямой фикс: сделать `markSessionQuestComplete` синхронным до `updateSession`, или в state.currentSession выставлять `isQuestComplete=true` до `updateSession`. Пока страхуемся защитами в init/ON_START.
- **Blur под баблом слабый** — QuestProgress тонкий (~46dp), а бабл ~140dp. Большая часть бабла сидит поверх пустого чёрного фона, размывать нечего. Alex поднимал радиус до 40f, но эффект малозаметен. Для сильного blur надо либо scrim (полупрозрачный тёмный overlay) под бабл, либо растянуть блюрируемое содержимое на всю высоту бабла.

## What Was Done (4–5 July 2026 — v11.74 → v11.82)

Обширная сессия по UX-полировке: cheer-up тексты с усреднением, «it was close» скалируется по субквесту, target-сброс, «in row × N/M» формулировка, разделение overlapping twin-групп, замена иконки, полная перестройка нижней навигации, стандартный share-диалог для Excel, скрытие top-bar на всех экранах, level-factor для SC, gate CTA «See your progress» по уровню.

### v11.75 — пять UX-фиксов cheer-up + attempt evaluation

**1. Сравнение со средним в чирапах, начиная с L2.2.**
В `GreetingView.Close` для success-триггеров (`AFTER_QUEST`, `AFTER_SUBQUEST_TO_BASELINE`, `AFTER_SIMPLIFIED_QUEST_DONE`, `AFTER_LEVEL`) на квестах уровня ≥ L2.2 async-хвост дописывает к тексту:
> `You beat your average — 24.3 ms this quest vs 31.7 ms overall.`
> или `Close to your average — 28.1 ms this quest vs 27.9 ms overall.`

Считается mean |error| по всем attempts завершённого квеста (через `sessionDao().getSessionsByQuest(L, T)`) и по `tryDao().getAllAttempts()`. `MainViewModel` — `comparisonEligible` gate + отдельный `viewModelScope.launch { setState { copy(cheerUpSpecificText = base + suffix) } }`.

**2. `it was close` съезжает с субквестом.**
Раньше порог был фиксированный `stepSec` (одна клетка от 0). Теперь `closeDistance = tolSec + stepSec` — одна клетка ЗА пределом допуска. Пример:
- baseline t=0: близко = ±0.001 (одна клетка от 3.000)
- subquest ± 0.001: близко = ±0.002 (одна клетка ЗА допуском)
- subquest ± 0.002: близко = ±0.003

Раньше на индульгентном допуске «close» срабатывал ВНУТРИ win-зоны и конкурировал с TRUE_THREE.

**3. Сброс мишеней при промахе (ROW).**
`landed` в `QuestProgress` вернул на `state.attempts.takeWhile { it.isWin }.size` — текущий streak от новейшей попытки, промах → 0. Внутренний `checkVictory.maxRow` продолжает считать max в окне, так что квест засчитывается когда N подряд случились где угодно в окне (Rule 2 работает).

Отмена короткого решения от 3 июля (max-streak в display): Alex переформулировал как «сбрасывать когда пошли ошибки» → возврат к current-streak семантике.

**4. `aim: 3.00 in row × N/M` для ROW.**
В `QuestProgress` info-line для ROW-квестов между aim и progress вставляется ` in row × `. Пример: `aim: 3.00 in row × 2/3 · left 33/33`. IN_N/ONE_ONLY остаются с обычным `·`. Игрок сразу видит, что нужно подряд, а не «где-то в окне».

**5. Две пары близнецов → два кружочка.**
Twin-group extension теперь разрывается при смене `|error|`:
```kotlin
val itemAbsErr = kotlin.math.abs(item.error)
val prevMatchesErr = prev != null && !prev.isSessionHeader && prev.twinShot &&
    kotlin.math.abs(kotlin.math.abs(prev.error) - itemAbsErr) < 0.0005
val isTwinTopmost = item.twinShot && !prevMatchesErr
// в цикле роста группы — break если |nxt.error| ≠ itemAbsErr
```
Пары 2.95/2.95 → 3.05/3.05 получают два отдельных овала вместо одного огромного вокруг четырёх.

### v11.76 — новая иконка приложения

Alex прислал изображение канатоходца между 2.90/3.01 с зелёной пирамидой «1.0 / 2.00 / 3.000» на градиентном красно-зелёно-синем фоне.

Скрипт на Python (PIL) обрезал квадратно, ресайзил в 5 density'ей: `mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192`. Сохранил как `ic_launcher.png` (square) и `ic_launcher_round.png` (с круговой альфа-маской через `ImageDraw.ellipse`) во все `mipmap-*` папки. Adaptive-icon XML в проекте нет — только PNG.

### v11.77 — BottomNavBar (4 таба)

Новый компонент [`ui_lib/BottomNavBar.kt`](app/src/main/java/ru/iflex/timemvp/ui_lib/BottomNavBar.kt) — 4 равношироких таба:
- **Play** — `NavRoutes.MainScreen`, иконка `ic_run`
- **Analytics** — `NavRoutes.StatScreen`, иконка `ic_stat`
- **Settings** — `NavRoutes.SettingScreen`, иконка `ic_settings`
- **About** — `NavRoutes.AboutScreen`, иконка `ic_about` (создана новая — Material info-circle)

Каждый = иконка + подпись (10sp) под ней. Активный подсвечен `accentColor` + мягкий фон. Через `popUpTo(MainScreen, inclusive=false) + launchSingleTop` — back-stack не раздувается.

Обновлены все 4 экрана — BottomNavBar как sibling внутри `Column(fillMaxSize)`, sticky снизу за счёт `weight(1f)` на контенте:
- **MainScreen** — старые icon-кнопки `ic_settings`/`ic_stat` из нижней Row убраны; `PeriodToggle` в отдельной строке над BottomNavBar.
- **StatScreen** — обёрнут в Column, `LazyColumn` в `weight(1f)`, BottomNav под ним. `stickyHeader` PeriodPill по-прежнему работает.
- **SettingScreen** / **AboutScreen** — тот же паттерн.

**MainActivity NavHost** — `StatScreen`/`SettingScreen`/`AboutScreen` получают `navController` через новый параметр (для BottomNavBar).

### v11.78 — Export button (share via FileProvider), удаление top-bar

**Export = 5-й таб в BottomNavBar.** Не навигация, а action:
1. Генерирует Excel в `cacheDir/excel/play_of_time_<yyyyMMdd_HHmmss>.xlsx` (Apache POI XSSF).
2. Через `FileProvider.getUriForFile()` получает content:// URI.
3. Запускает `Intent.createChooser(Intent.ACTION_SEND)` с `EXTRA_STREAM = uri` + `FLAG_GRANT_READ_URI_PERMISSION` → системный share-диалог (Telegram / Gmail / Drive / любое приложение).

Setup:
- **AndroidManifest.xml** — `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">` + meta-data `android.support.FILE_PROVIDER_PATHS`.
- **res/xml/file_provider_paths.xml** — `<cache-path name="excel_export" path="excel/" />`.
- **[ui_lib/ExcelShareUtil.kt](app/src/main/java/ru/iflex/timemvp/ui_lib/ExcelShareUtil.kt)** — `suspend fun shareExcelAsync(context, scope, exportUseCase)` + Compose-хелпер `rememberExcelShareAction(): () -> Unit` (koinInject'ит `ExportToExcelUseCase`).

Каждый из 4 экранов передаёт `onExport = rememberExcelShareAction()` в BottomNavBar.

**Верхний бар удалён на всех экранах:**
- `Screen(showTopBar = false, ...)` на Main / Stat / Setting / About.
- Драйер (hamburger), заголовки, export-icon наверху — всё исчезло. Навигация исключительно снизу.

**"Analytics" в PeriodPill:**
- В `StatScreen.PeriodPill()` слева от `Session / Day / Month / All time` вписано `Analytics` (`labelStyle(12.sp, SemiBold, accent)`, padding 10dp end). Заменяет прежний топ-бар заголовок.
- Сам pill остаётся sticky сверху (не тронул `stickyHeader`).

### v11.79 — размеры

**Иконки BottomNav в 1.5× меньше:** `NavTab.Image.size(24.dp → 16.dp)`. Текст 10sp остался.

**Гистограмма на ~1/4 ниже:**
- `DistribChart.Canvas.height(64.dp → 48.dp)`.
- `topPad` внутри канваса: `9.dp → 6.dp` (пропорционально, чтобы count-метки над барами помещались).
- `DiagramTotalsRow` (Σtries / ΣSC оверлей) в MainScreen: `padding(top = 14.dp → 8.dp)` — по центру новой высоты.

Distrib axis labels row (10dp) и `GeomMeanRow` (20dp) не тронуты.

### v11.80 — About переписан на английский

Полная замена русского текста в `AboutScreen.kt` на английскую версию. 6 секций (`AboutSection` composable, тот же layout):
1. **About Play of Time** — intro + Gellerstein reference (советский психолог, изучавший восприятие коротких интервалов).
2. **Reaction** — «Every attempt compares your intended action with your actual movement…».
3. **Serenity** — «High accuracy requires balance… Serenity Coins reward steady, well-controlled performance».
4. **Luck Coefficient** — объяснение что Lucky не про случайность, а про attention/anticipation.
5. **Learn more** — `playoftime.online`.
6. **Credits** — Alexander Belyshkin (Concept & Development), Semyon Kulikov (Design).

### v11.81 — SC level-factor (÷100 на L1, ÷10 на L2, ×1 на L3)

**Мотивация Alex'а**: точность растёт 100→10→1 мс между уровнями, значит «работа точности» одного клика на L3 в 100× сложнее L1. SC должна отражать эту разницу — иначе L1 становится комфортным ground'ом для фарма коинов.

Формула:
```kotlin
val levelFactor = 10.0.pow((curr.accuracy - 3).toDouble())
return 99.0 / denominator * levelFactor
```

Читаем `curr.accuracy` (per-attempt, из БД) — исторические попытки при recalc'е получают множитель по своему уровню записи, а не текущему уровню игрока.

Точность:
- L1 (deci, ord=1): `10⁻²` = **×1/100**
- L2 (centi, ord=2): `10⁻¹` = **×1/10**
- L3 (milli, ord=3): `10⁰` = **×1**

Peak за идеальную 3.000: L1 → 0.99 SC, L2 → 9.9 SC, L3 → 99 SC.

**Миграция v3 → v4:** `LocalStorageService.scmRecalculatedV3Key` → `scmRecalculatedV4Key`. У всех существующих игроков `isScmRecalculated()` вернёт `false` при следующем запуске → `RecalculateScmUseCase` пройдёт по всем attempts и пересчитает SC с новой формулой. Old players будут удивлены drop'ом (700 SC для L1-heavy истории → 7 SC), но это честно — точность L1 не стоит той же валюты, что L3.

Обновлён doc-comment в `RecalculateScmUseCase.kt` (v2 → v3 → v4 история).

### v11.82 — CTA «See your progress →» gate по уровню

Alex: «убрать приглашение на просмотр статистики с первого уровня. первый раз кнопка возникает после завершения первого квеста второго уровня» (т.е. arrival = L2.2).

В [MainScreen.kt](app/src/main/java/ru/iflex/timemvp/features/main/ui/MainScreen.kt) при рендере бабла:
```kotlin
val q = state.currentQuest   // уже arrival-квест после advance
val levelUnlocksAnalytics =
    q.levelId >= 3 || (q.levelId == 2 && q.taskId >= 2)
val offersLink = levelUnlocksAnalytics &&
    CheerUpMessages.offersAnalyticsLink(state.cheerUpTrigger)
```

| Advance | arrival q.levelId, taskId | levelUnlocks | CTA |
|---|---|---|---|
| L1.1 → L1.2 | 1, 2 | false | скрыта |
| L1.4 → L1.5 | 1, 5 | false | скрыта |
| L1.5 → L2.1 (AFTER_LEVEL) | 2, 1 | false | скрыта |
| **L2.1 → L2.2 (AFTER_QUEST)** | **2, 2** | **true** | **первый раз** |
| L2.2 → L2.3 и далее | 2, ≥2 или 3 | true | видна |
| L3.5 → GAME | 3, 5 | true | видна |

Работает и для success-триггеров и для subquest-возврата к baseline — везде `state.currentQuest` уже отражает arrival, gate единообразный.

### Файлы, тронутые в 4–5 июля

- `CheerUpMessages.kt` — NBSP между `Next:`/`Now:`/`waits:` и `hit`, plain-text без L2.1-меток, сравнение со средним async в MainViewModel.
- `MainViewModel.kt` — `itWasClose` теперь с `closeDistance = tolSec + stepSec`; comparisonEligible enrichment для L2.2+.
- `MainScreen.kt` — landed для ROW вернулся на `takeWhile` (current streak); twin-group split по `|error|`; CTA gate по level; BottomNavBar + `PeriodToggle` теперь в верхней строке; убрана прежняя row с settings/analytics иконками; histogram tot row padding 14→8dp.
- `QuestProgress.kt` — «in row × N/M» для ROW-квестов в info-line.
- `DistribChart.kt` — 64→48dp, topPad 9→6dp.
- `BottomNavBar.kt` (новый) — 4-5 табов, active state, popUpTo/launchSingleTop.
- `ExcelShareUtil.kt` (новый) — share intent + rememberExcelShareAction.
- `StatScreen.kt` — Column wrap, BottomNavBar, "Analytics" в PeriodPill, showTopBar=false.
- `SettingScreen.kt` / `AboutScreen.kt` — Column wrap, BottomNavBar, showTopBar=false.
- `AboutScreen.kt` — полный английский текст.
- `MainActivity.kt` — navController в StatScreen/SettingScreen/AboutScreen.
- `AndroidManifest.xml` — FileProvider.
- `res/xml/file_provider_paths.xml` (новый) — cache-path.
- `res/drawable/ic_about.xml` (новый) — Material info-circle.
- `res/mipmap-*/ic_launcher.png` / `_round.png` — новая иконка (5 density'ей × 2 варианта).
- `CalculateScmUseCase.kt` — level-factor `10^(accuracy − 3)`.
- `LocalStorageService.kt` — миграционный флаг v3 → v4.
- `RecalculateScmUseCase.kt` — обновлён doc-comment.

### Собранные версии
v11.75 → v11.82. APK: `app/build/outputs/apk/debug/play-of-time-v11.82.apk`.

### Известные нерешённые / отложенные
- **Lucky Index scale — sensitivity к уровню.** Обсуждалось: current формула `empirical / (1/outcomeSlots)` где `slot = 100/10/1 ms per level`. На L1 random baseline высокий (33% при range 200ms), на L3 крошечный (2.4%) → одна и та же «skill excess» даёт очень разные Lucky (1.2 vs 4.1). Alex просил предложить fix — предложены три варианта (A: fixed 1ms slot универсально; B: убрать ratio, показать hit_rate + random_baseline; C: log-scale «skill dB»). Ответ на выбор пока не получен, изменение не сделано.
- **Fake-difficulty tuning L1**: peak за чистую тройку на L1 = 0.99 SC. Alex может захотеть чуть поднять floor (min factor 0.02), пока оставлено как есть — «символические» цифры L1 = feature.
- **Analytics label в PeriodPill sticky** — проверить, как выглядит при скролле аналитики (label остаётся, pill sticky работает). Не тестировалось на устройстве после v11.78.
- **Recalc SC v4 миграция на живой БД** — не проверялось. У старых игроков большой баланс L1-heavy → shock effect после запуска v11.81+. Возможно нужен UI-hint «SC formula updated: L1 hits now weight 1/100 of L3 hits» при первом запуске после апдейта. Пока не сделано.

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

## Important Notes
- Hand toggle is a LEGITIMATE feature (tracks which hand user plays with) — do NOT remove it
- The `deflection` field still exists in TryDatabaseModel (DB compatibility) — just not used anymore
- SessionStatisticItemState.kt must NOT be deleted — it's still referenced by GetLogAttemptsUseCase
- PowerShell is used (not CMD) — use PowerShell syntax for terminal commands
- User prefers written communication over voice for technical work
- User's name: Alex (Alexandr Belyshkin), company: Usethics
