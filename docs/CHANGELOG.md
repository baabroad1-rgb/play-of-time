# Play of Time — журнал разработки (native)

Вынесено из `android/CLAUDE.md` 26 сентября 2026. Причина: журнал занимал
2078 строк из 2232 и грузился в контекст при каждом запуске Claude Code,
хотя нужен редко — только когда надо восстановить, почему что-то сделано именно так.

Порядок записей **не менялся**: как и раньше, сверху старое, снизу новое.
Новая запись добавляется **в конец этого файла**, а не в `CLAUDE.md`.
Правило Алекса писать каждый день остаётся в силе — по этому журналу
веб-версия догоняет нативную.

Точный и полный отчёт о том, что менялось в коде, — `git log`. Этот файл
отвечает на другой вопрос: **почему** так сделано и какие решения за этим стоят.
Одно не заменяет другое.

## Содержание

* [What Was Done (18-19 June 2026)](#what-was-done-18-19-june-2026)
* [What Was Done (20 June 2026)](#what-was-done-20-june-2026)
* [What Was Done (21 June 2026)](#what-was-done-21-june-2026)
* [What Was Done (21 June 2026 — session 2)](#what-was-done-21-june-2026-session-2)
* [What Was Done (23–25 June 2026)](#what-was-done-2325-june-2026)
* [What Was Done (26 June 2026 — cheer-ups & Error-Pattern chip refactor)](#what-was-done-26-june-2026-cheer-ups-error-pattern-chip-refactor)
* [What Was Done (27 June 2026 — long session, v0.10.69 → v11.11)](#what-was-done-27-june-2026-long-session-v01069-v1111)
* [What Was Done (28–29 June 2026 — very long session, v11.11 → v11.35)](#what-was-done-2829-june-2026-very-long-session-v1111-v1135)
* [What Was Done (29 June — 1 July 2026 — v11.36 → v11.38)](#what-was-done-29-june-1-july-2026-v1136-v1138)
* [What Was Done (2 July 2026 — v11.39 → v11.54)](#what-was-done-2-july-2026-v1139-v1154)
* [What Was Done (2 July 2026 — session 2 — v11.54 → v11.63)](#what-was-done-2-july-2026-session-2-v1154-v1163)
* [What Was Done (3 July 2026 — v11.63 → v11.74)](#what-was-done-3-july-2026-v1163-v1174)
* [What Was Done (4–5 July 2026 — v11.74 → v11.82)](#what-was-done-45-july-2026-v1174-v1182)
* [What Was Done (13–14 August 2026 — v13.07 → v13.19)](#what-was-done-1314-august-2026-v1307-v1319)
* [What Was Done (15 August 2026 — v13.19 → v13.21)](#what-was-done-15-august-2026-v1319-v1321)
* [What Was Done (15 August 2026, afternoon — v13.21 → v13.23 native + webapp Version 13.45)](#what-was-done-15-august-2026-afternoon-v1321-v1323-native-webapp-version-1345)
* [What Was Done (16 August 2026 — v13.21 → v13.25)](#what-was-done-16-august-2026-v1321-v1325)
* [What Was Done (17 August 2026 — v13.25 → v13.29) — reconstructed post-hoc, no session wrote this at the time](#what-was-done-17-august-2026-v1325-v1329-reconstructed-post-hoc-no-session-wrote-this-at-the-time)
* [What Was Done (22 August 2026 — v13.29 → v13.31)](#what-was-done-22-august-2026-v1329-v1331)
* [What Was Done (23 August 2026 — v13.31 → v13.32) — coaxiality correction](#what-was-done-23-august-2026-v1331-v1332-coaxiality-correction)
* [What Was Done (23 August 2026, later — v13.32 → v13.33) — targets turn into the animated logo on hit](#what-was-done-23-august-2026-later-v1332-v1333-targets-turn-into-the-animated-logo-on-hit)
* [What Was Done (23 August 2026, later still — v13.33 → v13.34) — hit-badge removed](#what-was-done-23-august-2026-later-still-v1333-v1334-hit-badge-removed)
* [What Was Done (23 August 2026, evening — v13.34 → v13.35) — quest-title timing audit + IN_N gap-based Rule 2/3](#what-was-done-23-august-2026-evening-v1334-v1335-quest-title-timing-audit-in_n-gap-based-rule-23)
* [What Was Done (25 August 2026 — v13.36 → v13.39)](#what-was-done-25-august-2026-v1336-v1339)
* [What Was Done (26 August – 1 September 2026 — v13.36 → v13.53)](#what-was-done-26-august-1-september-2026-v1336-v1353)
* [What Was Done (9 September 2026 — v13.61 → v13.70)](#what-was-done-9-september-2026-v1361-v1370)
* [What Was Done (11 September 2026 — v13.81 → v13.82)](#what-was-done-11-september-2026-v1381-v1382)
* [What Was Done (12 September 2026 — webapp only, no native build)](#what-was-done-12-september-2026-webapp-only-no-native-build)
* [What Was Done (15 September 2026 — webapp only, no native build)](#what-was-done-15-september-2026-webapp-only-no-native-build)
* [What Was Done (16 September 2026 — webapp only, no native build)](#what-was-done-16-september-2026-webapp-only-no-native-build)
* [What Was Done (17 September 2026 — webapp only, no native build)](#what-was-done-17-september-2026-webapp-only-no-native-build)
* [What Was Done (19 September 2026 — v13.87 → v13.90)](#what-was-done-19-september-2026-v1387-v1390)
* [What Was Done (20 September 2026 — documentation only, no build)](#what-was-done-20-september-2026-documentation-only-no-build)
* [What Was Done (21 September 2026 — quest block redrawn from Alex's sketch, v13.91)](#what-was-done-21-september-2026-quest-block-redrawn-from-alexs-sketch-v1391)
* [What Was Done (22 September 2026 — self-update button + LAST QUEST + FailCard face, v13.95 → v13.96)](#what-was-done-22-september-2026-self-update-button-last-quest-failcard-face-v1395-v1396)
* [What Was Done (22–25 September 2026 — v14.06 → v14.12)](#what-was-done-2225-september-2026-v1406-v1412)

---

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
В [StatScreen.kt](../android/app/src/main/java/ru/iflex/timemvp/features/stats/ui/StatScreen.kt) убран вызов `item { TopDeviationsCard(data, selectedPeriod) }`. Само определение `TopDeviationsCard(...)` пока оставлено в файле как мёртвый код (не вызывается ниоткуда) — если понадобится вернуть, доступно.

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

Полная переработка [CheerUpBubble.kt](../android/app/src/main/java/ru/iflex/timemvp/ui_lib/CheerUpBubble.kt):
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

**Компонент**: [QuestProgress.kt](../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/QuestProgress.kt). Всё через Compose Canvas, никаких XML drawable — 100% векторно.

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

**1.** [`MainViewModel.kt` init — `questMatch` перед restore](../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/MainViewModel.kt):
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

**Фикс** [`MainScreen.kt`](../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/MainScreen.kt): переключил ROW-подсчёт `landed` на **максимальный** streak в окне — та же логика что в `checkVictory.maxRow`. Теперь как только были 2 подряд, targets показывают `2/3` и не сбрасываются на промах.

**Жалоба** #2 (Rule 2 не срабатывал): "перевел на оригинальный квест но поскольку я выполнил условия оригинального квеста в сабквесте должен был перевести на следующий по отношению к оригинальному квесту квест". Alex сделал в субквесте L2.5 ± 0.01 много попыток, в конце три чистых 3.00 подряд. Ожидал Rule 2 → advance к L3.1. Получил Rule 3 → stepback (t=10 → t=0).

**Root cause:** `worstWinErrorMs` в `checkVictory` копил max ошибки по **всем** победам окна за всё время. Ранние победы 3.01 (err=10мс) навсегда зафиксировали `worstWinErrorMs=10`. Потом 3 чистых 3.00 (err=0), но проверка `worstWinErrorMs (10) ≤ baseT (0) = false` → Rule 2 не срабатывал.

**Фикс в двух местах** [`MainViewModel.kt`](../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/MainViewModel.kt):

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

Замена всех breakable-пробелов на NBSP в [`QuestProgress.kt`](../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/QuestProgress.kt):
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

Новый компонент [`ui_lib/BottomNavBar.kt`](../android/app/src/main/java/ru/iflex/timemvp/ui_lib/BottomNavBar.kt) — 4 равношироких таба:
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
- **[ui_lib/ExcelShareUtil.kt](../android/app/src/main/java/ru/iflex/timemvp/ui_lib/ExcelShareUtil.kt)** — `suspend fun shareExcelAsync(context, scope, exportUseCase)` + Compose-хелпер `rememberExcelShareAction(): () -> Unit` (koinInject'ит `ExportToExcelUseCase`).

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

В [MainScreen.kt](../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/MainScreen.kt) при рендере бабла:
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

## What Was Done (13–14 August 2026 — v13.07 → v13.19)

Two-day session. Covers: leaderboard feature (new, first native network feature) + two Koin-DI crash fixes on top of it, About-screen credits sync with webapp, three separate bugs in the "Pace of Learning" analytics chart found and fixed across multiple rounds, a Lucky Index divergence-by-multiples bug (three formula implementations unified), a quest-text-updates-too-early bug in the subquest step-back path, a Settings "Erase all data" button that silently did nothing (root-caused to a Compose multi-root-composable layout bug), a new attempt-log scrollbar, help-bubble repositioning, a hand-drawn quest-completion checkmark, and a full redesign of the cheer-up bubble (animation, text format, typography, CTA removed).

### Leaderboard feature (new) — `features/leaderboard/`

Mirrors the webapp's server-backed leaderboard: same domain (`playoftime.online/api/`), same anonymous-identity model (client-generated UUID `playerId` + `syncToken`, persisted once in `LocalStorageService`), same debounce/backfill sync semantics.

- `LeaderboardApi.kt` — plain `HttpURLConnection` + `org.json` (no new dependency), all IO-dispatched, `runCatching{}`-wrapped — never blocks gameplay.
- `LeaderboardSyncManager.kt` — singleton with its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. `notifyNewAttempt()` (debounced 1200ms) fires after every recorded attempt; `backfillAll()` (batches of 500) runs once at `MainViewModel.init` to upload full local history on first sync.
- `LeaderboardScreen.kt` / `LeaderboardViewModel.kt` — new **Ranks** tab, 6th slot in `BottomNavBar.kt`. Sort toggle (Progress/Coins/Accuracy/Combined), editable display-name field, ranked rows list, own row highlighted.
- `TimeDatabase.kt` — `TryDao.getAttemptsForSync()` raw `@Query` JOIN into a plain `SyncAttemptRow` data class (no schema/version bump — projection only).
- `AndroidManifest.xml` — added `INTERNET` permission (first outbound-network feature in the native app).

**Two crash bugs found on first install** (both root-caused via emulator + adb, not guessed):
1. `KoinModule.kt` — `singleOf(::LeaderboardApi)` made Koin try to resolve the constructor's `baseUrl: String = "..."` param as a DI dependency (despite having a default) → `NoBeanDefFoundException` for bare `String`, crash on app launch. Fixed: `single { LeaderboardApi() }`.
2. `LeaderboardViewModel.kt` — `setInitialState()` read `localStorage.getOrCreatePlayerId()`, but `BaseViewModel`'s `initialState by lazy` is forced during the **base class's own** property initializer, which runs **before** the subclass's constructor-injected `val localStorage` is assigned → NPE the moment the Ranks tab was opened. Fixed by moving the read into `init{}` via `setState{}` (matches the established pattern already used by `MainViewModel`/`StatsViewModel` — `setInitialState() = State()`, real values populated after construction completes).

### About screen — Credits line synced with webapp

`AboutScreen.kt` Credits section was missing a third line present on the web version. Compared both apps' About text directly (webapp's `renderAbout()` in `app.js`) — every other section already matched; only Credits differed. Added:
`"Financial support: Alexey Sibiryakov"` (third line, after Concept & Development / Design).

### Pace of Learning chart — three separate bugs, found and fixed across the day

**Bug #1 — null-SC-as-zero.** By design the single most-recent attempt in the whole history never has SC yet (`SaveScmAfterAttemptUseCase` computes SC retroactively for N−1 only once N+1 arrives — "Первая и последняя попытка в истории SCM не получают"). `AnalyticsUseCase.kt`'s chart-bucketing code treated `it.scm ?: 0.0` as an honest zero and divided the bucket's SUM by the FULL attempt count. On Month/All-time (hundreds of attempts per bucket) the effect drowns in noise; on Session/Today (a handful of attempts) one such attempt visibly drags the average down — and in Session mode (raw per-attempt points, no averaging) it drew a hard drop to zero on the very last plotted point every time. Fixed: attempts without a computed SC are now excluded from both numerator and denominator everywhere in the chart path (and in the headline "SC/att" metric, `paceOfLearning`, same root cause).

**Bug #2 — level-factor swamping the trend ("monotone line").** `CalculateScmUseCase` multiplies raw SC by `levelFactor = 10^(accuracy−3)` — L3 is worth ~100–2000× more raw-SC-units than L1 for the *same* precision quality. A player who crossed levels during the All-time window got a chart that was really just "which level was I on this month" (a near-vertical step), not a real learning-pace signal. Fixed: chart values are now normalized to *efficiency* (`scm / peakScmForAccuracy(attempt.accuracy)`, range 0..1, shown as a %) instead of raw SC — the level-factor cancels out, cross-level comparison becomes meaningful. New helper `AnalyticsUseCase.peakScmForAccuracy(accuracy)`. Chart legend/axis in `StatScreen.kt`'s `PaceSparklineChart` updated to `%` and "efficiency" (was "SC/att"); the headline `paceOfLearning` metric itself was **not** touched (it's legitimately an absolute-currency-per-attempt number, different concept).

**Bug #3 — fixed month-granularity regardless of actual history length ("wrong X-axis dimension", Alex's own diagnosis).** `ALL_TIME` bucketing was hardcoded to month-keys. A player a few weeks into the game gets 1–2 real month-buckets — visually flat/meaningless. Fixed: granularity now adapts to the real span of the period's attempts (`spanDays`): ≤1.5 days → hourly, ≤45 days → daily, else → monthly.

**Follow-up (same day, separate request) — break the line on real gaps, don't interpolate through them.** Alex: *"прерывай графики для периодов, данных о которых у тебя нет... не надо тянуть линию или опускать её до нуля формально"*. Buckets were previously built only from calendar keys that actually had data — a week-long gap (no play) silently collapsed, and the line drew a smooth, dishonest-looking connector straight across it. Fixed: `AnalyticsData.paceChartValues` changed from `List<Float>` to `List<Float?>`; `AnalyticsUseCase.bucket()` now walks the *full calendar grid* (step = hour/day/month matching the chosen granularity) from first to last attempt, and a bucket with zero attempts gets `null` (not `0f`, not skipped). `PaceSparklineChart` in `StatScreen.kt` breaks the path (`moveTo` instead of `lineTo`) whenever it hits a `null`, computes min/max from non-null values only, and puts the "current value" dot on the last *real* (non-null) point, not literally the last array index.

### Lucky Index — diverges "by multiples" between screens (Alex's own diagnosis, confirmed by code)

Three separate Lucky Index formula implementations existed in native (`AnalyticsUseCase.kt`'s headline "CHANCE OF WIN" card, `StatScreen.kt`'s `luckyIndexOf` for the Sessions Tree, and `GetQuestSummaryUseCase.kt`'s post-HOORAY GreetingView number). All three used the *same* strict win-numerator (`|error| < 0.5ms`, not tolerance/"green" wins as first suspected and ruled out) — the actual divergence was in the **dispersion denominator's range**: the headline metric uses a P3–P97 percentile-trimmed range of signed errors; the other two used the literal `max − min` of the whole sample, so a single outlier attempt (e.g. an unusually slow first tap of a session) inflated `outcomeSlots` and the resulting Lucky number by a large factor relative to the headline card. Fixed: both `StatScreen.luckyIndexOf` and `GetQuestSummaryUseCase.luckyIndex` now use the same P3–P97-trimmed range as the headline formula; `GetQuestSummaryUseCase` additionally lost its separate non-normalized 1/10/100ms-by-majority-accuracy slot logic (was silently different from the "always normalize to level 3" rule established 2026-07-30) — now uses the same fixed 1ms slot everywhere.

### Quest text updates too early — subquest step-back path

Alex: *"Текст квеста, после завершения, может меняться только после того как показан промежуточный экран с описанием и статистикой (сейчас показывается сразу после выполнения)."* Checked every completion path — HOORAY→GreetingView, window-failure→FailCard, session-auto-close→SessionSummaryOverlay all already deferred title/condition-field updates to their respective tap-handlers (confirmed correct on inspection). The **one** exception: Rule 3 (subquest partial win, step back one indulgence level) updated `taskTitle`/`questAimStr`/etc. immediately inside `checkVictory()`, at the moment of the win — while the green result was still showing on the button, before the player had tapped RESET. Fixed in `MainViewModel.kt`: those field updates moved out of `checkVictory`'s Rule-3 branch and into the `awaitingContractionResult` ON_STOP tap-handler (same place every other completion path already does it). Underlying localStorage N/t values don't change between win-detection and the RESET tap, so the final displayed text is identical — only the *timing* of when it's shown moved.

### Settings — "Erase all data & start over" button did nothing

Reproduced on-device via adb/uiautomator (screen-position bugs in the test tooling itself cost significant time before landing on a clean repro — confirmed via a direct SharedPreferences read that OTHER Settings events do reach the ViewModel, ruling out a broader event-pipeline break). Root cause: `SettingScreen.kt` rendered its confirm dialog / wipe-spinner as **siblings appended after** the screen's main `Column`, with no `return` — i.e. the composable function emitted two top-level nodes in one pass. `MainScreen.kt`'s equivalent full-screen overlays (`WelcomeOverlay`/`GreetingView`/`FailCard`) all use `if (condition) { Render(); return }`, ensuring exactly one root node is emitted. Inside `HorizontalPager`'s per-page slot, the second sibling node apparently isn't stacked as an overlay — the tap on "Erase data" *did* flip `state.confirmReset` to true (confirmed), but the dialog never rendered. Fixed: `SettingScreen.kt` restructured to the same early-return pattern, checks moved before the main `Column`.

### Attempt-log scrollbar (new, main screen)

`AttemptLogScrollbar` in `MainScreen.kt` — thin vertical thumb on the right edge of the attempt log `LazyColumn`, size/position estimated from `listState.layoutInfo` (average visible-item size × total item count). Visible only while `listState.isScrollInProgress`, fades out ~600ms after scrolling stops (`animateFloatAsState`).

### Help "?" bubble — banner no longer overlaps the quest title

`HelpTapOverlay.kt`'s invitation banner (shown on first tap of "?", before any zone is selected) was pinned to the overlay's top-left corner — directly over the quest-title text that starts right under the "?" icon. Repositioned to sit **below** `questionRect` with a `HelpBeak` pointer, using the same positioning math already used for a selected zone's bubble.

### Hand-drawn checkmark on quest completion (new)

`QuestProgress.kt` gained a `completed: Boolean` param (wired from `MainScreen.kt` as `state.awaitingHurray`) — when true, a small green checkmark draws itself stroke-by-stroke over ~700ms (`PathMeasure.getSegment` animated via `Animatable`), appearing to the right of the quest-conditions text at the exact moment a quest is won (while the green HOORAY result is still showing, before GreetingView).

### Cheer-up bubble — full redesign (this was a 4-part request, all landed)

1. **Smooth appear/disappear** — bubble was a bare `if()`, appearing/vanishing as a hard cut. Wrapped in `AnimatedVisibility` (fade+scale, 220ms in / 160ms out for the normal "×"/next-attempt dismiss path). `lastBubbleText` local latch keeps the text visible through the exit animation (state clears `cheerUpSpecificText` in the same `setState` as `cheerUpTrigger`, which would otherwise blank the text mid-exit).
2. **CTA removed** — "See your progress →" button (and its level-gating logic, `offersAnalyticsLink()`) deleted entirely from `CheerUpBubble.kt`, `CheerUpMessages.kt`, and the `MainScreen.kt` call site.
3. **"Quest #L.T:" replaces "Next:"/"Now:"** — `CheerUpMessages.kt`'s `specificText()` now builds every phase-bearing trigger's text as `"$opener\nQuest #$nextQuestLabel:\n$phaseDescription."` — quest-number label computed unconditionally in `MainViewModel.composeBubbleText()` (was previously gated to advance-only triggers), format changed from `"L2.3"` to `"2.3"` (bare digits, "Quest #" prefix supplies the word). The quest-task text always starts on its own line now (forced `\n` before `$phaseDescription`).
4. **Amber highlight removed, bigger font** — the `**bold**`-segment amber-background parsing was deleted from `CheerUpBubble.kt`; body text is now plain, `fontSize` raised 14sp → 16sp (`lineHeight` 20sp → 22sp).
5. **RUN press deflates the bubble** (new interaction — previously the bubble only auto-dismissed on the *next completed attempt*, i.e. on STOP). Pressing RUN while a bubble is showing now dismisses it immediately, but with a distinct 1.5s "deflate" exit — `shrinkVertically(tween(1500), shrinkTowards = Alignment.Top)` + slow fade, vs. the quick fade+scale used for the "×" tap. The stopwatch button (next sibling in the same `Column`) automatically "follows" the collapsing bubble — no manual coordination needed, Compose animates the layout reflow as the `AnimatedVisibility` node's height shrinks. A local `dismissedViaRun` flag (set by a `LaunchedEffect` watching `stopWatchState == IS_RUNNING`) picks which exit transition `AnimatedVisibility` uses.

### Files touched this session
`features/leaderboard/**` (new package), `KoinModule.kt`, `AndroidManifest.xml`, `AboutScreen.kt`, `AnalyticsUseCase.kt`, `AnalyticsData.kt`, `StatScreen.kt`, `MainViewModel.kt`, `SettingScreen.kt`, `MainScreen.kt`, `HelpTapOverlay.kt`, `QuestProgress.kt`, `CheerUpBubble.kt`, `CheerUpMessages.kt`, `BottomNavBar.kt`, `TimeDatabase.kt`.

### Collaboration note
Alex asked that changes be summarized into this file **daily** going forward (not just at odd multi-day intervals) specifically so the webapp coworker session can pick up native-side formula/behavior changes without re-deriving them from a live source diff each time. Apply this from now on: at the end of each day's work (or clearly before context is at risk of being lost), append a "What Was Done (date — vX.XX → vY.YY)" section here, same format as above.

### Known open / deferred
- Cheer-up bubble redesign not yet tested on-device for the RUN-deflate animation's actual feel (only compile-verified + reasoned through, not visually confirmed on emulator/device).
- Coworker (webapp session) has not yet been asked to sync any of this — Lucky Index range-trim fix, Pace of Learning gap-breaking/level-normalization, and the cheer-up bubble text-format change (`Quest #L.T:` replacing `Next:`/`Now:`) are all native-only right now and will diverge from webapp until a sync pass.

## What Was Done (15 August 2026 — v13.19 → v13.21)

Short focused session. Two topics: three small cheer-up-bubble/quest-label refinements requested right after yesterday's redesign, an attempt-log scrollbar smoothness fix, and a full app-icon/logo replacement (source artwork correction + regenerated assets everywhere the logo appears, native and webapp).

### Cheer-up bubble / quest-label follow-ups

**1. Indulged subquests get a distinct tag format: `SubQuest#2.4(1)`.** Previously an indulged subquest and a baseline quest both just showed `L.T` (e.g. "2.4") with no visual cue that N/t had been eased. Alex: *"если это индульгированный квест то в бабле (и вообще там где это уместно) надо писать в формате SubQuest#2.4(1). число в скобках — уровень индульгирования."* The number in parens is the indulgence step (1/2/3, from the existing `subquestStep()` helper — already computed elsewhere, just not surfaced in this format before). Applied in three places:
- `MainViewModel.composeBubbleText()` — the tag fed into the cheer-up bubble is now `"SubQuest#${q.levelId}.${q.taskId}($step)"` when `isInSubquest() && step > 0`, else unchanged `"Quest #${q.levelId}.${q.taskId}"`.
- `MainViewModel`'s session-log-header builder (the `Session #N · … · HH:mm` separator row) — same tag format replaces the old `"Level L. SubQuest T.step"` text.
- `QuestProgress.kt`'s compact info line — `questTail` becomes `"SubQuest#$taskId($subStep)"` (Level is already shown separately to its left via `"Level $levelId · …"`, so no redundant level digit here).
- `CheerUpMessages.specificText()`'s `nextQuestLabel` doc-comment updated to describe the full tag is now passed in pre-built (not just "2.3") — the function no longer hardcodes the `"Quest #"` prefix itself, since the caller now supplies the whole tag string.

**2. The word "hit" had silently vanished from the quest-task line.** Alex: *"куда делся hit в начале задачи?"* Root cause: yesterday's "Next:"/"Now:" → "Quest #{L.T}:" reformatting rewrote the template but dropped the verb that used to precede the phase description (old format was literally `"Next: hit {phase}."`). Restored: `questBlock = "$nextQuestLabel: hit $phaseDescription."` — e.g. `"Quest #2.4: hit 3.00 ± 0.01, twice in a row."` / `"SubQuest#2.4(1): hit 3.00 ± 0.02, twice in a row."`

**3. Quest text no longer forced onto its own line, but is now visually distinct via size.** Two related asks: *"и сам текст квеста, по отношению к названию не надо сносить на следующую строку"* + *"сделай слова, начиная с Quest, большим шрифтом. то что до этого трогать не надо."*
- `CheerUpMessages.kt` — removed the `\n` between the tag and `hit {phase}` (they were on separate lines as of yesterday's redesign); now one line: `"{tag}: hit {phase}."` The opener/recap sentence above still gets its own line (untouched, not part of this ask).
- `CheerUpBubble.kt` — the body text is no longer a single plain `BasicText`. It's now a `buildAnnotatedString`: everything from the first match of `"Quest #"` or `"SubQuest#"` onward is wrapped in a `SpanStyle(fontSize = 19.sp)` (base is 16sp), so the quest tag + task description reads larger than the recap/opener sentence that precedes it — opener styling is completely untouched, per Alex's explicit "don't touch what's before."

### Attempt-log scrollbar — jerky movement fixed

Alex: *"скроллер некрасивый и двигается рывками."* Root cause: `AttemptLogScrollbar`'s thumb position was derived from an *average* item size across all currently-visible rows (`visibleItems.sumOf{it.size}/count`). Session-header rows (tall, carry embedded charts) sit interleaved with plain log rows (short) in the same `LazyColumn`, so that average swings sharply frame-to-frame as different mixes of headers/rows scroll into view — producing visible jumps rather than smooth tracking. Fixed in `MainScreen.kt`: position is now a continuous fraction built from the *first visible item's own real offset and size* (`(first.index + (-first.offset)/first.size) / totalItems`), which doesn't depend on what else happens to be on screen. Also widened 3dp→4dp, added a faint always-visible track behind the thumb (was floating on bare background), and gave the thumb a minimum height (24dp) so it doesn't shrink to an illegible sliver in long logs.

### App icon / logo — triangle re-centered, replaced everywhere

**Source fix.** `app-art-rgb-variant.png` (repo root) — the play-button triangle was measurably off-center: analyzed via a Python/PIL script (corner-to-circle-edge gap for each of the triangle's 3 corners), found the right tip sat ~52px from the circle edge while the two left corners sat ~31–33px — a ~20px asymmetry, visible as the triangle looking pushed left. Solved for the exact rightward shift that equalizes all three gaps (binary search over the shift amount against the corner-distance-to-center function): **13px right**. Applied via a rigid pixel-shift (color+hole-fill mask over the triangle+letter silhouette, `scipy.ndimage.binary_fill_holes` to include the letter interior despite it being colored near-identically to the navy background, then `np.roll` to move the masked pixels as a unit, erasing the vacated strip with the sampled flat navy background color `#101a26`). Result: all three corners now ~38.7–40.4px from the circle edge (was 31.5/33.2/51.9). Verified via zoomed crops of all three corners — no seams or leftover fragments from the rounded-corner anti-aliasing (first attempt using hand-picked polygon vertices left a small notch artifact at the top-left rounded corner; switched to the color+hole-fill mask approach specifically to avoid needing to hand-estimate where the rounded corner curve actually extends).

**Regenerated everywhere the logo appears** (Alex: *"заменим именно на это изображение и пиктограмму и вообще везде где всплывает логотип"*) — found two previously-divergent "families" of this artwork in the repo (a partial-ring/text-gap version like the root file, and a full-ring-no-gap version used for the launcher icon and webapp PWA/social assets) and unified everything to the one corrected source:
- Native app launcher icon — `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` **and** `ic_launcher_round.png` (48/72/96/144/192px), regenerated by direct LANCZOS resize of the corrected 512×512 source (its transparent corners already make it valid for both square- and round-icon-shape devices, no extra masking needed).
- `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_about_logo.png` (60/90/120/180/240px) — the About-screen logo image.
- `app/src/main/ic_launcher-playstore.png` (512px) — Play Store listing icon.
- Webapp `assets/app-art-rgb-variant.png` — plain copy of the corrected root file (same filename already referenced by the About page, no HTML changes needed there).
- Webapp `assets/app-art.20260809l.png` → **new file** `assets/app-art.20260815a.png` (cache-busted per the project's dated-filename convention) — used for `og:image`, `twitter:image`, and the homepage `.launcher-art` image; all 3 references in `index.html` updated to the new filename.
- Webapp `assets/icon-192.v11.png` / `icon-512.v11.png` → **new files** `icon-192.v12.png` / `icon-512.v12.png` — favicon (`<link rel="icon">`), `apple-touch-icon`, the header `.brand-mark` image, and both entries in `manifest.webmanifest`'s PWA icon list; all references updated. Old `v11` files left in place (unreferenced, harmless) rather than deleted.

### Files touched

`app-art-rgb-variant.png` (root, corrected source), `MainViewModel.kt`, `CheerUpMessages.kt`, `CheerUpBubble.kt`, `QuestProgress.kt`, `MainScreen.kt` (scrollbar only — the deflate-animation/CTA/format work from yesterday's session is unchanged), `res/mipmap-*/ic_launcher*.png` (10 files), `res/drawable-*/ic_about_logo.png` (5 files), `ic_launcher-playstore.png`, `webapp/assets/app-art-rgb-variant.png`, `webapp/assets/app-art.20260815a.png` (new), `webapp/assets/icon-192.v12.png` / `icon-512.v12.png` (new), `webapp/index.html`, `webapp/manifest.webmanifest`.

### Build
v13.19 → v13.21 (auto-bumped to v13.22 for the next build). APK on Desktop: `play-of-time-v13.21.apk` (old v13.19 removed per the cleanup habit). Alex is installing/testing manually this time — not verified on-device by me this session.

### Known open / deferred
- The two icon "families" unified today (partial-ring vs full-ring) were themselves drifted apart at some earlier point without anyone deciding to diverge them — worth keeping an eye out for a third divergence next time either surface gets touched in isolation.
- Old webapp `icon-192.v11.png` / `icon-512.v11.png` / `app-art.20260809l.png` left on disk unreferenced — harmless, but could be deleted in a future cleanup pass if Alex wants a tidier assets folder.
- Webapp coworker session has not yet been told about the `SubQuest#L.T(step)` tag format or the "hit" restoration — both are cheer-up-bubble text changes that should port to the web version's equivalent copy if it has one.

## What Was Done (15 August 2026, afternoon — v13.21 → v13.23 native + webapp Version 13.45)

Long session, almost entirely webapp-focused (one small native addition). Covers: a new animated brand-mark logo (native + webapp + standalone MP4/SVG deliverables), a full About-page layout redesign on the webapp, a real cache-staleness bug found and fixed at the root, a webapp file cleanup, and an in-progress investigation into a reported webapp data-persistence bug.

### Animated logo — triangle + letter flip, ring inertia-spin

New continuous animation built from the corrected `app-art-rgb-variant.png` (the triangle-recentered source from the prior session): the ring/circle/"PLAY OF TIME" text rests most of the time, then periodically does a full clockwise spin with an overshoot-and-settle "inertia" ease, landing back at its start orientation (it's rotationally symmetric at 360°, so the loop is seamless). The triangle and the letter "t" continuously flip around a **strictly vertical axis** positioned at the letter's measured horizontal centroid (48.5% of canvas width) — counter-rotating (opposite directions, same 4s speed).

**Design iteration, in order (each a direct response to Alex's feedback):**
1. First pass: triangle+letter rotate together as one rigid layer. Alex: "t должна стоять на месте, она не крутится вместе с кнопкой" — split into 3 layers (ring / triangle-only / letter-only) using a color-mask + `scipy.ndimage.binary_fill_holes` split, so the letter could be extracted and controlled independently. First attempt at a triangle-without-letter layer left a visible "ghost" of the letter (a baked-in soft drop-shadow in the source art, not just the sharp glyph) — fixed with OpenCV Telea inpainting (`cv2.inpaint`) over a dilated hole, which cleanly removes both the glyph and its shadow halo.
2. "Попробуй вариант чтобы t вращалась в другую сторону" — counter-rotation added.
3. "Выглядит неотличимо" — correctly diagnosed as the **"spinning dancer" illusion**: a flat `rotateY` plane's width-squish is `cos(θ)`, an even function, so `+360°` and `−360°` are pixel-identical at every instant; no amount of speed/timing tweaking can fix this, only a genuine asymmetric visual cue can. First attempt: a mask-clipped "glare" sweep — had a real bug (the glare div didn't share the shape's own rotation animation, so it drifted out of registration and looked like an extra ghost letter — the "лишние отблески в виде инвертированной буквы" Alex reported). Replaced entirely with Alex's own suggestion: a **motion trail** (2–4 faint, time-lagged copies of the same rotating image, via `animation-delay`), which is direction-correct automatically (a trailing ghost always lags on the true trailing side, whichever way the shape is turning) and has no synchronization to get wrong.
4. Trail tuned twice: made denser/bigger on the letter only (4 copies, 55%→15% opacity), then **removed entirely from the triangle** per explicit request ("у треугольника трейл уберем а у буквы оставим").

**Deliverables produced:**
- `logo_anim_common.py` (scratchpad) — single Python source of truth for the CSS/HTML (`build_style()` / `build_markup()`), reused by every downstream target so they can't drift out of sync.
- Standalone HTML prototype, SVG (CSS animation wrapped in `<foreignObject>`), and an MP4 render (`render_video.py` — reimplements the CSS 3D `perspective(900px) rotateY(θ)` transform as a per-frame homography matrix via `cv2.warpPerspective`, composited frame-by-frame; hit and fixed one real bug — mixing a 0–255-scale base canvas with 0–1-scale layer images in the alpha blend, which rendered as solid black until caught and fixed). 20s loop @ 24fps (LCM of the 4s flip cycle and 5s ring cycle, so everything re-syncs and loops seamlessly).

### Native — About screen (`AnimatedLogo.kt`, new file in `ui_lib/`)

Ported 1:1 (same timing constants) using Compose's real 3D primitives — `graphicsLayer { rotationY = angle; cameraDistance = ...; transformOrigin = TransformOrigin(0.485f, 0.5f) }` — rather than reimplementing the perspective math by hand; `rememberInfiniteTransition` per rotating copy (main + trail ghosts) with `StartOffset(delayMs, StartOffsetType.Delay)` reproducing the trail effect, `Animatable` + a manual `LaunchedEffect` loop for the ring's non-uniform rest/spin/settle keyframe shape (a plain `infiniteRepeatable` can't express that). Three new `drawable-nodpi` PNGs (`pot_logo_ring/triangle/letter.png`) hold the split layers. Wired into `AboutScreen.kt` replacing the static `ic_about_logo` image. One build-time fix needed: `animateFloat` is an extension function requiring its own import — qualifying only the receiver (`rememberInfiniteTransition`) wasn't enough. v13.21 → v13.23.

### Webapp — animated logo rolled out everywhere the brand mark appears

- **About screen** (in-game) — replaced the static `<img class="about-logo">` with the layered markup.
- **Homepage launcher shortcut** (`#gameLauncher` button, `.launcher-art`) — same replacement; this one was missed in the first pass (Alex: "в вебапп сделано... осталось только чтобы на сайте шоткат тоже крутился") since it's a completely separate DOM location from the in-game About screen.
- **Site header brand-mark** (`.brand-mark`, both desktop and mobile — one CSS rule, no per-breakpoint override) — same replacement, then sized to 60px (3× the original 20px).
- CSS scope generalized: originally scoped as `.about-logo .pot-logo` (only matched one location); rewritten to plain `.pot-logo` so the same rule set works in any container the markup is dropped into, avoiding three near-duplicate CSS blocks.

### Real cache-staleness bug found and root-caused (not a deploy mistake)

Alex reported the About-page animation "didn't work" after deploying, then insisted (correctly, and with justified frustration after a couple of wrong "did you deploy?" turns from me) that it wasn't a deploy issue. Root-caused by directly fetching the **live** `playoftime.online` files and inspecting the browser's own parsed `CSSOM` (`document.styleSheets[...].cssRules`) — found the browser was serving a stale cached copy of `styles.20260814a.css` even after a hard page reload, because the filename never changed between edits (browsers/CDNs have no reason to refetch a same-named URL). Reproduced this myself, first-hand, in the debugging session (not a guess) via a controlled fresh-CSS-link test. **Fix**: renamed to `app.20260815a.js` / `styles.20260815a.css` (this project's own established cache-busting convention — should have been applied to these two files from the start, same as the image assets already were).

*Side lesson from the same debugging arc*: an earlier false alarm (`getAnimations()` returning empty, `computedStyle().transform` stuck at "none") turned out to be **my own** test methodology clicking nav buttons inside a `display:none` game overlay that was never actually opened — CSS animations don't run on hidden elements. Real verification required actually driving the UI through Play → Start playing → About, same as a real user.

### About-page layout, iterated in several rounds per Alex's feedback

1. Logo size: 64px (original) → 128px (2×) → `clamp(140px, 32vw, 220px)` (fills the available space, responsive) — "занимал все пространство между абзацами справа".
2. Version/date stamp: was a hand-typed string that had gone stale (still showed 12 Aug while edits were happening on the 15th). Alex: "должна обновляться всякий раз при деплое" — replaced with `document.lastModified` (the server's real file mtime for `index.html`) for the date, keeping only the semantic version number (`APP_VERSION` const) as manually-bumped. Self-updating from now on with zero manual editing, as long as `index.html` is re-uploaded on every deploy (already required anyway, since cache-busted filenames force an `index.html` edit).
3. "Learn more" + the `playoftime.online` link: moved onto one line (was heading-style label + link split apart via flex `space-between`), logo moved from beside the link to **below it, left-aligned**, Credits panel immediately follows. Then simplified once more — Alex: "это должна быть одна обычная фраза... в смысле единая, не надо ее разносить" — dropped the heading styling entirely, now a single plain sentence: `<p class="about-text">Learn more <a href="...">playoftime.online</a></p>`.
4. Header brand-mark alignment: was flush with `.site-header-inner`'s own edge, while `.page`'s body text sits 42px further in (`.page` has its own internal `clamp(18px,4vw,42px)` padding that `.site-header-inner` doesn't share — its inset comes from `.site-header`'s padding instead, one layer up). Added a matching `margin-left` to `.brand` so the header logo lines up with the page's actual text column.

### Webapp file cleanup

Audited every file actually referenced (transitively, from `index.html` → CSS/JS → `manifest.webmanifest`) and moved everything unreferenced to a local backup (`scratchpad/webapp_cleanup_removed/`, not deleted outright): old versioned JS/CSS (`app.20260812i.js`, `app.20260813a.js`, `styles.20260812d.css`, `styles.20260814a.css`), an unused `icon.svg`, and five stale image assets (`app-art-rgb-variant.png` — dead now that the About logo is inline-animated rather than an `<img src>`, plus old `v11` icons and an unused Serenity slide PNG). Verified nothing broke: full site walkthrough (Play/Analytics/Ranks/Settings/About) with zero 404s afterward. Gave Alex the exact keep-list for what needs to actually be on the server vs. what's dev-only (`*.md` docs, `reference/`, `.claude/` — confirmed via direct fetch that none of these are even reachable on the live server, so no cleanup needed there).

Also checked for stray `iflex`/`ifirst` mentions (Alex's ask, likely referring to the native package name `ru.iflex.timemvp` leaking into public-facing text) — found only in `webapp/reference/native-src/*.kt` and `TASKS.md`, both dev-only and confirmed 404 on the live server. Nothing to remove.

### Known open / deferred

- **Reported webapp data-persistence bug, not yet fixed** — Alex: "при повторном логине данные не сохраняются". Investigated the `saveDb()`/`loadDb()`/`normalizeDb()` path — structurally sound (correct call coverage, correct merge/migration logic), and directly confirmed real `localStorage` survives an actual page reload. The one genuine code-level weak point found: `browserLocalStorage()` silently falls back to a `window.name`-based store (which does **not** survive closing the tab/app) whenever real `localStorage` throws — private browsing, some in-app/embedded browsers, or storage-blocking settings would all trigger this with zero visible error. Waiting on Alex to confirm which browser/device shows the symptom before deciding whether this fallback path is actually the cause or something else needs to be found.
- MP4/SVG animated-logo deliverables were one-off exports for Alex to review/share — not wired into any build pipeline; if the trail/timing constants change again in `logo_anim_common.py`, those two files would need manually regenerating to match.

### Files touched
Native: `ui_lib/AnimatedLogo.kt` (new), `res/drawable-nodpi/pot_logo_{ring,triangle,letter}.png` (new), `features/about/ui/AboutScreen.kt`.
Webapp: `index.html`, `app.20260815a.js` (renamed from `...813a.js`), `styles.20260815a.css` (renamed from `...814a.css`), `app-art-rgb-variant.png` (root, unchanged from prior session — referenced by the now-removed static About image, no longer used).
Root: `app-art-rgb-variant.png` unchanged this session (already fixed previously).

## What Was Done (16 August 2026 — v13.21 → v13.25)

Very long session. Two parts: (1) a ~20-round iterative firework/salute animation prototype built entirely in scratchpad HTML/CSS/JS, refining ring↔triangle sync, a real two-stage rocket+burst particle system, and real-recording sound design; (2) once approved, a full integration pass wiring the fix and the finished salute into the actual native app, webapp, and every icon asset.

### Part 1 — Firework prototype (scratchpad only, ~20 rounds)

Round-by-round, each iteration was a direct response to specific feedback, not guesswork — every geometry/timing claim below was measured from the actual rendered art (Python + OpenCV/PIL replicating the exact `perspective(900px) rotateY(θ)` CSS matrix, or live browser matrix/pixel inspection), not eyeballed:

- **Ring/triangle sync** — found that firing a salute in sync with "the triangle touching the ring" requires the two independent CSS animations (ring free rotation, triangle's own `rotateY` flip) to be locked, not just timed once. Landed on gearing them **exactly 1:1** (one ring lap per triangle flip cycle) with the ring's start phase solved so a specific letter ("O" in "OF") sits exactly on the touch point at the touch instant — this is the *only* way to guarantee the touch angle is crossed exactly once per cycle with nothing else passing that point unaccompanied. A faster-but-approximate ring (independent period, "close enough" sync) was tried first and explicitly rejected once the geometry was worked through — no partial/lucky solution survives contact with "one and only one point," it forces the 1:1 gearing.
- **Symmetric left+right touch** — extended to a second (right-side) touch, found by the same leftmost/rightmost-pixel theta-sweep method.
- **Two-stage rocket+burst salute** — replaced flat "single burst" confetti with a real two-phase system: a few rocket sparks rise (decelerating, physically-correct projectile arc) to a shared apex above the ring's vertical axis, then hand off to a much larger burst (34 pieces) that explodes and disperses left/right under gravity. Confetti rotation is a linear spin **plus a sine wobble** (chaotic-but-plausible tumbling, not a smooth constant spin).
- **A real regression, caught and fixed mid-session**: a keyframe-generation refactor left CSS animation ranges only bounded *around* each event, not across the whole loop — anything outside that narrow window fell back to the browser's *implicit* rest style (full opacity, un-transformed, sitting at its own origin) instead of staying invisible. Symptom: permanent "parasitic" specks and a fixed yellow blob. Root-caused and fixed by always clamping 0%/100% to duplicates of the first/last real keyframe.
- **Triangle axis bug, found by Alex's own eye** — "left touches, right leaves a small gap." Root-caused: the triangle shared its rotation axis (48.5%) with the letter layer, but that value was calibrated for the *letter's* glyph centroid, not the triangle's own shape. CSS mirrors a rotated element around wherever `transform-origin` sits (not the true perspective-origin/center) — the 7.7px gap between 48.5% and the true 50% center got **doubled** by the mirror, adding ~15.6px of extra reach to the left touch only. Fixed by giving the triangle its own axis (50%, its true center) — verified the fix numerically (left/right max reach equal to within 0.03px, was 15.65px apart).
- **Letter repositioning, two wrong attempts before landing right** — Alex asked for the letter's lower hook-curl to be "coaxial" (соосно) with the rounded corner it points toward. First attempt made the two circle *centers* fully coincide — wrong, "соосно" means sharing an axis (a *line*), not a point, and it also dragged the letter too low. Second attempt corrected the vertical position (letter's own center now equidistant from the triangle's upper/lower edges) but broke the axis alignment by assuming that axis was vertical — it isn't; the real axis is the corner's angle *bisector*, a diagonal line (measured at 28.5° off vertical from the two edge directions, using the actual edge vectors, not assumed). Final fix: move the letter along that diagonal bisector from the fully-concentric point until its own center lands on the edge midline — satisfies both constraints at once, verified (measured post-shift angle 28.45° vs. the 28.5° bisector, and the vertical-centering criterion independently). Net shift from the ORIGINAL untouched position: ~18.4px left, ~4.4px up (in the 320px logo) — a small, mostly-horizontal correction, not the large vertical drag either wrong attempt produced.
- **Sound design, iterated from synthesized → single "barabum" take → a 55-second real "fireworks + crowd" recording** (`fireworks-a-crowd-of-people.wav`, supplied by Alex). Measured the actual audio (envelope/onset detection, not guessed) and found it's genuinely structured as bang+crowd-reaction / quiet gap / repeat — extracted **9 full natural phrases** (400ms short pops up to a 7s sustained barrage-with-cheering), each traced from its onset to where the envelope genuinely decays back to quiet (not a fixed-length cut, which was an earlier, explicitly rejected attempt that still "cut off" mid-phrase). These 9 clips are randomly reused for both the exact salute hits (capped ≤2.2s with a real fade if the natural phrase runs longer, so a ~2s firing cadence doesn't stack whole barrages) and a continuous low-volume ambient bed (full natural length every time, one clip at a time with a random 1.4–3.2s gap) — so everything heard is always a complete, undamaged piece of the real recording, never truncated abruptly.

### Part 2 — Integration into the real app

Once Alex approved the final prototype, four things were fixed/replaced **at the file level** (not just in the scratchpad) so both the native app and the webapp reference the corrected assets:

1. **Static icon corrected everywhere.** Recomposited ring+triangle+corrected-letter at rest pose (θ=0 — the triangle axis fix doesn't affect the *static* unrotated image at all, only the letter shift does) into a new master `app-art-rgb-variant.png` (root). Regenerated from it: all 5 native `mipmap-*/ic_launcher.png` + `ic_launcher_round.png` densities, `ic_launcher-playstore.png`, and the webapp's `icon-192.v13.png` / `icon-512.v13.png` / `app-art.20260816a.png` (cache-busted, all references in `index.html` + `manifest.webmanifest` updated).
2. **"Simple flip" animated logo (no salute) corrected.** Native `ui_lib/AnimatedLogo.kt`: split the single shared `AXIS_X_PCT` into `TRIANGLE_AXIS_X_PCT` (0.5, new) and `LETTER_AXIS_X_PCT` (0.485, unchanged) on the shared `SpinningLayer` composable; `pot_logo_letter.png` replaced with the corrected art. Webapp: same axis split in `styles.20260816a.css`'s `.pot-triangle`/`.pot-letter` rules, and the letter's base64 (embedded inline, both the main image and its 4 trail-ghost copies — 5 occurrences, all replaced) swapped in `app.20260816a.js`. This variant already routes to both About screens (native `AboutScreen.kt`, webapp `renderAbout()`) — confirmed still wired, no changes needed there.
3. **Salute animation ported from the HTML prototype into a real Compose component** — new file `ui_lib/SaluteCelebration.kt`. Ring/triangle/letter driven by one shared elapsed-time clock (not independent `rememberInfiniteTransition`s) so the locked 1:1 sync carries over exactly; the two-stage rocket+burst particle system draws via `Canvas` using the *closed-form* physics functions directly (not the 20-sample keyframe approximation the CSS version needed) for a smoother result. The outer composable is sized larger than the 320-unit logo box itself (matching the prototype's margins) with the logo centered inside, since bursts fly well past the logo's own edges and would otherwise get clipped. Sound: the 9 phrase clips were sliced out of the WAV at build time (Python, into `res/raw/salute_clip_0-8.wav` + `ambient_clip_0-8.wav`, ~7MB total, fades baked into the samples since Android's `SoundPool` doesn't support live gain-envelope automation) and played via `SoundPool` (fixed a real bug caught in on-device logcat during testing: `SoundPool.load()` is async and calling `play()` before a clip's own load finished silently no-ops — added proper per-id readiness tracking via `setOnLoadCompleteListener` instead of assuming everything was ready).
4. **Wired the salute in** — `ui_lib/GreetingView.kt`'s `SaluteIcon()` (previously a pulsing static `ic_win` icon) now hosts `SaluteCelebration()`, so it fires on **every quest and level completion screen**, as asked. Verified end-to-end on the emulator: built, installed, played through to a real quest completion (`Level 1 · Quest 3 — Complete!`), confirmed the salute renders (ring/triangle/letter/confetti all visible, correctly positioned) with no crashes in logcat, then rebuilt again after the SoundPool fix.

### Files touched
Native: `AnimatedLogo.kt`, `GreetingView.kt`, `SaluteCelebration.kt` (new), `res/drawable-nodpi/pot_logo_letter.png`, `res/raw/salute_clip_{0-8}.wav` + `ambient_clip_{0-8}.wav` (new, 18 files), `res/mipmap-*/ic_launcher*.png` (10 files), `ic_launcher-playstore.png`.
Webapp: `index.html`, `manifest.webmanifest`, `styles.20260816a.css` (new, from `...815c.css`), `app.20260816a.js` (new, from `...815c.js`), `assets/icon-192.v13.png`, `assets/icon-512.v13.png`, `assets/app-art.20260816a.png` (all new).
Root: `app-art-rgb-variant.png` (corrected letter position baked in).
Scratchpad: `layer_letter.png` (canonical source updated to match), `build_firework_demo9.py` through `build_firework_demo33.py` (prototype iteration), `android_clips/` (sliced audio, source for the `res/raw` copies).

### Collaboration note
This was another long row-by-row session (per [[feedback_working_style]]) — the letter-axis fix in particular took two visibly-wrong attempts (shown as rendered PNG previews, not just described) before the third landed; each was corrected from Alex's own precise visual read of the result, not from re-deriving the math alone.

### Known open / deferred
- The salute's on-device visual polish (particle density, colors, exact burst timing feel) was approved in the HTML prototype and ported 1:1, but the *Compose* rendering hasn't had the same round-by-round aesthetic review the HTML version got — worth a dedicated pass if it doesn't read the same on a real device vs. the emulator.
- ~~`res/raw/salute_clip_*` + `ambient_clip_*` are uncompressed WAV...~~ **Resolved 2026-08-25** — re-encoded to lossless FLAC via Python `soundfile`/libsndfile (no ffmpeg needed); see that day's entry below.
- Webapp does not yet have an equivalent "quest/level completion" screen wired to the salute (the salute+sound Compose work is native-only) — webapp's About screen already got the corrected *no-salute* flip, but if webapp ever grows its own celebration screen, the salute would need a separate JS/CSS port. **Superseded 2026-08-17 — see below**: the webapp session ported its own Canvas-based salute the next day.

## What Was Done (17 August 2026 — v13.25 → v13.29) — reconstructed post-hoc, no session wrote this at the time

**How this entry came to exist**: on 2026-08-22, a fresh session found the actual build on disk (`play-of-time-v13.29.apk`, `version.properties` timestamped 17 Aug 21:41) was four patch versions ahead of this changelog's last entry (v13.25, 16 August) — a live violation of [[feedback_daily_changelog]]. Per the division-of-labor Alex agreed with the webapp/Cowork session on 2026-08-13 (`webapp/TASKS.md`), day-to-day native development had moved to Alex working directly in Android Studio — so no Claude session was present to write a same-day entry. Reconstructed here from file mtimes (`find -newermt`) plus the dated inline comments Alex left in the code itself (`grep "Alex 2026-08-17"`), not from a live session transcript — treat specifics as best-effort, not a verified first-hand account. Files touched, by mtime: `CheerUpMessages.kt`, `CheerUpBubble.kt`, `MainScreen.kt`, `SaluteCelebration.kt` (this last one at 00:03, likely still the tail of the 16 August session rather than a new same-day edit).

**1. Cheer-up bubble openers shortened.** `CheerUpMessages.kt`'s `pickOpener()` doc comment, dated 2026-08-17: *"Короче: ≤6 слов, одно предложение. Раньше openers были развёрнутой прозой (иногда два предложения) и читались как несогласованные по ритму с задачей ниже — теперь это просто короткий кивок, а вся «суть» — в строке задачи."* Every opener bank (`AFTER_QUEST`, `AFTER_LEVEL`, `AFTER_SUBQUEST_*`, etc.) was rewritten to short punchy fragments — e.g. `AFTER_QUEST` is now `"Quest cleared." / "Quest done — nailed it." / "That's the quest, cleared." / "Quest done — good control."` (was longer prose per the 4–5 July session's original table).

**2. Cheer-up bubble — unified font size, arrow anchor, wave emoji.** `CheerUpBubble.kt` comment, dated 2026-08-17: *"не нравится что разным размером шрифта"* — the 15 August redesign's size-based split (opener 16sp / quest-tag-onward 19sp) is gone. Now **everything renders at one size (15sp/21sp line-height)**; the visual split between opener and quest-task line is done by weight + color only (opener: normal weight, muted brown `#6B5210`; quest-tag-onward: bold, dark brown `#2A1E00`), plus a `"→ "` arrow prepended right before the quest tag as an eye anchor. A `"👋"` wave emoji was also added at the start of the bubble (wasn't there as of the 15 August redesign).

**3. Cheer-up bubble "×" now uses the same deflate animation as RUN-press.** `MainScreen.kt` comment, dated 2026-08-17: previously (13 August) the "×" tap closed with a quick fade+scale (160ms) while pressing RUN mid-bubble used a distinct slow 1.5s `shrinkVertically`/fade "deflate". Both dismiss paths now use the same 1.5s deflate. Auto-dismiss on STOP (next attempt) is unchanged — still fast.

**4. Salute canvas margins tightened.** `SaluteCelebration.kt` comment, dated 2026-08-17: the outer canvas around the 320-unit logo box was sized to a flat 200/260 margin (matching the original HTML prototype's generous canvas), leaving most of the reserved card area empty — real particle travel measured at only ~50 units sideways and ~160 units above the logo. Margins reduced to measured worst-case reach: `MARGIN_SIDE` 60f, `MARGIN_TOP` 170f, `MARGIN_BOTTOM` 30f (was flat 200/260), with a `LOGO_VERTICAL_BIAS` computed from the new asymmetric top/bottom margins to keep the logo itself correctly centered in the now-smaller card.

**5. Webapp got its own salute port the same day** (not a native change, but relevant context missed by the "Known open / deferred" note above): per `webapp/TASKS.md`'s 2026-08-17 entry, the Cowork/webapp session read this file's own changelog (not just the stale `reference/native-src/` mirror — a process fix in itself) and, in one batch, ported four items to `app.js`/`styles.css`: the deferred quest-switch-only-after-GreetingView-dismissed behavior, the hand-drawn completion checkmark, the attempt-log scrollbar, the cheer-up bubble animations, and a from-scratch Canvas reimplementation of the firework salute (same locked timing constants copied from `SaluteCelebration.kt`, different PRNG for particle scatter — behavior ported, not a bit-identical random sequence). **That session's port target was `app.20260817a.js`/`styles.20260817a.css` — it predates today's font-size/opener/margin changes above**, so a future sync pass should re-diff those specific pieces once this entry exists to point at.

### Known open / deferred
- **This entry is a reconstruction, not a first-hand session record** — if anything above turns out to not match what Alex actually intended, trust his correction over this writeup.
- The salute-margin tightening (#4) and bubble redesign (#1–3) are NOT yet reflected in the webapp's 17 August port (see #5) — worth flagging to the webapp session on the next sync.

## What Was Done (22 August 2026 — v13.29 → v13.31)

Bottom nav reorder + Export relocation, distribution-chart label cleanup, help-icon state, all verified on-emulator with pixel measurements (not just code review).

### Bottom navigation (`BottomNavBar.kt`, `MainActivity.kt`, `SettingScreen.kt`)
- **Export tab removed** from the bottom nav entirely — moved into Settings, as a new row directly above "Erase all data & start over" (`StartOverSection` now takes `onExport`, renders an "Export attempt history" row using the same `rememberExcelShareAction()` helper, called from `SettingScreen` itself instead of `MainActivity`). `BottomNavBar`'s `onExport` param removed.
- **Visual tab order changed** to Play – Ranks – Analytics – Settings – About (was Play – Analytics – Settings – About – Ranks) so the Analytics balance-gauge indicator sits dead-center among 5 tabs. Pager page indices are unchanged (`onSelectPage` still uses the same 0/1/2/3/4 mapping) — only the visual `NavTab(...)` call order in the `Row` moved.
- **Balance gauge enlarged** 24×12dp → 32×16dp (label untouched, still 10sp) per Alex's ask to "try making the indicator bigger, keep the label the same size."
- **Analytics icon horizontally aligned to the histogram/log "0" axis.** The histogram's zero is NOT at true screen center — `DistribChart`'s asymmetric margins (64dp left label column, 32dp right SC column, per `CHART_LABEL_WIDTH`/`CHART_SC_PAD`) push it ~16dp right of center. Measured directly on a real emulator screenshot (1080×2400 @420dpi): the "0" tick (drawn by `GeomMeanRow`'s center line) sits at pixel x≈581.5, while the Analytics tab's natural center was x≈539 — a 42.5px / 16.2dp gap. Added a `contentOffsetX: Dp` param to `NavTab`, applied `Modifier.offset(x = 16.dp)` to the whole Analytics tab (icon+label move together, background highlight follows). Re-measured after the fix: gauge color-pixel center landed at x≈580.5 — within 1px of the target. **This 16dp is empirical, not derived from a formula in code** — if `CHART_LABEL_WIDTH`/`CHART_SC_PAD` ever change, or if a session with browser/DOM-inspector-equivalent tooling wants an exact value, remeasure the same way (screenshot + pixel-scan for the `GeomMeanRow` center tick, compare to the tab's `uiautomator` bounds) rather than trusting 16dp as permanent.

### Main screen — distribution chart (`DistribChart.kt`, `MainScreen.kt`)
- **"Distrib." label removed** (Alex: "и так понятно" — self-evident without it) — was the left-column label on the axis-values row under the histogram. Replaced with a bare `Spacer(width = CHART_LABEL_WIDTH)` so the numeric axis labels (`-0.9 -0.5 0 +0.5 +0.9` etc.) keep their position. Side-effect (intended): that row's height used to be set by the 11sp text's line-height, taller than the 10dp value-label canvas next to it — removing the text let the row collapse to the canvas's own 10dp, which visually pulls `GeomMeanRow` (the next sibling, no explicit spacer between them) up closer to the histogram. This is what satisfied Alex's separate "raise Geom. Mean up a level" ask — no extra layout change was needed once the label was gone.
- **"Σtries" → "Total tries"**, and its label font size bumped 9sp → 11sp to match `GeomMeanRow`'s "Geom. Mean" label (same row family, was visibly smaller before).

### Help icon (`MainScreen.kt`)
- The "?" in the circle next to the quest title now renders as "×" while `helpMode` is active (tap again, or tap any zone, exits — unchanged logic) — same circle now doubles as the exit control. Verified via a real emulator tap + cropped screenshot: circle correctly shows "×" once help mode is entered.

### Verification method
All of the above were checked on a live `Medium_Phone_API_36.1` emulator, not just read from source: installed the built APK, generated real attempt data via the `TEMP DEBUG` left-hand auto-stop hack (`docs`/`MainViewModel.kt` — HandToggle=LEFT was already selected by default this session), screenshotted, and for the pixel-critical alignment item used Python/PIL to scan actual pixel columns rather than eyeballing (`GeomMeanRow` center-tick detection, `BalanceGauge` colored-pixel bounding box) — same methodology this project's changelog already documents using for the logo/salute work.

### Known open / deferred
- **Quest task formulation change — not started.** Alex said "будет картинка" (an image is coming) for this item; waiting on that before touching quest-title/description text or layout.
- ~~The 16dp Analytics-tab offset is empirical...~~ **Superseded same day, see below** — the 16dp per-tab offset was wrong and got reverted a few hours later once Alex clarified the actual requirement.

## What Was Done (23 August 2026 — v13.31 → v13.32) — coaxiality correction

Direct follow-up to the same day's bottom-nav/alignment work above, after Alex looked at the result and asked for something stricter: **four** separate "0" reference points across the app — the stopwatch button's aim tick, the error bar in each log row, the distribution histogram, and the bottom-nav Analytics icon — all coaxial (same vertical line), AND the bottom nav still evenly spaced/symmetric about the center icon. The 16dp per-tab offset shipped a few hours earlier (see above) achieved coaxiality for 2 of the 4 points but broke the nav's own symmetry — exactly what Alex flagged.

**Root cause, found by measuring all four points on a real screenshot (Python/PIL pixel-scan, not eyeballing):**
- Stopwatch button's green "3.0" aim tick (`StopWatchButton.kt`'s `SecondsProgressBar`): x≈539.5px — sits at the TRUE horizontal center of the screen (its canvas is plainly centered by the parent Column, no asymmetric side columns).
- Histogram (`DistribChart.kt`) and log error-bars (`SessionStatisticItem.kt`'s `cx = w/2f` per-row "0" line): x≈581.5px — NOT at true center. Root cause: `CHART_LABEL_WIDTH` (64dp, left — matches the log's number+time columns) and the old `CHART_SC_PAD` (32dp, right — matches the log's SC column) are unequal, so the shared plot area's own midpoint sits ~16dp right of true screen center. This was long-standing, deliberate-but-unexamined design (matching histogram to log, never checked against the button or nav).
- Bottom-nav Analytics icon: naturally ≈539.5px (true center, via `Arrangement.SpaceEvenly` across 5 equal-weight tabs) — matches the button, NOT the histogram/log.

**Asked Alex which of the two clusters (true-center vs. +16dp) should be the reference — he chose true center** (x≈540px): simpler, and it's the button/nav that are "structurally correct" (symmetric layout) while the histogram/log's off-center position was an accident of unequal column widths nobody had compared against anything else before.

**Fix — widen the histogram/log's right margin to match the left, instead of moving the button or nav:**
- `DistribChart.kt`: `CHART_SC_PAD` 32dp → **64dp** (now equals `CHART_LABEL_WIDTH`). Used as right-padding on the histogram canvas, the axis-label canvas, and (via same-package visibility) `GeomMeanRow.kt`'s canvas and label row — all three shift together, automatically, since they all already keyed off this one constant.
- `SessionStatisticItem.kt`: the SC column's `Modifier.width(32.dp)` → **`64.dp`**, matching. Its text stays `TextAlign.End` (right-aligned) — the visible SC digits don't move at all, only the column's invisible left boundary does, which is exactly what shrinks the error-bar canvas before it and pulls its `cx` (the per-row "0") left to match.
- `BottomNavBar.kt`: the 16dp `contentOffsetX` hack from a few hours earlier was **fully reverted** (param removed from `NavTab`, the now-unused `offset` import removed too) — with the histogram/log now centered correctly, the nav's own natural `SpaceEvenly` center already lands in the same place, no per-tab trick needed.

**Verified by re-measuring all four points on a fresh screenshot after the fix** (same Python/PIL pixel-scan method): button tick x=539.5, `GeomMeanRow` center tick x=539.5, log-row zero-line x=540, nav gauge color-center x=539.5 — all agree within 1px. Also re-checked nav symmetry via `uiautomator` tab bounds: consecutive tab-center spacing is 211–212px across all 5 tabs, and Play/About (117/963) and Ranks/Settings (328/751) are each equidistant from the Analytics center (540) — confirms evenness AND symmetry, not just coaxiality.

### Files touched
`DistribChart.kt` (`CHART_SC_PAD` constant + doc comment), `SessionStatisticItem.kt` (SC column width + comment), `BottomNavBar.kt` (removed `contentOffsetX`/offset hack, restored plain `NavTab`).

### Known open / deferred
- This 64dp/64dp symmetric scheme is still only verified at one density (API 36.1 emulator, 420dpi) — should hold at other densities since everything's in dp, but not independently confirmed elsewhere.
- Quest task formulation change (image pending from Alex) — still not started, unrelated to this fix.

## What Was Done (23 August 2026, later — v13.32 → v13.33) — targets turn into the animated logo on hit

Alex: "мишени (слева вверху главного экрана), по мере выбивания, превращались в анимированные логотипы." A hit target used to just get a static green wash + a small check badge drawn in the same `Canvas` as the rest of the archery-metaphor graphics (`QuestProgress.kt`'s `TargetsRow`/`drawTarget`). Now a hit target's whole body becomes a live copy of the spinning brand-mark ([`AnimatedLogo.kt`](../android/app/src/main/java/ru/iflex/timemvp/ui_lib/AnimatedLogo.kt) — the same ring/triangle/"t" flip used on the About screen), with the green check badge kept as a small overlay in the corner so "hit" is still readable at a glance without waiting to notice the logo.

**Why this needed a structural change, not just a new draw call**: `AnimatedLogo` is a real `@Composable` (drives its rotation via `rememberInfiniteTransition`/`Animatable`, renders `Image`s with `graphicsLayer{ rotationY = ... }`) — it can't be invoked from inside a `DrawScope.drawTarget()` lambda, which is plain imperative Canvas drawing with no composition of its own. `TargetsRow` was a single `Canvas` drawing every target (hit or not) in one pass; rebuilt it as a `Box` with each target as its own positioned child (`Modifier.offset(x = stepDp * i)` per slot, replacing the old absolute-coordinate math inside one `DrawScope`) — un-hit targets stay small per-target `Canvas`es (same `drawTarget`, minus the now-dead `hit` branch), hit targets become `Box { AnimatedLogo(...); Canvas { drawHitBadge(...) } }` clipped to a circle. Draw/composition order is unchanged from before (un-hit first as background, hit ones after so a hit target's badge isn't clipped by an overlapping neighbor on ROW quests) — same visual stacking, just per-target `Box` children in a parent `Box` instead of one shared `Canvas`.

**Verified on-device, not just compiled**: built, installed, used precisely-timed `adb input tap` RUN/STOP pairs (empirically tuned to ~2.70s between taps to land inside the tenths-precision win window) to actually land a clean 3.0 on a live L1.4 (TWO_IN_ROW) quest. First target visibly swapped from the plain archery bullseye to the spinning logo + check badge the instant the win registered; a second screenshot ~1.2s later showed the triangle/letter in a visibly different rotation than the first — confirms the animation is actually running per-target, not a frozen image. Also checked `logcat` for crashes/ANRs after running 2 simultaneously-animating targets (only reached 1/2 landed before verification was called sufficient, not the full 2/2) — none, only pre-existing unrelated `AppOps` warnings.

### Files touched
`QuestProgress.kt` (`TargetsRow` rebuilt around `Box`+per-target children instead of one `Canvas`; `drawTarget()` lost its `hit` branch; new `drawHitBadge()` extracted from the old inline badge-drawing code).

### Known open / deferred
- Only checked with 1 of 2 targets hit (a live "1/2" progress state) — the fully-completed "2/2" case renders through the exact same code path (`landed == victoryCount` just means every target takes the hit branch), so it's not expected to behave differently, but wasn't independently screenshotted this session.
- Performance: a 3-target ROW quest at "2/3 landed" would run 2 simultaneous `AnimatedLogo` instances (each with a ring `Animatable` + up to 6 layered `rememberInfiniteTransition` rotations, per `AnimatedLogo.kt`'s own trail-ghost setup) continuously in view during normal gameplay, not just on a completion screen like the existing salute. Felt fine on the API 36.1 emulator; worth Alex's own eyes on a real low-end device if it ever feels janky.

## What Was Done (23 August 2026, later still — v13.33 → v13.34) — hit-badge removed

Same-day follow-up, immediately after the above. Alex: "зелёные чеки в кружочке надо убрать, всеравно полностью они не помещаются" — the small green check badge drawn in the corner of a hit target (kept as an overlay when the target's body became `AnimatedLogo`, see above) didn't fully fit inside the 32dp target circle and had to go.

**Fix**: deleted the `Canvas { drawHitBadge(...) }` overlay from the hit-target `Box` in `QuestProgress.kt`'s `TargetsRow` — a hit target is now bare `AnimatedLogo`, nothing drawn on top. Also deleted the now-dead `drawHitBadge()` function entirely (was only ever called from that one site). The logo itself already reads as "done" at a glance; no replacement indicator was requested or added.

**Verified on-device**: rebuilt, installed, landed a fresh clean-3.0 win (same ~2.70s-spaced timed-tap technique as the prior verification) and screenshotted the target row — confirmed the hit target is now a plain spinning logo with no badge, no clipping artifacts, un-hit target unaffected.

### Files touched
`QuestProgress.kt` (removed the badge overlay + `drawHitBadge()`).

## What Was Done (23 August 2026, evening — v13.34 → v13.35) — quest-title timing audit + IN_N gap-based Rule 2/3

Two separate asks from Alex in one message: (1) re-audit every quest-completion path for the "top-of-screen text must not change before the intermediate/congratulation screen" rule (established 13–14 August, supposedly already fixed everywhere), and (2) change how IN_N-indulged quests (TWO_IN_N/THREE_IN_N — attempt-count indulgence, not tolerance) decide whether a subquest win counts as the *original* quest: "the level is determined by the number of attempts **between** successful hits" — if all required hits land within a baseline-sized span of each other, the quest counts as done at baseline even if the window itself had been widened, and the cheer-up bubble should say so in its own paragraph.

### 1. Found and fixed a real regression: `onQuestWindowFailure()` updated the title immediately
Audited every completion path (HOORAY→GreetingView, Rule 3 subquest-stepback→RESET, session-auto-close→SessionSummaryOverlay, window-failure→FailCard) against live source, not against the 13–14 August changelog's claim that this was already handled everywhere — that claim turned out to be **wrong** for one path. `onQuestWindowFailure()` (called synchronously from `checkVictory()` the instant the window's Nth attempt fails, before the player has tapped anything) was setting `taskTitle`/`questAimStr`/`questToleranceStr`/`questToleranceMs`/`questIsSubquest`/`questSubStep`/`questWindowLimit` in the same `setState` as `awaitingFailResult = true` — i.e. the top-of-screen conditions flipped to the newly-indulged phase while the player was still looking at their failed attempt's colored result on the button, before tapping "OK" or seeing the FailCard. Every other path already deferred correctly.

**Fix**: removed those seven field updates from `onQuestWindowFailure()`'s `setState` — they were already being set (redundantly, just later) in the existing `awaitingFailResult` ON_STOP tap-handler (same pattern GreetingView.Close and the Rule-3 handler use). The underlying LocalStorage N/t values are unaffected — they were already bumped earlier in the same function; only the *displayed* moment moved.

### 2. IN_N Rule 2/3 — gap-based, not position-from-window-start
Old logic (`onQuestWindowSuccess()`) judged "was this win baseline-quality" by `lastWinIndex ≤ baseN` — the position of the *final* win counted from the start of the (possibly widened) window. Alex's correction: a player who happens to land all required hits close together — say, hit #1 at window-attempt 35 and hit #2 (final) at window-attempt 66, under a 66-attempt indulged window — has, purely by luck, actually satisfied the *original* 33-attempt condition (any 33-attempt slice containing both hits would have worked), even though `lastWinIndex=66` fails the old `≤33` check outright. The old rule only asked "how far is the last win from window start"; the right question is "how far apart are the wins from each other."

**New tracking**: `LocalStorageService` gained `questWindowFirstWinIndex` (paired with the existing `questWindowLastWinIndex`) — set once per window on the *first* win (not overwritten by later wins, not reset by misses in between, since IN_N doesn't require consecutive hits — only reset when the window itself resets). `MainViewModel` computes `winSpan = lastWinIndex − firstWinIndex + 1` in `onQuestWindowSuccess()` and uses `winSpan` (not `lastWinIndex`) in both:
- **Rule 2** (`baselineQualityN`): `winSpan in 1..baseN` → jump straight to baseline + advance, even though the actual window was wider.
- **Rule 3** (step-back sizing): `overBase = winSpan − baseN` instead of `lastWinIndex − baseN` — the "how many indulgence steps over baseline" computation now reflects the real gap, not raw position.

Restore-on-init (`MainViewModel.init{}`) and both window-reset sites (`onQuestWindowSuccess`, `onQuestWindowFailure`) got matching `firstWinIndex` read/write/reset logic, mirroring the existing `lastWinIndex` handling.

### 3. Cheer-up bubble gets a dedicated paragraph for the gap-based win
New `MainContract.State.pendingCheerUpInNGapNote: Boolean`, set in `checkVictory()` exactly when `!wasAtBaselineBeforeWin && currentQuest.adaptiveN` (i.e. Rule 2 fired *and* it's an IN_N-type quest — ROW quests never have `adaptiveN`, so this can't misfire on a tolerance-based Rule 2). Threaded through `composeBubbleText()` → `CheerUpMessages.specificText(..., inNGapNote: Boolean)`, which appends, only when true: *"Worth noting: the hits landed close enough together to count on the original pace anyway — cleared it without even needing the extra room."* Plain English, no "window"/"attempt limit" jargon, per the established cheer-up vocabulary rule.

### Verification — mixed: some direct, some code-review-only
Built, installed, played extensively on-device (100+ real timed attempts across several quest transitions on L1.2/L1.3, precisely-timed `adb` taps for wins, rapid batches for misses) rather than trusting compilation alone:
- **Confirmed live**: a real Rule-3 stepback fired correctly using the new `winSpan` variable (span=37 > baseN=33 → stepped back to exactly baseline N=33, no crash, no advance) — proves the new tracking fields are wired correctly end-to-end (write on win, read/reset in `onQuestWindowSuccess`, survive app restore).
- **Confirmed live (negative control)**: a genuine Rule-1 baseline completion (quest never left N=33 in its final window) correctly did **not** show the new gap-note paragraph in the cheer-up bubble — confirms `pendingCheerUpInNGapNote` isn't spuriously true outside the Rule-2-IN_N case.
- **Confirmed live**: the GreetingView title-deferral pattern (unrelated to this session's fix, but re-exercised organically) held correctly — top-of-screen conditions stayed on the just-completed quest until "TAKE NEXT QUEST" was tapped, then updated.
- **Not directly captured on-device**: the specific Rule-2-fires-via-gap positive case (`inNGapNote=true`, the new paragraph actually rendering) and a freeze-frame of the `onQuestWindowFailure` fix at the exact failure instant — both attempts were cut short by the emulator becoming increasingly unresponsive after a very long session (multiple unrelated-system-app ANRs, then `adb input tap` itself started landing unreliably). Chose to stop rather than keep fighting emulator flakiness; relying on code review for these two specific cases — the formula was hand-verified against Alex's own numeric example (66-window, gap ≤31 between hits → counts as baseline) before writing it, and the paragraph logic is a simple, isolated boolean-gated string append.
- `logcat` checked for crashes across the entire test session (100+ attempts, several quest/level transitions, two prefs-based state probes that were reverted): none — only pre-existing unrelated `AppOps` warnings.

### A testing note for next time: don't hand-edit SharedPreferences/DB on this device
Tried to shortcut reaching a specific IN_N window state by writing `shared_prefs/Application Preferences.xml` directly via `run-as` (force-stop, edit, relaunch) — worked for one probe, but a second attempt to also inspect the Room DB (`time_database.db` + `-wal`/`-shm`) for a cleaner injection hit `database disk image is malformed` when opened locally (WAL files pulled mid-flight don't reassemble reliably outside the device). Backed out before writing anything back — the on-device DB itself was never touched, only read attempts failed. Real, extensive on-device play (as done for the rest of this verification) is the safer way to exercise this logic; direct prefs editing is workable for quick single-field probes but the DB should not be hand-edited this way again without a real checkpoint step first.

### Files touched
`MainViewModel.kt` (`onQuestWindowFailure` title-field removal, `onQuestWindowSuccess` winSpan logic, `checkVictory` inNGapNote flag + win-tracking, init-restore firstWinIndex, `composeBubbleText` param threading), `MainContract.kt` (`pendingCheerUpInNGapNote` field), `LocalStorageService.kt` (`questWindowFirstWinIndex` pref + reset-on-erase), `CheerUpMessages.kt` (`inNGapNote` param + paragraph text).

### Known open / deferred
- The Rule-2-gap positive case and the exact fail-moment freeze-frame are code-review-verified only, not screenshotted live — worth a quick real confirmation next session (fresh emulator boot, not this same long-running instance) if it matters before shipping.
- This generalizes to THREE_IN_N quests too (span from 1st to 3rd/last win ≤ baseN) — the formula is win-count-agnostic (`lastWinIndex − firstWinIndex + 1`), but only the TWO_IN_N boundary case (2 wins) was actually exercised; THREE_IN_N's 3-win span was attempted but not reached before the emulator became unreliable.

## What Was Done (25 August 2026 — v13.36 → v13.39)

Two small, unrelated requests in one short session: matched the stopwatch button's progress-bar width to the histogram's actual plot width, and shrank the APK by re-encoding the salute/ambient sound clips.

### Progress-bar width now matches the histogram (`StopWatchButton.kt`, `MainScreen.kt`)
Alex: "ширина прогрессбара должна быть равна ширине гистограммы частотного распределения." The bar's width was previously derived from the digit-block width of the "0.000" time text (70sp monospace bold) — visually much narrower than the histogram below it, since the histogram's plot area is inset by `CHART_LABEL_WIDTH` (64dp) on the left and `CHART_SC_PAD` (64dp) on the right (the same coaxiality margins fixed 2026-08-23).

`StopWatchButton` gained a new optional `progressBarWidth: Dp? = null` param — when supplied, it overrides the digit-derived width entirely; `null` keeps the old behavior (used only as a fallback now). `MainScreen` computes the real histogram plot width from the already-tracked `chartBounds` (the same `Rect` used for the falling-drop animation geometry): `chartBounds.width − 24dp (DistribChart's own horizontal padding) − CHART_LABEL_WIDTH − CHART_SC_PAD`, and passes it in. Falls back to the old digit-width behavior when `chartBounds` is still null (e.g. no attempts recorded yet for the current period, so the histogram section hasn't rendered). Build-verified only this session — not confirmed visually on-device (Alex declined on-emulator verification for this change).

### APK size — salute/ambient WAV clips re-encoded to lossless FLAC
Alex noticed the debug APK had grown from a remembered ~15MB to 23MB and asked why. Measured the actual contributors by unzipping and sorting by size: the two biggest were `org.apache.poi`/`poi-ooxml` 5.2.3 (added 4–5 July for the Excel-export feature — its dependency tree pushes the app to 4 multidex `classes*.dex` files, ~34MB of that alone) and the uncompressed `salute_clip_*`/`ambient_clip_*` WAV clips added 16 August for the salute celebration (7.1MB raw) — the latter had been explicitly flagged as a known deferred APK-size concern in that day's changelog entry ("no ffmpeg available on this machine to re-encode... worth revisiting").

Alex asked to try fixing the WAV-clip issue only, explicitly "не в ущерб качеству. без фанатизма" (no quality loss, don't go overboard) — so POI wasn't touched this session, and lossy re-encoding (MP3/OGG at a bitrate) was ruled out in favor of a **lossless** codec. Still no `ffmpeg` on this machine, but `pip install soundfile` (wraps `libsndfile`, which ships with a built-in FLAC encoder) worked, so no need to find/build ffmpeg. All 18 clips (44.1kHz/stereo/16-bit PCM) were converted `.wav → .flac` and the round-trip was verified **bit-exact** (`numpy.array_equal` on the decoded PCM samples, not just "sounds the same") before deleting the original WAVs — genuinely lossless, not a quality tradeoff. Total: 7.05MB → 2.52MB (36% of original, saved 4.53MB).

No code changes were needed for this swap: `SaluteCelebration.kt` references clips by generated resource id (`R.raw.salute_clip_0`, etc.), which Android derives from the file's basename without extension — renaming `salute_clip_0.wav` → `salute_clip_0.flac` keeps the same `R.raw.salute_clip_0` identifier. Android's built-in FLAC extractor/decoder (available since API 21, well under this project's `minSdk = 27`) should decode these the same way `SoundPool` already decodes any other format — not independently confirmed on-device this session per the standing "ask before touching the emulator" rule ([[feedback_emulator_permission]]), only build-verified (APK assembled successfully, FLAC files confirmed present inside the built APK via `unzip -l`).

Debug APK: 23MB → **18MB** (saved 5MB net — the histogram/log-row rendering and multidex/POI cost were untouched, so the full 4.53MB raw saving didn't all show up 1:1 in the final zip, since WAV already compresses somewhat under the APK's own deflate pass).

### Files touched
`StopWatchButton.kt` (`progressBarWidth` param), `MainScreen.kt` (histogram-width computation + wiring), `res/raw/salute_clip_{0-8}.flac` + `ambient_clip_{0-8}.flac` (replacing the `.wav` originals, deleted).

### Known open / deferred
- Neither change was confirmed visually/audibly on-device this session (Alex declined emulator use for the progress-bar width; the FLAC swap wasn't asked to be device-tested) — both are build-verified only. Worth a quick on-device look next session that has emulator permission: confirm the bar visually lines up with the histogram, and confirm the salute/ambient clips still play correctly (no silent failure from an unexpected codec issue on a specific device).
- POI's own contribution to APK size (~15-18MB across the 4 dex files) was measured and reported but explicitly NOT touched this session — Alex asked for the WAV fix only. If APK size matters again, that's the next lever (e.g. `poi-ooxml-lite` instead of full `poi-ooxml`, or a hand-rolled minimal `.xlsx` writer to drop the POI dependency entirely).

### Systemic code-optimization pass (same day, follow-up request)
Alex: "оптимизируй чтобы минимизировать количество проблем, сделать быстрым и легким" — asked for a system-level look at the codebase, not another single-issue fix. Scoped this as: (1) do the safe, zero-risk cleanup directly, (2) surface the two real systemic issues found with concrete evidence rather than guessing, and ask before touching anything that carries real behavior-change risk — this codebase's own changelog is full of hard-won, narrowly-scoped bug fixes (quest state machine especially), so a blind broad refactor is more likely to reintroduce a regression than to help.

**Dead code removed (safe, zero behavior change, build- and unit-test-verified):**
- `ThemeViewModel` class in `Theme.kt` (`ui_theme/`) — a completely unused, never-instantiated, never-Koin-registered light/dark toggle scaffold; the app's *actual* theme switching (`themeMode`/`userThemeMode`/`themeAsSystem`) is a separate mechanism that was already live and untouched. Removed the class + 3 now-unused imports (`State`, `mutableStateOf`, `ViewModel`).
- `getDate()`, `formatDate()`, `getHours()` in `core/utils/Utils.kt` — zero call sites anywhere in the codebase (verified via a full-codebase reference count, not just a local grep). Removed along with 6 now-unused imports (`SimpleDateFormat`, `LocalDate`, `DateTimeFormatter`, `Calendar` — this last one was *already* dead before today, unrelated to these three functions — `Date`, `TimeZone`).
- 3 unused drawables: `ic_about_logo` (all 5 densities — superseded by `AnimatedLogo.kt`, confirmed dead 15 August but the old files were never deleted), `ic_export` (dead since the Export tab was folded into Settings 22 August), `ic_win` (dead since `SaluteCelebration` replaced the static pulsing icon 16 August). ~116KB.
- Method: for the Kotlin dead-code sweep, wrote a one-off Python script that finds every top-level/private `fun` definition across all 99 `.kt` files and counts total occurrences of its name in the combined source — a count of 1 (only its own `fun` line) means zero call sites. Cross-checked the handful of hits against Room `@TypeConverter` functions and `@Preview` composables (both legitimately low-reference-count but NOT dead — Room calls converters via reflection, Studio calls `@Preview` functions via its own tooling) before removing anything.
- Verified via `./gradlew assembleDebug` (clean build, no errors) and `./gradlew testDebugUnitTest` (both `QuestListInvariantsTest` and `CalculateScmUseCaseTest` — the project's only two real unit-test files — still pass). APK size effect negligible as expected (~18MB either way — this pass was about removing dead weight from the source tree, not shrinking the binary).

**The real "systemic" finding — R8/minify on the debug build no longer shrinks the thing that actually needs shrinking:**
`app/build.gradle.kts`'s debug `buildType` has had `isMinifyEnabled = true` since 2026-07-14, with a comment explicitly recording the tradeoff Alex accepted at the time: *"Стоимость: debug-сборка ~6 мин вместо 30 сек. Выигрыш: 23 → ~10 MB."* That tradeoff no longer holds. Reading `proguard-rules.pro` line by line: POI/xmlbeans/openxmlformats/commons/etsi/w3/microsoft-schemas are all under a **blanket** `-keep class org.apache.poi.** { *; }` (and five siblings) — meaning R8 cannot remove a single class from any of those packages, full stop, regardless of debug or release. This isn't a guess; the rule reads exactly that way. The file's own comment explains why: a narrower rule was tried once and caused a real production bug (log4j's static initializer breaking mid-Excel-export, "баг v12.33–v12.37") — so the blanket keep is a deliberate, hard-won safety net, not an oversight.

Since POI is *also* the single biggest size contributor (measured this session: ~34MB of the ~46MB uncompressed `classes*.dex` total, per the WAV-size investigation earlier today), and R8 cannot touch any of it either way, **debug-build minification is now paying its full ~10-minute cost for a shrink that only applies to the smaller, non-POI part of the app** — nowhere near the "23 → 10MB" this project's own app-codebase comment promised when it was written (before the POI keep-rules existed in their current blanket form). Every `assembleDebug` this session took 9-10.5 minutes; disabling `isMinifyEnabled`/`isShrinkResources` on the `debug` buildType specifically (leaving `release` completely untouched — same POI protection, same shrink behavior, since that's what would actually ship to Play) should cut Alex's own build-and-verify loop to roughly the ~30 seconds the original comment cites, at the cost of a debug APK that's some amount bigger than today's 18MB (not measured — would need one comparison build to quantify, not done this session since it's Alex's call whether to make the change at all, not just how big the delta is).

**Deliberately NOT done this session** (flagged, not touched, since each carries real behavior-change or regression risk on a fragile part of the app):
- Did not flip the debug-minify setting — this reverses a decision Alex made and left an explicit comment about; asked instead of silently overriding.
- Did not touch POI's dependency footprint itself (`poi-ooxml-lite`, hand-rolled `.xlsx` writer) — same as the WAV-only session earlier today, this is a separate, much bigger lever that changes real export behavior and needs its own dedicated pass + on-device Excel-export testing.
- Did not attempt to decompose `MainViewModel.kt` (2252 lines) or `StatScreen.kt` (2140 lines) into smaller pieces, despite these being the clearest "god file" architecture smell in the codebase (a single `MainViewModel` holds the entire quest state machine, all the `awaiting*` boolean flags, cheer-up dispatch, and session bookkeeping in one file) — this is exactly the part of the app with the longest history of subtle, hard-to-reproduce regressions (see essentially every "What Was Done" entry above touching quest logic), and a structural refactor of it without the same kind of extensive on-device verification those sessions used would be reckless. Flagged as a real, valuable, but *large and risky* future undertaking, not started.
- Did not touch the already-diagnosed-but-unfixed root cause behind the "Numbering-баг между сессиями" defensive patches (`updateSession` overwriting `isQuestComplete=false`, see 23 August and earlier entries) — a real, scoped fix exists on paper (make `markSessionQuestComplete` synchronous before `updateSession`, or set `isQuestComplete=true` on `state.currentSession` before the write), but it touches the same fragile quest/session state machine and deserves its own dedicated verification pass, not a drive-by fix bundled into a general cleanup.

### Follow-up: minify-on-debug — decision made, and the predicted "~30s" was wrong
Alex chose to disable it. Measured, rather than assumed, the actual before/after this time:
- **Build time**: 8m36s with minify off vs. ~9-10.5min with it on this session — only ~15% faster, nowhere near the "~10min → ~30s" the 14 July comment promised and this session's own report initially predicted from that comment.
- **APK size**: 26MB with minify off vs. 18MB with it on — **+8MB**, bigger than expected.
- **Root cause of the wrong prediction**: R8's `minifyDebugWithR8` task shrinks AND dexes POI in one combined, relatively efficient pass. Without R8, AGP falls back to the standard per-library `dexBuilderDebug` (D8) task, which has to dex *every* class in POI's jars individually with no shrink filter first — POI alone is ~5000+ classes, and dexing all of them unfiltered turned out to cost nearly as much wall-clock time as R8's shrink+dex combined pass did. The 14 July "~30s" number almost certainly predates POI being added to the project (4-5 July) becoming the dominant cost driver it is today — nobody re-measured it since, including this session's own report before the second build.
- Reported the real numbers plainly to Alex rather than let the earlier (wrong) prediction stand; he confirmed keeping minify off despite the smaller-than-promised time win, since it's still faster and removes the residual R8 "assumed unreachable" mis-shrink risk on debug (see the WARNING about `SVGUserAgent.getViewbox()` in the build log). `isMinifyEnabled = false` / `isShrinkResources = false` on `debug` only — `release` untouched.
- Verified via `./gradlew testDebugUnitTest` after the change (both unit tests still pass) — this wasn't skipped just because the change looked "safer."

### Files touched (optimization pass)
`Theme.kt` (removed `ThemeViewModel` + imports), `Utils.kt` (removed `getDate`/`formatDate`/`getHours` + imports), `res/drawable*/ic_about_logo.png` (5 densities, deleted), `res/drawable/ic_export.xml` + `ic_win.xml` (deleted), `app/build.gradle.kts` (`debug` buildType: `isMinifyEnabled`/`isShrinkResources` → `false`, proguardFiles removed from that block, comment rewritten with the measured numbers).

### Known open / deferred
- Debug APK is now **26MB**, up from 18MB — this is the accepted tradeoff for faster (if less dramatically so than hoped) iteration; don't be surprised by the jump if comparing sizes across sessions.
- `MainViewModel.kt`/`StatScreen.kt` decomposition and the `isQuestComplete` root-cause fix are both real, identified opportunities to "minimize the number of problems" long-term, but are large/risky enough that they need their own dedicated, carefully-verified session — not bundled into this cleanup pass.

## What Was Done (26 August – 1 September 2026 — v13.36 → v13.53)

Long stretch covering: a full iterative redesign of the main-screen quest-info panel (`QuestProgress.kt`) driven by Alex's own reference sketches, a real reactivity bug fix in the bottom-nav balance gauge, replacing the cheer-up bubble with a lighter fade-highlight, a Google-Play launch-checklist breakdown (published as an artifact + a legal brief handed to a lawyer), and — this session — a manifest permission cleanup plus a no-op telemetry scaffold (Analytics/Crash reporter interfaces) so Firebase (or a self-hosted alternative) can be dropped in later without touching call sites.

### QuestProgress — multi-round redesign to match Alex's sketches (`QuestProgress.kt`, `MainScreen.kt`)
Series of small, confirmed-one-at-a-time changes (per the established row-by-row workflow), each a direct response to a reference screenshot or correction:
- Arrow-bundle visualization **removed entirely**, replaced by `AttemptPipsRow` — one dot per attempt in the current window, colored by result (green/red/blue), hollow rings for unplayed slots. Multi-row staggered/brick layout (`PIPS_COLS = 33`, alternating half-cell offset per row) for indulged windows (66/99).
- Mechanic label restored under the targets (`"N× in a row"` / `"N× in total"`), bright not muted (bold `N×` + 0.85-alpha suffix).
- App-wide alpha brightening pass (regex-scripted, 0.30–0.85 range only, +0.08 capped at 0.90) — deliberately left borders/decorative alpha untouched.
- Quest-info block reduced to exactly 3 elements (aim, targets, characteristic label) — dropped the old "Level N · quest Y of 5" / "left N/M" text lines from that cluster.
- New two-line "Level x" / "Quest y.z[.step]" block reintroduced to the **left**, independent of the centered aim+targets+label cluster; quest notation is `x.x.x` (level.task.subquest-step) — third segment only present when actually in a subquest.
- Aim-number font shrunk to match the label's size; whole cluster centered.
- **Final step — true coaxial centering**: the aim+targets+label cluster's center (and, for ROW/multi-target quests, the geometric center across targets) now sits on the exact same vertical axis as the stopwatch button's "3.0" tick, the log's per-row error bar, and the histogram — the same axis established 23 August. Root cause of the earlier misalignment: the "?" help icon lived as a `Row(SpaceBetween)` sibling next to `QuestProgress`, an asymmetric-width column that shifted the apparent center left, identical in kind to the bottom-nav/histogram bug fixed 23 August. Fixed the same way — not with a manual offset, but by removing the asymmetry: `QuestProgress` now takes a `helpIcon: @Composable () -> Unit` slot parameter and renders it *inside* its own already-centered `Box`, at `CenterEnd`, below the pips row (so it can't collide with the rightmost pip) — `MainScreen.kt`'s outer `Row(SpaceBetween)` wrapper is gone, `QuestProgress` gets the full panel width like the histogram and the button do.

### Balance gauge — real reactivity bug fixed (`LocalStorageService.kt`, `BottomNavBar.kt`)
Alex: "индикатор analytics странно дёргается и врёт, должен обновляться после каждой попытки." Root cause: the bottom-nav balance gauge read `getBalanceRed()`/`getBalanceBlue()` as plain synchronous SharedPreferences getters — no Compose observation, so the gauge only repainted when *something else* happened to recompose `BottomNavBar` for an unrelated reason, not on every `putBalance()`. Fixed: `LocalStorageService` gained `private val _balanceFlow = MutableStateFlow(...)` / `val balanceFlow: StateFlow<Pair<Int,Int>>`, updated inside `putBalance()` itself; `BottomNavBar` now does `val balance by storage.balanceFlow.collectAsState()`. Gauge now updates synchronously with every attempt, on every screen.

### Cheer-up bubble → fade-highlight (`MainScreen.kt`)
Alex: the GreetingView (congrats/salute/stats screen) stays exactly as-is; the amber cheer-up *bubble* (the popup at the top of the main screen) goes away — but its code (`CheerUpBubble.kt`, `CheerUpMessages.kt`) is NOT to be deleted, just disabled, in case it's wanted back. Replaced with a simple amber glow (`Color(0xFFF5C02E).copy(alpha=0.14f)` rounded box) over the quest-info area, shown via `AnimatedVisibility` whenever `cheerUpTrigger != NONE`, and faded out the moment the player starts their first attempt of the new session (`stopWatchState == IS_RUNNING && attempts.isEmpty()`) — same dismiss-on-first-RUN timing the old bubble used, just a plain fade instead of the old deflate/blur/CTA machinery. The "?" icon is no longer dimmed/disabled while the highlight is showing (it used to be gated off while the bubble was up).

### Launch checklist — published + lawyer brief handed off
Broke the generic Google-Play pre-launch checklist into 9 owner-tagged blocks (mine vs. Alex-only), grounded against what's actually in the repo (no Firebase/Crashlytics yet, no monetization, domain/leaderboard already live so Privacy Policy is needed *now* not "eventually"). iOS explicitly descoped per Alex ("веб-версия закрывает не-Android"). Published as an artifact ("Карта запуска"). Wrote a technical (not legal) brief for a lawyer covering what the app actually collects, handed off as a separate artifact — the webapp/Cowork session then drafted Privacy Policy / Terms of Use / a Data Safety guide from that brief; those drafts contain unconfirmed assumptions (EU/US audience framing, GDPR/CCPA/COPPA, RF governing law) that still need Alex's fact-check before anything is finalized or published.

### 1 September — permission cleanup + no-op telemetry scaffold
Two small, verified, zero-risk pieces of forward progress on the checklist's "store policy compliance" and "analytics & crash reporting" blocks, doable without any account/credential from Alex:

**Manifest cleanup** (`AndroidManifest.xml`): `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` (`maxSdkVersion=28`) removed — confirmed via a full-codebase grep that nothing references them; they were a dead leftover from the Excel-import-from-external-storage feature removed back in v11.36. Manifest now declares only `INTERNET` (leaderboard sync) and `VIBRATE` — the minimal, clean set the launch-checklist artifact already claimed was there (it wasn't, quite — now it is).

**Telemetry scaffold** (new `core/telemetry/AnalyticsReporter.kt` + `CrashReporter.kt`): two small interfaces (`logEvent(name, params)`; `recordException(t)`/`log(message)`) with `NoOp*` default implementations, bound in `KoinModule.kt`. The idea: swap ONE Koin binding for a Firebase- (or self-hosted-) backed implementation later, with zero call-site changes needed anywhere else. Wired call sites at the three points the launch-checklist promised ("старт сессии, завершение квеста/уровня, экспорт"):
- `MainViewModel.kt` — `session_start` fired inside the existing async `pendingAddSession` coroutine (NOT on the synchronous button-press path — this app has a long, hard-won history of stopwatch-button latency work, so nothing new goes on that hot path); `quest_complete`/`level_complete`/`game_complete` fired in `checkVictory()` right where `pendingTrigger`/`completionStats` are already computed, reusing that same data.
- `ExcelShareUtil.kt` — `export_excel` on successful share-intent launch; `crashReporter.recordException(e)` added to the existing `onFailure` branch (was already logging to logcat + toast, now also reaches the crash-reporter hook).
- `MainApplication.kt` — wraps `Thread.setDefaultUncaughtExceptionHandler`: records through `CrashReporter` then always delegates to the previous handler, so today's no-op behavior is byte-for-byte identical to before — the hook just exists now, ready for a real backend.

Build-verified (`assembleDebug` succeeded, APK installed nowhere new — not device-tested this session since it's inert by construction: `NoOp*` bindings do nothing observable).

### Files touched
`AndroidManifest.xml`, `core/telemetry/AnalyticsReporter.kt` (new), `core/telemetry/CrashReporter.kt` (new), `core/KoinModule.kt`, `MainApplication.kt`, `features/main/ui/MainViewModel.kt`, `ui_lib/ExcelShareUtil.kt`, `features/main/ui/QuestProgress.kt`, `features/main/ui/MainScreen.kt`, `core/data/LocalStorageService.kt`, `ui_lib/BottomNavBar.kt`.

### Collaboration note
Discussed (not built) a "check server for newer version, prompt update" feature — conclusion: solved automatically by Google Play's own update mechanism once published there (optionally with the in-app-updates API for a flexible/immediate prompt), not worth a custom sideload-only mechanism given Play is the actual next milestone. Parked, no code written.

### Known open / deferred
- Telemetry scaffold is genuinely inert (`NoOp*`) until Alex either creates a Firebase project (`google-services.json`) or says "self-hosted" (Sentry + the existing `/api`) — see launch-checklist block 02.
- FAQ content for playoftime.online was drafted (see scratchpad `faq_draft.md` from this session) but **not** placed on the live site — the webapp is maintained by a separate Cowork session that edits the *live* server directly (Sprutio) and mirrors down to local files afterward; editing the local `webapp/index.html` copy directly here risked being silently overwritten on their next sync. Content needs to be handed to Alex or that session, not pasted in from here.
- The Privacy Policy/Terms of Use jurisdiction assumptions (from the block above) are still unconfirmed — nothing should be treated as final/publishable until Alex answers.
- QuestProgress's final coaxial-centering fix is build-verified and reasoned through the same Compose-alignment mechanics as the 23 August coaxiality fix, but has not been re-confirmed with a fresh on-device pixel-scan (no emulator session run since).

## What Was Done (9 September 2026 — v13.61 → v13.70)

Every change this session was verified live on the `Medium_Phone_API_36.1` emulator (build → `adb install` → launch → screenshot, several rounds pixel-measured) rather than reasoned through alone — established as the working method after a separate side-session shipped a fix without checking it first and it turned out broken.

### Week as a real entity in the Sessions tree (Analytics)
`PeriodFilter.WEEK` (the rolling 7-day filter) already existed and was already wired into the Analytics period pill — verified genuinely live, not just referenced in scattered code. What was actually missing, per Alex's own framing ("не забудь отразить в дереве попыток"): Week had no place in the *Sessions tree* hierarchy (Year → Month → Day, no Week level).

- New `SessionWeekNode` (`StatScreen.kt`) sits between Month and Day: `buildTree()` groups each month's already-built day nodes by `Calendar.WEEK_OF_YEAR`, label = numeric day-range (`"1–7"`) since the month name is already shown on the parent row. Weeks that straddle a month boundary land in two separate nodes (one per month) — intentional, weeks are scoped to the month they're grouped under, not global.
- New `TreeWeekRow` composable (indent 18dp, between Month's 12dp and Day's 24dp), reusing the shared `TreeRow` — same lucky/mean/att4win columns as every other level.
- `expandedWeeks: Set<String>` + `Event.ToggleWeek` threaded through `StatsContract` → `StatsViewModel` → `StatScreen`, mirroring the existing Year/Month/Day pattern exactly.
- The auto-expand-to-most-recent-session path (`StatsViewModel.loadAnalytics`) now also computes and expands the correct week key — otherwise opening Analytics would land one level short of the latest session after this change.

### Analytics "?" → "×" help-icon toggle
The main screen's "?" circle already flips to "×" while help mode is active (22 August fix) — Analytics's own "?" (`StatScreen.kt`'s `PeriodPill`) never got the same treatment and always showed "?" regardless of state. One-line fix: `text = if (helpActive) "×" else "?"`, same pattern as `MainScreen.kt`.

### Main screen — deliberate rightward shift of histogram/log/progress-bar/targets
Alex: "какая-то странная пустота" — the 22–23 August coaxial-centering work (histogram, log zero-line, progress bar, and the bottom-nav Analytics icon all sharing the screen's true geometric center) made the right-side margin symmetric with the left, but the *left* column is always visually dense (numbers/time, never empty) while the *right* column (SC values) usually isn't — so the "equal" margins never read as equal. Asked to stretch the plot rightward and shift its center right on purpose, reversing that symmetry.

- `DistribChart.kt`'s `CHART_SC_PAD` (right-side inset, previously 64dp to match `CHART_LABEL_WIDTH` for true-center coaxiality) is now smaller than the left — first tried 12dp, live on-device that read as "too far" (Alex: "слишком сильно подвинул"), landed on **32dp** (the column's own original pre-22-August width) after showing a before/after sketch and iterating live. Net center shift = `(CHART_LABEL_WIDTH − CHART_SC_PAD) / 2` = 16dp.
- `StopWatchButton.kt` gained `progressBarOffsetX: Dp` (new param, applied via `Modifier.offset` on `SecondsProgressBar` only — the round button graphic itself is a sibling in the same centered `Column` and does **not** move) so the progress bar tracks the same rightward shift as the histogram instead of just matching its *width* (matching width alone, in a screen-centered column, doesn't reproduce an asymmetric *position*).
- `QuestProgress.kt`'s `TargetsCenteredRow` (the archer targets — Alex's "мишени", initially missed in the first pass and flagged: "я так и подумал что ты про мишени забудешь") gets the identical shift, added directly to its true-center layout formula.
- `SessionStatisticItem.kt`'s SC column now reads `CHART_SC_PAD` instead of a separate hardcoded `64.dp` literal, so the two can't drift apart again.
- Verified via `adb shell screencap` + pixel measurement (yellow-dot centers of the three targets, the histogram's "0" tick, the log rows' zero-line) — all land within ~3px of each other, confirming histogram/log/progress-bar/targets are still mutually coaxial, just shifted together; the round button's own center stayed at the screen's true center throughout.
- **Known, accepted side-effect, not fixed**: the bottom-nav Analytics icon (aligned to the *old* true center back in the 23 August coaxiality project) is no longer coaxial with the histogram/log/targets group. Not in scope for this ask — flagged for a future pass if it turns out to matter.

### Twin/neighbor-shot log-row outline — legibility fix
The hand-drawn freehand oval marking twin-shot/neighbor-shot pairs in the attempt log (green/yellow, `SessionStatisticItem.kt`) was read by Alex as "covering" the number/time digits inside it. Root-caused by measuring, not guessing: the oval is genuinely drawn via `Modifier.drawBehind` on the row (which paints strictly before the row's own children — the digits are architecturally already on top, this was never a z-order bug), but a fully-opaque, saturated stroke sitting directly behind small 9–11sp monospace glyphs still reads as visual noise competing with the text. Fixed by reducing only the stroke's alpha (color/hue untouched, per Alex's explicit "яркость вернём, только opacity"), iterated live on-device across four builds: 1.0 (original) → 0.45 → 0.30 (still "too strong") → **0.05** (settled — barely a trace, digits fully legible).

### Files touched
`StatScreen.kt` (SessionWeekNode/TreeWeekRow/buildTree, "?"/"×" toggle, period-context caption/chevrons from the prior stretch of this same day — see below), `StatsContract.kt`, `StatsViewModel.kt`, `DistribChart.kt` (`CHART_SC_PAD`), `StopWatchButton.kt` (`progressBarOffsetX`), `MainScreen.kt` (offset wiring, stale-comment cleanup), `QuestProgress.kt` (`TargetsCenteredRow` shift), `SessionStatisticItem.kt` (SC column references `CHART_SC_PAD`, twin/neighbor oval alpha).

*(Earlier the same day, not yet logged before this entry: `PeriodFilter.rangeMsForOffset()` + a new `getAttemptsInRange` DAO query + `AnalyticsUseCase`/`AnalyticsData` gained a full period-offset ("page back/forward a day/week/month/session") feature, with `‹›` chevrons flanking the period-context caption on Analytics — caption also enlarged/centered. And `PeriodFilter.label`'s `TODAY` entry was renamed `"Today"` → `"Day"` — confirmed via grep that `.label` is read only on the Analytics pill, not the main screen, so this doesn't touch main-screen text anywhere.)*

### Known open / deferred
- Bottom-nav Analytics icon coaxiality with the now-shifted histogram/log/targets group — not addressed, see above.
- The period-offset paging feature (day/week/month/session back-forward) was build-verified but not re-walked live on-device after this session's later layout-shift changes landed on top of it — worth a quick sanity pass together next time either area is touched.

## What Was Done (11 September 2026 — v13.81 → v13.82)

Whole session was one continuous arc: the personal-report feature discussed the day before (2026-09-10, see the Narrative Feature Decision Log artifact D29) went from "opens in the system browser" to "opens in-app via WebView," plus two real formula bugs found and fixed on the leaderboard, plus two more report-page requests (a sample-size disclaimer, and enforcing the level-boundary rule at the UI level, not just in the data). Everything server-side is written and locally verified but **not yet deployed** — this session has no server access; see `webapp/handoff/*.md` for the exact upload list.

### Leaderboard — Lucky Index and Hit Rate now match the native Analytics screen

Alex: "лаки индекс в лидборде не совпадает со значением в аналитикс. исправь. проверь так же остальные параметры." Two real, independent formula mismatches, both server-side (`webapp/api/leaderboard.php` + a new helper in `webapp/api/_lib.php`), **no native changes needed** (both fields are just displayed as-is from the JSON):

1. **Lucky Index** — was sizing the "outcome slots" denominator from the raw, untrimmed `MIN(error_ms)`/`MAX(error_ms)` across a player's whole history (a known, explicitly-flagged simplification in the old code's own comment). Native's `AnalyticsUseCase.kt` instead trims to the 3rd/97th percentile of signed errors before sizing the range — the same rule already unified across the other two native call sites back on 13-14 August, but the server was the one holdout. Fixed: `leaderboard.php` now pulls every attempt's `error_ms` per player (scoped to `current_epoch`) and computes the same P3/P97 index math in PHP (`(int)($n*0.03)`/`(int)($n*0.97)`, clamped to `[0,n-1]` — PHP's `(int)` truncates toward zero the same way Kotlin's `.toInt()` does).
2. **Hit Rate ("Acc%")** — was `winsTotal/attemptsTotal*100`, the raw `is_win` DB column, which includes every tolerance-eased subquest win across a player's whole history. Native's actual "Hit rate" (`CHANCE OF WIN` card) is a different, stricter number — `trueThreeHitRatePct` via `countTrueThree()`: each attempt's duration rounded to *its own* accuracy grid (deci=100ms/centi=10ms/milli=1ms), checked against `tolerance_ms` (+ a resolution floor that's a permanent no-op today). Ported faithfully as `is_true_three_win()` in `_lib.php`.
3. **Other columns checked, left alone**: `scTotal`/`levelId`/`taskId`/`winsTotal`/`attemptsTotal` are raw synced values with nothing to diverge from; `combinedScore` is a leaderboard-only ranking key with no Analytics equivalent to match.

Verified with two independent throwaway SQLite datasets, each hand-computed in a standalone Python script and diffed against the real rewritten `leaderboard.php` running over local HTTP: a 20-attempt case (2 exact clean-threes + 2 tolerance-eased non-clean wins) confirmed `hitRatePct=20.0%`/`luckyIndex=19.6` exactly matched the hand-derived reference (vs. the old formula's 100%/inflated-range result); a 100-attempt case with 10 genuine outliers confirmed the P3/P97 trim actually changes the computed range (750ms span vs. 1000ms untrimmed) and the server matches the trimmed reference.

### Personal report page — made real, then moved in-app

Continuation of 2026-09-10's plan (native opens browser → `report.html` → `report_data.php`). This session:

**1. Finished building `report.html` + `report_data.php`** (were mid-edit going into this session). `report.html`: stripped the multi-test-player scaffolding from the "Metric Comparison" artifact, `state.player` is now the fixed key `"me"`, added a real async `loadReport()` that reads `playerId`/`token` from the URL, fetches, and drives loading/error/success states. `report_data.php` (new): `playerId`+`token` via GET, `hash_equals` against `players.sync_token` (same shape as `sync.php`), filters to the player's *current* precision tier (D13's rule, now actually enforced server-side) and `current_epoch`, computes `n` (attempt-number-within-session) as a plain PHP running counter per `session_id` (no window functions — MySQL 5.7 baseline).

**2. Moved from browser to in-app WebView.** Alex asked whether an in-app popup was feasible instead of leaving the app; presented the tradeoff (full native port of the narrate() engine — rejected, duplicates ~2000 lines already iterated 28+ rounds — vs. WebView-in-Dialog — same JS, no duplication); Alex picked WebView. `LeaderboardScreen.kt`: `LocalUriHandler.openUri(...)` replaced with a `reportUrl: String?` state var and a new `ReportDialog` composable (near-fullscreen Compose `Dialog`, dark-panel styling matching `ComparePlayersDialog`, `AndroidView` wrapping a plain `android.webkit.WebView` with `javaScriptEnabled=true`, a `CircularProgressIndicator` until `onPageFinished`). No new permission needed. Confirmed explicitly for Alex: the page still does its own `fetch()` on every load (no caching layer anywhere), so WebView gets the same fresh-data guarantee a browser tab would.

**3. Sample-size disclaimer.** New line under the lede in both languages — RU "Точность интерпретации растёт вместе с числом попыток. Более-менее достоверные данные появляются примерно после 500 попыток на втором уровне." / EN "Diagnostic accuracy improves with more attempts. Reasonably reliable data starts to show up after roughly 500 attempts at Level 2."

**4. Period picker now enforces the level boundary, not just the data.** `report_data.php` already only returns current-tier attempts (D13), but the period-preset popover (Today/Yesterday/Week/Month/All time) didn't know that boundary and could offer a preset whose nominal range reached earlier than any same-tier attempt exists — never pulling wrong-tier data (impossible, it isn't fetched) but silently showing a shrunken, mislabeled window. Fixed exactly as asked ("просто дизейбли соответствующие периоды в меню"): `openPeriodPopover()` disables (real `disabled` attribute) any preset whose computed `.from` is earlier than `playerBounds().min` (= the first same-tier attempt, since `DATA` is already tier-filtered); "All time" is never disabled by construction. Custom-date inputs got a matching `min` attribute. `setDefaultRanges()` got the same clamp — the range shown before the user touches anything no longer starts before the level began either.

Verified live: seeded a throwaway SQLite player with only 8 days of current-tier history, served the real page + endpoint together over local HTTP — confirmed the disclaimer renders, confirmed the default comparison period landed exactly on the level boundary (was reaching 5 days too far back pre-fix), confirmed the popover disabled "Week"/"Month" for the comparison side while keeping "Today"/"Yesterday"/"All time" enabled, and confirmed period A's own popover (anchored to "today" instead of "day before A") correctly kept "Week" enabled and only disabled "Month" — the check reacts to which side is being picked, not a blanket rule.

### Files touched
Native: `LeaderboardScreen.kt` (`ReportDialog` + `reportUrl` state, replacing the browser-open call).
Webapp: `api/_lib.php` (`is_true_three_win()`), `api/leaderboard.php` (rewritten Lucky Index + Hit Rate), `report.html` (async loader, disclaimer, level-boundary period picker — see 2026-09-10/11 for the full build), `api/report_data.php` (new).
Handoff docs (not deployed by this session — no server access): `webapp/handoff/report_page_deploy_2026-09-11.md`, `webapp/handoff/leaderboard_formula_fix_2026-09-11.md`.

### Build
v13.81 → v13.82. APK on Desktop: `play-of-time-v13.82.apk`.

### Known open / deferred
- **Nothing server-side is live yet.** `_lib.php`, `leaderboard.php`, `report.html`, `report_data.php` are all written and locally verified (SQLite + local PHP server + real browser checks) but not uploaded — `https://playoftime.online/report.html` still 404s as of this session's last check. See the two handoff docs for the exact 4-file upload list and a `curl` sanity check for each.
- The in-app WebView popup is compiled and built into v13.82 but can't be exercised end-to-end (no emulator run this session) until the server files are live — will just show the same 404 inside the popup instead of a browser tab until then.
- The Decision Log artifact (D29-D31) has the full blow-by-blow if any of the above needs re-deriving without re-reading this changelog.

## What Was Done (12 September 2026 — webapp only, no native build)

Continuation of 2026-09-11's report-page work, same session (date rolled over past midnight partway through). **Everything below is `webapp/report.html` only — no native changes, no new APK.** Still not deployed anywhere; see the two 2026-09-11 handoff docs for the upload list, which hasn't grown (same 4 files, `report.html`'s content has just changed further since they were written).

### 1. Sample-size disclaimer + level-boundary-aware period picker
Alex asked for a disclaimer that diagnostic accuracy depends on attempt count ("более-менее достоверные данные появляются примерно после 500 попыток на втором уровне") — added as a new line under the lede, both languages, wired into both `render()` and the pre-fetch static-translation pass. Same message also asked to keep comparisons within one precision level by disabling the corresponding period-picker options — `report_data.php` already only returns the current-tier history (D13's rule), but the period popover (Today/Yesterday/Week/Month/All time) didn't know that boundary and could offer a preset reaching earlier than any same-tier data exists. Fixed: `openPeriodPopover()` disables (real `disabled` attribute) any preset whose computed date would need to reach before `playerBounds().min` (= the level's first attempt, since `DATA` is already tier-filtered); custom-date inputs got a matching `min` attribute; `setDefaultRanges()` (the range shown before the menu is even touched) got the same clamp. Verified live with a throwaway SQLite player with only 8 days of current-tier history — confirmed "Week"/"Month" correctly disabled, "Today"/"Yesterday"/"All time" stayed enabled, and the default range no longer started before the level began.

### 2. "Holding steady" — narrate() now comments on unchanged metrics too
Alex: comment not just on what shifted, but on what stayed the same — encourage keeping it if it's good, push to improve if it's poor. This needed an absolute (not just before/after-relative) judgment call for 8 of 9 metrics that have no natural universal threshold (Lucky Coefficient is the one exception — it has a built-in 1.0× chance baseline). Asked Alex directly rather than invent thresholds and risk a false diagnosis; he picked "judge against the player's own history" over the alternatives offered.

New `historicalDistribution(attempts, accOrd)`: buckets the player's full current-tier history into Monday-anchored weekly buckets (drops buckets under 5 attempts as too noisy), computes `metricsFor()` per bucket, collects each metric's distribution across buckets. For a metric whose delta falls under the flat/noise threshold, compares its current value to the `median()` of that distribution (needs ≥2 buckets, else stays silent) per the metric's own favorable direction. Output is a new "Holding steady" section in `narrate()`, between "What changed" and "How they connect" — generic two-template copy (not per-metric, since there's no physiology to explain, just the fact + verdict). Verified live with a 40-day flat-error-spread test player: the section correctly fired for two metrics (one judged strong-half, one weak-half), and flat-but-data-starved metrics (Hit Rate/Luck Coefficient, both 0 in that run) correctly stayed silent instead of guessing.

### 3. Chart redesign — twice, same day
First pass ("объедини в единый профиль — названия почти друг над другом и узкие полоски... заполни пустое пространство шириной графиков"): horizontal redesign — metric name on its own line above two thin (13px) horizontal bars (was name beside, in a 148px side column that ate width from the bars), "BEFORE"/"AFTER" text tags replaced with the actual chosen date rendered vertically (first attempt printed the full date range and blew the row height out to ~90px, defeating the "almost stacked" ask — shortened to a single end-date, "DD.MM" only).

Alex rejected this outright next round ("хуйня какая то... давай попробуем сделать график вертикальным. чтобы на одной оси все показать делай склеенные полоски до и после"). Rebuilt as a real grouped **vertical** bar chart: 9 metrics side by side, each a glued before/after column pair (no gap between the two bars), height = value/localMax*100 per metric — same per-row normalization the horizontal version used for width, now read as one shared 0-100% vertical axis. Per-bar text labels dropped entirely (physically can't fit "132.8 ms" on a 20px bar) — exact numbers live in the hover tooltip (now on the whole glued pair) and the existing "Show as table" view. This also made the old WCAG-contrast label-ink machinery (`parseRgb`/`resolveVarColor`/`contrastRatio`/`pickReadableColor`, from the 2026-09-08 label-placement fixes) fully dead code — deleted. 9 columns don't fit 640px, so the chart scrolls sideways in its own container (`.profile-scroll`, same pattern as the data table) rather than the page.

**Real bug caught on the very first check**: Alex flagged the vertical bars as visibly misaligned ("выравняй их хотя бы по 0 отметке"). Root cause: `.profile-chart{ align-items:flex-end }` aligned the bottom of the *whole group* (bars + the vertical metric-name label below them), not the bottom of the bars — since metric names are different lengths as vertical text, groups with short names sat lower than groups with long names, and the bars "floated" at different baselines instead of sharing one. Fixed: `align-items:flex-start` — since the bars container has a fixed height in every group, top-aligning the group makes the bars' bottom edge coincide regardless of the name label's length. Verified via `getBoundingClientRect()` on all 9 groups: identical pixel after the fix, visibly different before.

### 4. Delivery method — real interactive HTML instead of screenshots
Alex asked to see a design before anything gets built ("перед тем как компилировать покажи эскиз") — sent a headless-Chrome-rendered PNG the first time. Next round he asked for "хтмл как предыдущие" (an actual HTML file, not a picture) — built a standalone offline snapshot of `report.html` with a real `report_data.php` JSON response baked in directly (`fetch()` skipped), so it opens straight from disk with no server needed, and sent that file instead. Worth reusing this snapshot technique for future design-review rounds on this page rather than defaulting to screenshots.

### Files touched
`webapp/report.html` only. Nothing else.

### Known open / deferred
- Still nothing deployed — same 4 files as 2026-09-11's handoff docs (`api/_lib.php`, `api/leaderboard.php`, `report.html`, `api/report_data.php`), `report.html`'s content has just moved on further since those docs described it.
- The vertical profile chart's per-metric-local-max normalization means the shared 0-100% axis is honest about proportion-of-own-range but doesn't let you compare ABSOLUTE magnitude across metrics (e.g. a 50%-tall "Hit Rate" bar and a 50%-tall "Overwhelming" bar represent unrelated real-world quantities) — this was accepted as the correct tradeoff for a heterogeneous-units profile, not flagged as a bug, but worth knowing if it comes up again.
- Decision Log entries D32-D34 have the full detail if anything above needs re-deriving.

## What Was Done (15 September 2026 — webapp only, no native build)

Long session, `webapp/report.html` only. Covers: `range_span` fully removed from display (was already dropped from the table, turned out to still be live in the narrative text and a cross-metric callout), the legend merged into the narrative section for real (a first pass duplicated it instead of replacing it — caught and fixed), tap-on-the-face-itself psychological explanations (a genuinely new interaction, not just a copy of the narrative's own tap targets), and a persistent week/month pager on the comparison face's label. Every change verified live via a local `http.server` + the Browser pane against synthetic mock data (`scratchpad/make_snapshot.py`), not just read from source — several real bugs were caught exactly this way (see below).

### 1. `range_span` — removed from display everywhere, not just the table
Alex: "мы его совсем отовсюду убрали... я ожидаю что он больше не представлен никак и нигде." Checked the live code rather than trusting the prior session's own summary of what had been done — `range_span` ("Control Under Pressure") was indeed dropped from the bottom data table on 2026-09-15 (earlier the same day), but was still fully alive in `narrate()`'s `order` array (a real "What changed" paragraph), in `PHRASES`/`PHRASES_EN`, and in a `CROSS_LINKS` entry pairing it with "early". Removed all of it — `METRICS` entry, both `PHRASES` blocks, the `CROSS_LINKS` pairing, `order`, and the now-dead `.filter(r => r.key !== "range_span")` in `render()` (the filter's target no longer exists in `METRICS` at all, so it was already a no-op). The internal `rangeSpan` local variable in `metricsFor()` stays — it still feeds the Luck Coefficient formula — but the field is no longer returned from the function at all (`range_span: rangeSpan` removed from the return object), since nothing reads it anymore.

### 2. Legend merged into "Here's how you're doing" (renamed from "Что изменилось"/"What changed")
Per Alex's original spec: every metric name shown in this section gets a tap tooltip sourced from the first half of its legend sentence (what the parameter means); a 🙂 icon + the second half (how the feature reacts) follows the paragraph text — tapping that phrase animates the feature, tapping the icon shows the full legend sentence in the same tooltip and animates too. Implementation: 7 of the 11 `CH_LEGEND` rows (the ones narrate() actually shows a paragraph for) got hand-authored `paramText`/`featureText` fields (or `paramTextByKey`/`featureTextByKey` for the one row — brow — that covers two narrate() metrics, early and late, at once); a shared `legendHalvesForKey(key, lang)` helper resolves either shape transparently. A shared tap-triggered tooltip (`#metricTooltip`, NOT the native `title` attribute — this page runs in a touch-only WebView with no hover state) is reused across all these tap targets.

**Real regression caught by Alex, not self-caught**: the first pass added all of the above but left the OLD standalone "How to read these faces" toggle + its own row-by-row panel sitting right next to the now-merged content — "совмещаем" meant merge/replace, not duplicate. Removed the whole panel: `buildFaceLegend()`, `wireFaceLegend()`, `chMetricNames()` (dead once its only caller was gone), the toggle button + panel HTML, its CSS (`.face-legend-toggle`, `.fl-row`, `.fl-name`, `.fl-metric`, `.fl-desc`, `.fl-chevron`), and the `faceLegendSummary` STR entries. The one piece with no equivalent elsewhere — the small Wikipedia "Chernoff faces" credit line — was kept, now sitting on its own under the faces (`#faceCredit`, built directly from `CH_CREDIT` in `render()`).

### 3. Tapping the drawn feature itself, directly on either face
A second, genuinely separate interaction from #2, requested explicitly after Alex noticed it was missing ("клик по самой черте лица должен приводить к её анимации и всплывашке"). Root design problem: an SVG element's own `data-feature` attribute can carry MULTIPLE tokens when one shape has more than one independently-adjustable dimension (the eye ellipse's rx from `eyeWidth`, ry from `eyeOpen`, cx from `eyeDx`; the head ellipse's rx/ry from `gcv`/`hit`; the mouth path's length/curve from `eff`/`lucky`) — a raw click can't unambiguously mean just one characteristic. Solved with `chLegendEntriesForFeatureTokens(dataFeatureStr, lang)`: finds every `CH_LEGEND` row whose own `.feature` tokens are a subset of the clicked element's tokens, flattens to one `{row, key}` entry per `metricKeys` entry of each matching row (so brow's own two keys, early/late, get the same per-key treatment as the multi-row cases), and a per-`data-feature`-string cycle counter (`chFaceElementCycle`) steps through all of them on repeated taps of the same element rather than always landing on the first.

**Content went through two corrections from Alex, both real, both applied**:
- First pass showed the row's own visual `desc` ("the more X the bigger the feature Y") — Alex: "по лицу когда тюкают объяснение давать в псих терминах" (explain in *psychological* terms when tapping the face, not restate the visual behavior the narrative's own мордочка/text triggers already cover). Required completing `paramText`/`featureText` (or the `ByKey` variant) for the 4 remaining `CH_LEGEND` rows that narrate() never uses (`eyeOpen`, `eyeWidth`, `ears`, `pupilR` — covering `earlyShare`/`lateShare`, `earlyCV`/`lateCV`, `earlyTail`/`lateTail`, `earlyMode`/`lateMode`), so every one of the 16 metric keys has psych-term text available now, not just the 8 narrate() shows.
- Second pass replaced the visual desc with JUST the short parameter-meaning line — Alex: "куда делась вся психология? шире ж были фразы, верни!" (the short version had thrown away real content, not just changed its framing). Fixed by combining both: `psychTextForEntry()` now returns `"${metricLabel} — ${param} ${feature}"` — the proper metric name/label leads, but the fuller meaning-plus-behavior text still follows in full, nothing dropped.

A follow-up ask for a SECOND, short "balloon" callout right at the tapped feature (separate from the fuller tooltip) was built (`#faceBalloon`, a real speech-bubble shape with a CSS triangle pointer) and then explicitly reverted a message later — "убери верхний тёмный балон нахуй" — element, CSS, and JS all removed again. The remaining single tooltip's positioning was also corrected in the same pass: it used to anchor to the clicked element's whole bounding-box bottom edge (`r.bottom + 6`), which could land well away from the actual tap point on a tall/wide shape; `showMetricTooltip()` now accepts an optional `clickEvent` third argument and, when the caller (the face-tap handler only) passes one, anchors to `clickEvent.clientX/clientY` instead — the narrative's own metric-name/мордочка callers don't pass one and keep the original bounding-box anchoring unchanged.

### 4. Persistent ‹›pager on the comparison face's label
Alex: "сделай листалку недель и месяцов для второй морды." The underlying step logic already existed and worked correctly (`showWeekPicker()`/`showMonthPicker()`'s own prev/next arrows, confirmed still functioning via a live test before touching anything) — the gap was that reaching it took two taps (open the label's popover, then tap "Week"/"Month" to reveal the arrows) rather than being immediately visible. Added `periodNavArrow(which, dir)` — renders a small ‹ or › directly flanking a face's own label whenever that side's current selection (`state.aSel`/`bSel.type`) is already `"week"` or `"month"`, correctly disabled at the same player-history boundary the popover's own pickers already respect — and `stepPeriod(which, dir)`, which mirrors the popover's exact step math (7-day or calendar-month) but applies immediately and re-renders, without needing the popover open at all. Side A never shows these (it only ever offers "Today", which has no natural "next" to page to) — matches `PERIOD_OPTIONS_A` already excluding week/month for that side entirely.

### Verification method
Every one of the above was exercised live: a local Python `http.server` on port 8791 serving `scratchpad/report_snapshot.html` (regenerated from the real `report.html` via `make_snapshot.py` after every edit), driven through the Browser pane — real click events (both programmatic `dispatchEvent` for exact repeatable sequences, like cycling through the eye's 5 characteristics in order, and real `computer` clicks with screenshots for visual confirmation), DOM/state inspection (`metricTooltip.textContent`, `state.bSel`, disabled-attribute checks at data boundaries) rather than assuming code correctness from reading it alone. Caught and fixed in-session, not shipped-then-reported: the outside-click-closes listener would have immediately closed a tooltip the same click had just opened (needed `[data-feature]` added to its exclusion list, same bug shape as the pre-existing `.metric-name`/`.feature-icon` exclusions already there for exactly this reason).

### Files touched
`webapp/report.html` only. `webapp/handoff/report_page_update_2026-09-15.md` (new) — deploy note for this session, since Alex went to sleep right after and asked for exact upload instructions rather than continuing to test interactively.

### Known open / deferred
- **Nothing deployed this session either** — same as every report.html session since 2026-09-11, no server access here. The new handoff doc has the exact single-file upload instruction (`report.html` → site root) plus a reminder to check whether the 2026-09-11 `api/*.php` files ever actually made it live, since that's still unconfirmed.
- Decision Log entries (if the artifact is still being kept) don't yet reflect tonight's session — worth a sync pass next time that artifact is open.

### Same-session continuation — psych-depth on steady metrics, Day pager for Face A, redundant-pager cleanup, background/width redesign

Directly continuing the same 15 September session above (not a new day). Alex kept testing live and found more gaps/bugs; all fixed and **re-verified this time with real mouse clicks** (`computer` tool), after a real bug (see below) was traced specifically to synthetic `.click()` calls not reproducing real click-event-bubble timing.

**5. Psychology added to steady (unchanged) metrics too, not just changed ones.** Alex: "а ты так и не добавил психологии к интерпретации? причем добавить надо не только в измененные параметры но и те которые не изменились." The old `STEADY_RU`/`STEADY_EN` were two flat generic good/poor templates shared across all metrics (a deliberate simplification from 2026-09-12). Replaced with `STEADY_PHRASES`/`STEADY_PHRASES_EN` — 8 metrics × good/poor × RU/EN, same psychological depth/voice as the changed-metric `PHRASES` table. `narrate()`'s steadyItems now pulls `steadyTable[key][isGood ? "good" : "poor"]` instead of the old generic pick. The tap-tooltip/мордочка treatment (item #2 above) was extended to these steady paragraphs too via the same shared `chipAndName()` helper used for changed items.

**6. Face-tap psych text — completed for all 16 metric keys, not just the 8 narrate() shows.** Item #3 above only had `paramText`/`featureText` for the 7 legend rows narrate() actually uses. Completed the remaining 4 rows (`eyeOpen`, `eyeWidth`, `ears`, `pupilR`) so every drawn feature has psych-term text when tapped directly, regardless of whether narrate() ever mentions that metric in prose.

**7. Balloon callout — built, then explicitly reverted; tooltip repositioned to the click point.** Alex asked for a short secondary "balloon" at the tap location (separate from the fuller tooltip), then reversed it next message: "убери верхний темный балон нахуй. нижний опусти пониже на уровень начала указателя." Removed the balloon entirely (element, CSS, JS, its own outside-click closer). The remaining single tooltip's anchor was corrected in the same pass — `showMetricTooltip()` gained an optional third `clickEvent` param; the face-tap handler (only) passes it so the tooltip anchors to the real `clientX/clientY` of the click, not the whole SVG element's bounding box.

**8. Side A ("subject" face) gets Yesterday, then over-scoped, then corrected, then merged into a single "Day" pager.** Sequence, in order:
   - Alex: "добавь в левую морду еще пункт yesterday" — added `yesterday` to `PERIOD_OPTIONS_A` (was `[today]` only).
   - A garbled bug report ("для месяца листалка не показывается, переключается между периодами тоже криво") was misread as "give side A everything side B has" — `PERIOD_OPTIONS_A` was wrongly set to the full B list. Alex, correctly furious: "ты сказал добавить в левую морду толко вчера, а ты добавил всю гармонь. переделай нахуй как велено!" Reverted immediately to `[today, yesterday]` + Custom.
   - Final ask, same session: "уберем и тудэй и естудей и оставим только дей. а переключать его будем аналогично месяцам. но первое значение будет не число а yesterday" — merged Today+Yesterday into one steppable "Day" menu entry (`PERIOD_OPTIONS_A = [{key:"day", ...}]`), defaulting to Yesterday (`dayOffset:1`) when first picked (`applyDefaultDay()`, new). Added `case "day"` to `presetRangeFrom()`, a `sel.type==="day"` branch to `describeSelection()` (labels: Today/Yesterday/Day before yesterday by offset, else a plain date), extended `periodNavArrow()`/`stepPeriod()` to handle `"day"` alongside the existing `"week"`/`"month"` handling — steps by `dayOffset`, forward-disables at today, back-disables at the level's own history start (`b.min`).
   - **Verified live with real clicks** (see Verification, below) — the earlier synthetic-only verification method had been flagged as insufficient by a prior bug (see #9), so this feature specifically got real-click confirmation before being called done.

**9. Real bug found: Month/Week popover buttons missing `stopPropagation`, only reproducible with real clicks.** Alex, repeatedly and with escalating frustration ("в 5 ссука раз тебе говорю: у морды справа надо сделать листалку месяцев если выбран месяц!"), insisted the Month pager for Face B wasn't working, despite earlier automated `.click()`-based tests appearing to pass. Root cause: `openPeriodPopover()`'s Month/Week buttons synchronously replaced `popover.innerHTML` mid-click (to show the old popover-internal prev/next picker), which detaches the just-clicked button before the click event finishes bubbling — the document-level "click outside closes popover" listener then sees a detached target and immediately closes what had just opened. This is the same bug class already fixed for "Custom dates" back on 2026-09-14, but this occurrence was missed then. **Critically, this bug only manifests with real mouse clicks** (`computer` tool) — synthetic `element.click()`/`dispatchEvent()` calls don't reproduce the same event-bubble timing, so every earlier automated test kept "passing" while Alex's real taps kept failing. This is now the established reason this project treats real clicks as the only trustworthy verification for popover/menu interactions (added to [[feedback_working_style]]-adjacent practice, not just this file).

**10. Redundant double-pager — the internal fix from #9 was made obsolete by removing the popover-internal picker entirely.** Once #9's `stopPropagation` fix was verified, Alex looked at the result and said the header pager (item #4, from earlier the same day) PLUS the popover's own internal prev/next picker together were confusing: "то ни одной листалки то сразу две. убери нахуй первую. оставь только ту которая в заголовке морды остается." Removed `showMonthPicker()`/`showWeekPicker()` (the popover-internal pickers) entirely, replaced with new `applyMonthOrWeek(which, key)` — applies the calendar-default month/trailing-7-day-week directly on menu click and re-renders, no intermediate picker UI at all. Since this handler no longer replaces `popover.innerHTML` mid-click, the `stopPropagation` fix from #9 became structurally unnecessary for this path (still theoretically relevant if a future popover-replacing handler is added) — only the persistent header-label pager (`periodNavArrow`) now exists for stepping.

**11. Visual redesign — flat cream background, wider page.** Alex: "убери желтую подложку. а кремовую раздвинь до границ экрана. за счет этого в принципе расширь ширину." Merged `--page`/`--surface` CSS vars to the same cream value (`#FBE7A6`, was a distinct darker yellow `#F2B70D` for the page vs cream for the card) — the page background IS the card background now, no separate "floating card" look. `.card` lost its own `background`/`border`/`box-shadow`/`border-radius` (kept only `padding`, since there's no longer a color distinction to justify the card look). `.wrap`'s `max-width` increased 640px → 880px. Removed now-dead CSS from the earlier popover-internal-picker removal in #10 (`.month-picker-row`, `.month-picker-label`).

### Verification (this continuation)
Re-verified from a clean browser tab (earlier in this same session, several leftover tabs from long back-and-forth testing had accumulated stale/confusing state, including one that had loaded the raw `file://` production file directly — closed all, started fresh) using **real mouse clicks exclusively** for every popover/pager interaction, per the #9 lesson:
- Face A "СЕГОДНЯ" label → popover shows exactly "День" + "Произвольные даты…" (no separate Yesterday item) → clicking "День" lands on "ВЧЕРА" with a persistent ‹›pager appearing → ‹ steps to "ПОЗАВЧЕРА" → › twice returns to "СЕГОДНЯ" → confirmed forward arrow is genuinely disabled there (`state.aSel` stayed `{type:"day", dayOffset:0}` after an extra click on the disabled arrow).
- Face B "НЕДЕЛЯ" label → popover shows Yesterday/Day-before/Week/Month/All-time/Custom (unchanged) → clicking "Месяц" applies "СЕНТЯБРЬ 2026" directly (no intermediate picker) with only the header pager visible → ‹ steps to "АВГУСТ 2026" → › returns to September (`state.bSel` confirmed via JS: `{type:"month", monthStart:"2026-09-01"}`).
- Confirmed no double-pager anywhere and no yellow page background remains (flat cream fill, page visibly wider) via screenshots throughout.

### Files touched (continuation)
`webapp/report.html` only (same file as the rest of the day). `webapp/handoff/report_page_update_2026-09-15.md` — rewritten to cover the full day's changes (was written mid-session, now stale entries corrected).

### Known open / deferred (continuation)
- Still nothing deployed — same caveat as above, `api/*.php` upload status from 2026-09-11 remains unconfirmed.
- This entire day's report.html work (both halves) is verified against synthetic mock data only, real-click interaction testing aside — worth a real-data pass once the page is actually live.

### Second continuation — mouth/nose/face-boundary geometry clamps, header restructure, tooltip widened + repositioned above, native "YOUR RESULTS" removed (v13.82 → v13.84)

Still the same 15 September session (third pass). Alex asked for a careful review of the Chernoff-face code specifically to eliminate incorrect facial-feature behavior: nose shouldn't intersect the mouth, mouth shouldn't reach outside the face outline or above the nose.

**12. Mouth/nose/head geometry — three real, confirmed overlap bugs found and fixed.** `mouthLen` (eff), `smile` (lucky), `headW`/`headH` (gcv/hit), and `noseTilt` (consist) are all independent metrics with zero cross-awareness in the old code — mouth corners sat at a hand-picked FIXED y (cy+40) regardless of head size, and the curve's control point could swing ±48px from there regardless of where the nose ended up. Worked through the actual trig by hand (not guessed) and confirmed three genuine collision paths exist at plausible (independently-reachable) metric extremes:
- small head (headH at its floor, 45) + widest mouth → corners poke past the sides of the head ellipse;
- small head + biggest smile (curve dips down) → curve extends below the chin;
- tilted nose (worst case ≈ −19.29°, found by differentiating the nose-tip's own y-formula) + deep frown (curve rises up) → curve rises into the nose, gap could shrink to ~2px or invert.

Fixed with three dynamic geometry-safety clamps added to the shared `chFaceGeometry()` (same function `chFaceSvg()` and `chApplySvgParams()` both already call, so the initial render and the legend's live-animation frames can't disagree) — same pattern already used there for brow/eye/ear collision-safety, same accepted tradeoff (a narrower extreme range only in the rare worst-case combination, full range in the common case). Verified two ways, not just by reading the math: a full grid sweep (161,051 combinations of head size × nose tilt × mouth width × smile) confirmed zero violations, worst-case margins land exactly on the configured safety constants (3px face-boundary margin, 6px nose-mouth gap); then a live rendered screenshot of two hand-built worst-case faces (small head + deepest frown + widest mouth; small head + biggest smile + widest mouth) confirmed the expressions still read as natural faces, nothing visibly clipped or malformed.

**13. Metric-characteristic tooltip — widened and repositioned above (not below) what it explains.** Two separate asks in the same breath: "делай плашку с текстом характеристик пошире" (widen the box) and, mid-turn, "пусть все баллоны которые срабатывают по тапу выводятся не под объектом, который они поясняют а над ним" (every tap-triggered tooltip should sit above, not below). `.metric-tooltip`'s `max-width` went 260px → 320px. `showMetricTooltip()`'s two anchor branches (click-point anchoring for face taps, element-bounding-box anchoring for narrative metric-name/мордочка taps) both used to add a gap and go DOWN from the anchor; both now measure the tooltip's own real rendered height (`offsetHeight`, read right after unhiding with the real text already in it — text length varies a lot row to row, so this can't be a fixed constant) and subtract that plus a gap to go UP instead. Verified live on both paths: a real click on a metric name, and a synthetic-but-real-handler click dispatched at the exact rendered pixel of a face's mouth path (screenshot confirmed in both cases: tooltip box sits above the tapped text/feature, correct wider width, full psychological text intact).

**14. Native — "YOUR RESULTS" label removed from the report WebView dialog's black title bar.** This one wasn't in `report.html` at all — Alex's "самая верхняя надпись your result на черном фоне" was the native `ReportDialog` composable's own header `Row` in `LeaderboardScreen.kt` (`panelBg = Color(0xFF1A1A1A)`, near-black). Removed the `BasicText("YOUR RESULTS", ...)` call; the Row's `horizontalArrangement` changed from `SpaceBetween` to `End` so the remaining "×" close button stays correctly right-aligned with nothing to space against.

**15. Report card header restructured — full-width title, language toggle moved below it.** `.title-row` was a `display:flex; justify-content:space-between` row (title left, RU/EN toggle right, per a 2026-09-13 decision this explicitly supersedes). Changed to `flex-direction:column`, so the `h1` (already block-level, naturally full-width) sits on its own line and `#langSeg` sits directly under it, left-aligned.

**Build**: v13.82 → v13.83 (assembled, 2m52s incremental — item 14 only). APK on Desktop: `play-of-time-v13.83.apk` (old v13.82 removed per the cleanup habit). `version.properties` auto-bumped to 13.84 for the next build.

### Files touched (second continuation)
`webapp/report.html` (geometry clamps, tooltip width/position, header layout). `LeaderboardScreen.kt` (`ReportDialog` — title text removed).

### Known open / deferred (second continuation)
- The geometry clamps are verified numerically (full grid sweep) and visually (two hand-built worst-case screenshots) against synthetic mock data — not yet seen against a real player's actual metric distribution, same standing caveat as the rest of this session's report.html work.
- Item 14 (native) is built into v13.83/APK on Desktop but not independently re-verified on-device this pass (no emulator session run) — the change itself is a one-line Compose removal, low risk, but worth a glance next time the Ranks → report flow is opened on a device.

### Third continuation — report dialog/card margins halved, header raised and made fluid (v13.83 → v13.85)

Same 15 September session, fourth pass. Alex: the report content should use the available screen space better, not sit inside unnecessarily wide margins.

**16. `.card` (report.html) — horizontal padding halved, top padding cut.** `clamp(16px,4vw,28px)` → `clamp(8px,2vw,14px)` horizontal; top padding `18px` → `10px` (bottom kept at 18px, asymmetric on purpose — only the top needed to shrink to raise the title, per Alex's separate ask below).

**17. Title made fluid instead of fixed-size, to guarantee it fits on one line.** "пусть в первой строчке уместятся четыре слова your play of time results." `h1{font-size:27px}` → `clamp(19px, 6.2vw, 27px)` — scales down on narrow viewports instead of wrapping. **Verified live**, not just reasoned through: emulated 375px and 340px viewports in the Browser pane, read `#pageTitle`'s real rendered height back via JS and compared it to its own computed `line-height` — equal at both widths (34.9px/31.6px respectively, matching line-height exactly), confirming the English title ("Your Play of Time Results") renders as a genuine single line at both sizes, not just visually plausible in a screenshot.

**18. Native `ReportDialog` — outer margin halved (width) and its manual vertical offset replaced with the same inset every other screen already gets.** "черную подложку тоже сделай минимальной, пусть начинается там где все остальные экраны начинаются по высоте. и соответственно подними крестик." Every other screen (`Main`/`Stat`/`Settings`/`About`) sits at plain `Scaffold` content padding (`WindowInsets.safeDrawing` — i.e. just the status-bar inset, no extra manual offset — see `ui_lib/Screen.kt`). `ReportDialog`'s outer `Column` had a flat `padding(horizontal=8.dp, vertical=24.dp)` **on top of** that same system inset — an artificial gap unique to this one screen. Changed to `padding(horizontal=4.dp).statusBarsPadding()` — horizontal halved per the explicit ask, vertical now reproduces the exact same top-of-screen starting position every other screen already uses, instead of a hand-picked 24dp. The close "×" (inside the same Column's header Row) rises automatically along with the now-higher black panel — no separate change needed there.

**Build**: v13.83 → v13.84 (27s incremental). APK on Desktop: `play-of-time-v13.84.apk` (old v13.83 removed per the cleanup habit). `version.properties` auto-bumped to 13.85 for the next build.

### Files touched (third continuation)
`webapp/report.html` (`.card` padding, `h1` font-size). `LeaderboardScreen.kt` (`ReportDialog` outer `Column` padding/insets, `statusBarsPadding` import).

### Known open / deferred (third continuation)
- Item 18 (native margin/inset change) is build-verified and reasoned from `Screen.kt`'s own Scaffold-default behavior, but not re-confirmed on-device (no emulator session this pass) — worth a glance next time the Ranks → report flow opens on a real device, specifically whether `statusBarsPadding()` alone (no explicit bottom inset) leaves the WebView content sitting flush enough at the bottom too, or whether the Dialog's own window already handles that automatically as assumed.
- The h1 fluid-sizing fix was verified in the Browser pane's viewport emulation (375px/340px), not inside the actual native WebView — the real WebView's font-rendering metrics could differ slightly from desktop-Chrome's, though the margin built into the clamp() (single-line confirmed with room to spare at 340px, the narrower of the two) should absorb small differences.

### Fourth continuation — dialog/body gap tightened further, page edge padding halved again, faces enlarged with a real overflow fix (v13.85 → v13.86)

Same 15 September session, fifth pass. Three asks in one message.

**19. Gap between the black dialog panel and the cream page cut by ~30dp.** The gap was entirely the close-button `Row`'s own height (44dp: 10dp padding + 24dp icon + 10dp) plus a genuine bug: `report.html`'s `<body>` had never had its UA-default 8px margin reset, so that stray margin showed the black panel bleeding through the WebView's transparent background for 8px above the cream. Fixed both: `Row` padding vertical `10.dp→2.dp` + close-icon `24.dp→18.dp` (font `14.sp→11.sp` to still fit) cuts the row to 22dp (−22dp), and `html, body { margin: 0; }` added to `report.html` removes the other −8dp. Total −30dp, exact.

**20. `.viz-root`'s own edge padding halved again.** `padding: 34px clamp(14px,3vw,36px) 64px` → `17px clamp(7px,1.5vw,18px) 32px` — every edge (top/sides/bottom) halved, not just the sides, since "от краёв экрана" (plural) read as all four edges, not only left/right.

**21. Chernoff faces enlarged, with a genuine pre-existing overflow bug found and fixed underneath it.** Alex: faces a bit bigger, but never past their own box even at the most "swollen" parameter extreme. Worked the actual worst-case trig, not guessed: `headW`/`headH` in `chFaceParamsAt()` were `max(35, 62·scaleFactor/√ecc)` / `max(45, 82·scaleFactor·√ecc)` with **no cap at all** — at the true worst-case combo (`scaleFactor` and `eccFactor` pushed to their independent extremes at once, real since they come from different metrics) headW/headH already computed to ~101/~126 against a 110-unit half-canvas, and ears (radius up to 18.2, anchored right at the head's own edge) pushed the true horizontal extent to ~119 — all silently clipped by the SVG's default `overflow:hidden`, just never large enough before to be visibly obvious. Fixed by raising the base multipliers (62→68, 82→90, a genuine ~10% "chuть побольше" for the typical case) and adding an explicit clamp — `headW = min(headWRaw, 110 − 4 − max(earRadL, earRadR))`, `headH = min(headHRaw, 110 − 4)` — so the worst case is capped exactly at a 4px margin from the canvas edge instead of overflowing, while the common case (well under the cap) is genuinely bigger than before.

Verified numerically (a real worst-case injection via the browser console — `chNorm` monkey-patched to force `scaleFactor`/`eccFactor`/ear metrics to their true extremes simultaneously, not just read the math) confirmed `leftExtent`/`topExtent` land exactly at 4, `rightExtent`/`bottomExtent` exactly at 216, out of the 0–220 canvas — never crossing. A rendered screenshot of that exact worst-case face (dropped into a bordered test box) confirmed it reads as a natural face with room to spare, not clipped or distorted. The everyday two-face comparison screenshot confirmed the faces are visibly bigger than the prior session's screenshots at the same viewport.

**Build**: v13.85 → v13.86 (21s incremental). APK on Desktop: `play-of-time-v13.86.apk` (old v13.85 removed per the cleanup habit). `version.properties` auto-bumped to 13.87 for the next build.

### Files touched (fourth continuation)
`webapp/report.html` (`html,body{margin:0}`, `.viz-root` padding halved again, `CH_CANVAS_HALF`/`CH_FACE_MARGIN` constants + `headW`/`headH` clamp in `chFaceParamsAt()`). `LeaderboardScreen.kt` (`ReportDialog` close-button `Row` padding/icon size/font size).

### Known open / deferred (fourth continuation)
- Item 19's native half (Row padding/icon shrink) is build-verified only, not re-confirmed on a real device/emulator this pass — the math is straightforward Compose padding arithmetic, low risk, but worth a glance next time the Ranks → report flow opens on-device.
- The 18dp close-button tap target is smaller than the usual ~44dp Android guideline minimum — accepted deliberately per the explicit "reduce by 30" ask; flag to Alex if it turns out hard to tap on a real device.
- The head-ellipse clamp assumes `cx=cy=110` (every current call site hardcodes this) — if a future caller ever draws a face off-center, the clamp's `CH_CANVAS_HALF` constant would need `cx`/`cy` threaded through instead of being a fixed number.

### Fifth continuation — leaderboard help mode intercepted by the leaderboard's own clicks (v13.86 → v13.87)

Alex: "хелп со знаком вопроса не работает внутри лидборда — сейчас при тюке на лидборд в режиме хелпа отрабатывает сам лидборд, а должен хелп."

**Root cause.** `LeaderboardScreen.kt`'s help-mode hit-testing (`helpTapGestures`, on the ancestor `Box` wrapping the whole scroll area — the same established pattern as Main/Analytics) relies on Compose's Main pointer pass processing children BEFORE the ancestor. If a descendant's own `clickable` consumes the tap-up first, `helpTapGestures` sees `event.changes.any { it.isConsumed }` and silently bails without ever selecting a help zone — the underlying click still fires. `StatScreen.kt`'s tree rows already guard against exactly this with `.clickable(enabled = !helpMode) { ... }`; `LeaderboardScreen.kt` never got the same treatment on its own interactive elements, so every tap during help mode fell straight through to the real leaderboard action instead.

**Fixed every clickable inside the two help zones** (`RANKS_NAME`, `RANKS_TABLE`) that wasn't already gated:
- `LeaderboardRowItem` (row tap → compare dialog / report) — new `helpMode` param, `.clickable(enabled = !helpMode, onClick = onClick)`.
- `LeaderboardHeaderRow`'s sort-column taps (both the coin-icon SC column and the plain-text columns) — new `helpMode` param, same `enabled = !helpMode` guard on each.
- `NameCard`'s Save button and the name `BasicTextField` itself (a focused/typable field would otherwise also consume the tap) — new `helpMode` param, `enabled = !helpMode` on both.
- The error-state "Try again" retry link — same guard, reusing `RowsPanel`'s already-in-scope `helpActive`.

The "?" toggle button itself keeps its unconditional `.clickable` (untouched) — that one's supposed to always work, same as the equivalent toggle on Main/Analytics.

### Files touched (fifth continuation)
`LeaderboardScreen.kt` (`LeaderboardRowItem`, `LeaderboardHeaderRow`, `NameCard`, retry link — all gained/used a `helpMode`/`helpActive` guard on their `clickable`).

### Known open / deferred (fifth continuation)
- Build-verified only this pass, not re-confirmed live on-device/emulator (no emulator session run) — per [[feedback_emulator_permission]], worth a quick on-device check next session that has emulator permission: enter help mode on Ranks, tap a row/sort header/Save, confirm the help bubble opens instead of the real action firing.

### Correction — "nothing deployed" was wrong; `webapp/` syncs to the live server automatically

Every "Known open/deferred" note since 2026-09-11 above claims `report.html`/`api/*.php` are written-but-not-deployed. Checked live, directly, 2026-09-15: fetched `https://playoftime.online/report.html` and diffed it byte-for-byte against the local file — **identical**, 176758 bytes both sides, including edits made minutes earlier in the same session (the `уменьши отступы бежевой плашки` comment, `CH_CANVAS_HALF`, the `html,body{margin:0}` reset). `api/report_data.php` responds 400 on a bad token (matches the new endpoint's real auth check); `api/leaderboard.php` responds 200 with `luckyIndex`/`hitRatePct` fields at plausible post-rewrite values. `api/_lib.php` can't be diffed this way (blocked from direct access, 403 — it's a shared include, not an endpoint).

**Conclusion: `C:\PlayOfTime\webapp\` is not a local-only copy that needs a manual upload step — it already reaches the live server, automatically, with no separate deploy action from any session.** Every "not deployed yet" caveat above (11 Sep onward) was a wrong assumption, not a real gap — ignore those specific claims; the actual content described in each of those sessions was live all along. Going forward: don't add a "not deployed" caveat to `webapp/` work without first checking live (`curl` + diff, same method as above) — the assumption that server access is needed to deploy has been wrong for weeks.

### Sixth continuation — About screen: "Learn more" on one line, meditation prompt rewritten, vibration on the flip cycle in fullscreen (v13.87 → v13.88)

Three asks in one message, all `AboutScreen.kt` (+ one shared constant in `AnimatedLogo.kt`).

**22. "Learn more at playoftime.online" on one line.** Was a bold section heading ("Learn more") on its own line, then the "playoftime.online" link sharing a `Row` with the logo column below it — on a narrow phone that link risked wrapping since the 140dp logo column ate into the same row's width. Now a single full-width `Row` of its own (two `BasicText`s — plain "Learn more at " + underlined-bold "playoftime.online", so only the domain stays clickable), never sharing width with the logo.

**23. "Tap for meditation" replaced with an explanation, moved to the logo's left.** Was a small caption sitting ABOVE the logo, in a `Column` centered together with it. Alex: instead, put explanatory text to its LEFT — "if you need to settle into the target time, tap the logo — its full turn takes exactly three seconds, watch it to feel the rhythm." Rebuilt as a `Row`: paragraph (`weight(1f)`) + the 140dp logo, replacing the old caption+logo `Column`.

**24. Vibration on the triangle/letter's flip cycle, fullscreen meditation view ONLY.** Alex: "вибросигнал при старте цикла и его окончании" (a pulse at cycle-start and cycle-end) — since the flip repeats with `RepeatMode.Restart`, end-of-one-cycle and start-of-next are literally the same instant, so this collapses to one repeating pulse every `SPIN_DURATION_MS` (3000ms) starting immediately when the fullscreen view opens (same moment the flip's own main layer starts, `delayMs=0`). `AnimatedLogo.kt`'s `SPIN_DURATION_MS` const changed `private` → `internal` so `MeditationFullscreen` reads the *same* constant instead of duplicating `3000` and risking drift if the flip timing ever changes. Uses the existing `VibrationService.tick()` (the light 50ms/200-amplitude pulse already used for the stopwatch's per-second tick — fits a soft rhythmic cue better than `.heavy()`, which is reserved for RUN/STOP press and a big win). Explicitly scoped to `MeditationFullscreen` only — the small always-visible 140dp logo on the About page itself does NOT vibrate, only the fullscreen tap-to-focus view.

### Files touched (sixth continuation)
`AboutScreen.kt` (Learn-more row, meditation-prompt row, `MeditationFullscreen` vibration `LaunchedEffect`, new imports). `AnimatedLogo.kt` (`SPIN_DURATION_MS` visibility only, `private` → `internal`).

### Known open / deferred (sixth continuation)
- Build-verified only — Alex declined an emulator run this pass ("нет, просто пришли APK"), so none of the three (single-line wrap, new paragraph's actual line-wrapping/length, vibration feel/timing) has been seen live yet. Worth a look next time there's emulator permission.

## What Was Done (16 September 2026 — webapp only, no native build)

Long session, `webapp/report.html` only. Two threads: (1) a real UI bug fix — the period picker could land on a day/week/month/custom range with zero attempts, showing the "insufficient data" empty state; (2) picking up the Chernoff-face theoretical-scale project from 2026-09-15/09-13 and actually finishing it — defined fixed, non-relative bounds for 14 of the 16 metrics that drive the faces (only `lucky` left undecided), added an outlier-exclusion rule, and built a new "optimal zone" visual reward (green highlight + a reshaped "laughing eye" for eyes specifically). A live-server deploy-status check also caught the 2026-09-15 "webapp syncs automatically" correction going stale — see below.

### 1. Period picker — real bug: could select a period with zero/insufficient attempts

Alex sent a screenshot of the "Недостаточно попыток" empty state appearing after normal menu navigation — reachable by design gaps in three places that the existing menu-level check (2026-09-11/14, `noAttempts` on the preset dropdown) never covered:

- The persistent ‹›pager (`periodNavArrow`/`stepPeriod`, added 2026-09-15) only checked calendar/level-boundary limits, never whether the CANDIDATE day/week/month it was about to step onto actually had any attempts in it — so paging through a gap (a day the player didn't play) landed on empty data with no warning.
- The custom-date form had no attempt-count check at all — min/max date bounds only kept it inside the current tier, not inside actual data.

New shared helper `attemptsCountInRange(from, to)`. `periodNavArrow` now also disables (with the existing `noAttemptsHint` title) whenever the candidate period would be empty; `stepPeriod` mirrors the same check as a defense-in-depth no-op (in case the disabled attribute is somehow bypassed); the custom-date form's Apply button now refuses an empty range and shows the same hint text inline instead of silently applying it.

### 2. Chernoff-face metrics — theoretical scales defined, row by row

Continuation of 2026-09-13's "how are base/amp actually chosen" question and the "let's define a real theoretical scale, not eyeball it" pushback from this session's start. Replaced `chNorm()`'s default RELATIVE scale (sized off whatever the two currently-compared periods happen to contain) with a new `CH_THEORY_MAX` table — a per-metric FIXED denominator — for every metric where a real theoretical bound could be pinned down:

- **`early`/`late`** (brow): 2.500–3.500s is "the norm" (Alex 2026-09-16: "давай брать за норму всё что больше 2,500 и меньше 3,500"), so the edge of the scale is **500ms** deviation — confirmed as the point "a more-or-less normal person can definitely tell apart" ("полсекунды он точно способен различить").
- **`earlyTail`/`lateTail`** (ears, P3/P97 near-worst miss) and **`earlyMode`/`lateMode`** (pupils, most frequent miss) — same physical quantity (signed ms deviation from the 3.000s aim), just a different statistic of it (near-worst / most-common instead of typical) — same 500ms edge applies directly.
- **`earlyCV`/`lateCV`/`gcv`** (eye width / head size — geometric coefficient of variation, a measure of how EVENLY-sized your misses are, not how big) — genuinely non-obvious, since CV is a ratio and NOT bounded at 100% just because it's expressed in percent (confirmed live with Alex after an initial wrong assumption that "% means the question is settled" — that only holds for a true proportion, not a CV). Derived a real, closed-form answer instead of guessing: for pure zero-mean Gaussian motor noise (no systematic bias, no outliers), the geometric CV is a **scale-invariant constant ≈ 203.65%**, independent of σ — Var(ln|Z|) for Z~N(0,1) = π²/8 via the χ²(1) trigamma identity (ψ'(1/2) = π²/2), so GCV = (e^(π/2√2) − 1)×100. Alex confirmed this is the EXTREME end (n=1, "chuть шум — это абсолютный минимум... должно случаться крайне редко"), not the neutral middle — real players correct/control at least somewhat, so hitting genuine pure-noise scatter should be rare. `CH_GEOM_CV_PURE_NOISE` computed once, reused for all three CV-family metrics.
- **`earlyShare`/`lateShare`** (eye squint, a count/total ratio — how OFTEN you miss to one side, not by how much) — naturally bounded 0–1 by construction, confirmed directly with Alex, no separate decision needed.
- **`focus`/`consist`/`hit`/`eff`** (eye spacing, nose tilt, head roundness, mouth length — all also count/total ratios stored 0–100 in `metricsFor()`) — **a real gap I initially got wrong**: first claimed "no code change needed, the relative scale behaves the same as a fixed 0–1/0–100 scale in the typical case" — checked that live and it was false. `chNorm()`'s relative fallback divides by the player's own HISTORICAL max for that metric (`scaleMax[key]`, built from weekly-bucket history), which is very often well under the metric's true ceiling — so a raw value like `focus=50%` could read as `n≈0.72` on the relative scale instead of a true `n=0.5`. Added all six to `CH_THEORY_MAX` for real once the gap was confirmed, not assumed.
- **Still open: `lucky`** (mouth curve/smile — empirical hit rate ÷ theoretical chance) — no natural bound found yet, discussion didn't reach it before the session moved on to the outlier/visual work below. Needs its own pass.

### 3. Outlier exclusion — attempts >500ms from the aim don't count toward the deviation-magnitude family

Alex, mid-discussion: "всё что больше 500 мс от 3.000 мы считаем выбросом и не принимаем в расчёт." New `CH_OUTLIER_MS = 500` and a `devErrs` (outlier-filtered) array inside `metricsFor()`, used for `early`/`late`/`earlyMode`/`lateMode`/`earlyTail`/`lateTail`/`earlyCV`/`lateCV`/`gcv` — everything computed straight FROM the raw ms error. Deliberately NOT applied to `n`/`hit`/`wins` (a huge miss is still a real miss for hit rate's own denominator — dropping it would inflate hit rate, not clean it up), `lucky` (same denominator reasoning), `focus`/`consist` (sign-change / did-it-get-closer comparisons between consecutive attempts, not a magnitude reading), or `earlyShare`/`lateShare` (still legitimately an early or late miss, just an extreme one). `earlyTail`/`lateTail`'s own P3/P97 percentile indices had to switch from indexing by the outer `n` to indexing by `devErrs`' own (possibly shorter) length — otherwise the percentile would silently misindex once the array being sliced no longer matched `attempts.length`. `lucky`'s internal `rangeSpan`/`slots`/`dispersion` calculation was deliberately kept on the RAW (unfiltered) range, since that's a separate, not-yet-decided question — the refactor added a second, explicitly-unfiltered `sortedErr`/`rawLoIdx`/`rawHiIdx` so this doesn't silently inherit the outlier cut just because it used to share `earlyTail`/`lateTail`'s old local variables.

Verified live (real browser, synthetic dataset mixing normal and >500ms-outlier attempts): confirmed the outlier values are excluded from `early`/`late`/`earlyTail`/`lateTail`/etc. by hand-checking the geometric mean/percentile math against the filtered set, and that `chFaceParamsAt()` still runs without error afterward.

### 4. Real bug found and fixed along the way: `CH_LEGEND`'s head-width description was backwards

While verifying the gcv→scaleFactor mapping (to answer "which feature does consistency drive, and does the direction make sense"), found the legend text said "the more predictable, the NARROWER the head's oval" — the actual code (`headWRaw = 68·scaleFactor/√ecc`, scaleFactor bigger when MORE consistent) does the opposite: more consistent → WIDER head. Fixed both RU and EN legend rows to match the real behavior instead of the stale claim.

### 5. "Optimal zone" — green highlight for traits reading as excellent

Alex: "подкрашивать зелёным, когда черта находится в оптимальной зоне, скажем ±5% от максимального." New `CH_OPTIMAL_THRESHOLD = 0.95` and an `optimal` map computed in `chFaceParamsAt()` from each metric's REAL (never demo-overridden) `chNorm(...)` goodness — since `chNorm` already orients every metric so `n=1` is unambiguously "best" (verified against all 16 in-use metrics' `CH_FAV` up/down direction before relying on this), no per-metric polarity logic was needed at the highlight layer itself. A shape driven by more than one metric (head: scaleFactor+eccFactor; mouth: mouthLen+lucky; each eye: eyeOpen+eyeWidth for that side) only counts as optimal when ALL of its drivers are — "in the zone" means the whole visible shape, not half of it. `chFaceSvg()` reads `p.optimal[key]` to swap `var(--text-primary)`/plain fill for `var(--good)` per shape (brow L/R, ears L/R, pupils L/R, head, mouth, nose — color only) — `chApplySvgParams()` (the legend-demo sweep's per-frame updater) never touches stroke/fill at all, only position/size, so a demo that forces a feature to its extreme can't make it flash green just because the override happens to land on 1; the color stays anchored to the real data throughout.

Scope, confirmed with Alex: **eyes only** get a shape change (see below) — ears/pupils/head/brow/nose/mouth stay their normal shape, just recolor. `eyeDx` (eye spacing) has a computed optimal flag but no dedicated shape of its own to apply it to.

### 6. "Laughing eye" shape for optimal eyes — three design iterations before landing

Alex, after the color-only version: "делать их не круглыми или овальными, а человеческими по форме, как бы смеющимися, с острыми углами." Iterated visually (PNG mockups sent for a pick, not guessed blindly) through three real attempts:

1. **First attempt** (symmetric lens, corners raised uniformly) — real bug, self-caught before shipping: raising the corner level shifted the shape's own true vertical center away from where the pupil was drawn (pupil visibly off-center). Fixed the algebra (asymmetric top/bottom arc amplitude) so the corners could still lift while the true center stayed pinned to the pupil's position — verified numerically (`true center == cy` to 0.0 difference) — but Alex rejected the whole APPROACH next: "надо не уровень поднимать, а изгибать" (bend the stroke, don't raise the flat corner level), sending it back toward the original "D" mockup reference (thin crescent, level corners).
2. **Second round** — three new candidates (E: level corners with a hooked/flicked tip near each end; F: only the OUTER corner lifts, like a real smiling eye; G: the whole D-silhouette rigidly tilted). Alex picked **E**. Caught a real regression in my OWN verification process here too: the first E mockup batch still drew the pupil at the OLD fixed `cy` for all three candidates without recomputing each shape's actual center — Alex, correctly furious, called this out explicitly ("центр это от края до края по вертикали... а не по горизонтали. записывай, если память тупая") — refined mockups with the pupil at each shape's real numeric center followed.
3. **Final build**: `chEyeLensPath()` — two CUBIC segments for the top edge (meeting at a shared peak `cy-ry`, each with an early "hook" tangent near its own corner) plus one shallow QUADRATIC for the bottom edge; corners sit at plain `cy` (not raised — the smile read comes purely from the hook tangent). Closed-form true center (verified against a from-scratch Python min/max-of-sampled-points check before trusting the algebra): the top edge's peak is exactly `cy-ry` and the bottom edge never dips below the corners' own `cy`, so the true center is `cy - ry/2` — `chEyePupilCenterY(cy, ry)`. Wired into BOTH `chFaceSvg()` (tag choice: `<path>` when optimal, plain `<ellipse>` otherwise) and `chApplySvgParams()` (tag-agnostic eye updater + matching pupil-cy branch), so the legend-demo sweep keeps the lens shape and its correctly-centered pupil in sync too.

A live-DOM check during this pass produced a false alarm worth recording as a lesson: injecting the face SVG into an ad-hoc test `<div>` (via `document.body.innerHTML` replacement, then later a plain `appendChild`'d container) broke CSS custom-property inheritance for that div specifically — `var(--good)` resolved to plain black instead of green, and the eye's fill looked like a hollow double-peak "M" instead of a solid dome. Re-tested through the REAL render() pipeline (`#faces`, the actual container the app uses) instead of an ad-hoc one, and both the color (`rgb(31,122,61)`, correct) and the shape (confirmed via `getBBox()`/path inspection) were fine — the bug was in the throwaway test harness, not the shipped code. Lesson for next time: verify a CSS-var-dependent render through the app's own real container, not a hand-rolled one, before concluding something is broken.

### 7. Deploy status — the 2026-09-15 "syncs automatically" correction was wrong (or stopped being true)

Checked live before answering Alex's "всё, что записал — осталось репорт вгрузить?": `curl`'d `https://playoftime.online/report.html` and diffed against the local file — genuinely DIFFERENT (live 176758 bytes vs. local 190728 bytes, live missing this whole session's work). This directly contradicts the standing "Correction" note added 2026-09-15 ("webapp syncs to the live server automatically, no manual deploy step"). Told Alex plainly rather than assume the old note still held; he has since uploaded `report.html` manually and confirmed ("готово").

**Treat the 2026-09-15 "auto-sync" claim as unreliable going forward** — whatever mechanism made that true then either isn't running consistently or was specific to that moment. Keep doing what that note itself already recommended (curl+diff against the live file before asserting deploy status either way) rather than trusting either "it's automatic" or "it's manual" as a standing fact.

### Files touched
`webapp/report.html` only.

### Known open / deferred
- **`lucky`** is the one remaining metric with no theoretical scale — still on the old relative-to-compared-periods fallback, and it directly drives the mouth/smile. Needs its own dedicated round with Alex.
- The `optimal`/green-highlight feature and the new eye shape are verified through direct DOM/computed-style/geometry inspection (not eyeballed) against synthetic data, but not yet seen by Alex against his own REAL play history on the live page — worth a look once he's back on it after this deploy.
- Deploy status must be re-verified live (curl+diff) each time from now on — don't carry forward either "automatic" or "manual" as an assumption; see item 7 above.

## What Was Done (17 September 2026 — webapp only, no native build)

Long session, `webapp/report.html` only, driven by a rapid sequence of live-tested bug reports and a final geometry-safety feature. Interactive testing was done through published Artifact links (not just local snapshots) so Alex could click a real URL rather than open a file.

### Tooltip flexible positioning + amber styling
`showMetricTooltip(anchorEl, text, clickEvent)` rewritten to measure real `spaceAbove`/`spaceBelow` against the tooltip's own `offsetHeight` and flip above/below accordingly, instead of clamping to the viewport top. `.metric-tooltip` recolored to match the native About-screen/cheer-up amber (`background:#F5C02E; color:#2A1E00; border:1px solid #FFD35E`).

### Click-through hit-testing bug on nose/mouth (real bug, found only on the published Artifact)
Initial extensive local testing (synthetic clicks, attribute polling) failed to reproduce Alex's report that tapping near the nose/mouth sometimes triggered the head-ellipse's own demo animation instead. Root-caused only after testing the actual published Artifact with a real click: `fill="none"` stroke-only elements (brow lines, nose lines, mouth path) have a hit-test area limited to their thin visible stroke — anywhere near-but-not-on them falls through to the solid-fill head ellipse underneath. Fixed with invisible wide-stroke (`CH_HIT_STROKE_WIDTH=16`) twin elements (`chHitLine`/`chHitPath`, `data-hit-overlay`-marked) positioned identically and painted after the head, for both brow lines, both nose-line segments, and the mouth path. `chApplySvgParams()` updated with matching `[data-hit-overlay]` selectors so the twins track the live/demo geometry too.

### Missing period-picker escape route when a side has no data
`buildFaces()` previously replaced the WHOLE two-column block — including the only period-picker/pager controls — with a "no data" message whenever either side's data was empty, leaving no way to pick a different period. Restructured so the label-row (period button + ‹›pager) always renders; only the face-drawing portion falls back to a `.face-empty` placeholder.

### Stale "Today" data after paging back then forward
Three different places computed "today" three different ways (`fmtDate(new Date())` vs. `b.max`, the player's own last-attempt date): `setDefaultRanges()`, `applyDefaultDay()`, and `stepPeriod()`'s day branch. Paging back then forward to "Today" could silently re-show yesterday's data under a "Today" label. Unified under one `const REAL_TODAY = fmtDate(new Date());` defined once, all three call sites switched to it. Verified by direct `stepPeriod()` JS calls stepping through a full multi-day range in both directions, confirming both the UI-disabled-button and the function's own defensive check block the invalid transition correctly.

### Faces bottom-anchored, empty-state chain resolved by one root-cause fix
Alex's real ask (after a long back-and-forth over the empty-state placeholder's exact spacing) turned out to be satisfiable with a single change instead of continued CSS patching: `setDefaultRanges()` now walks backward day-by-day from `REAL_TODAY` until it finds a day with real attempts, and sets `state.aSel` to `{type:"today"}` if today has data or an honest `{type:"day", dayOffset:N}` otherwise. Since both sides now always render a real 240px face, the earlier "labels same height" / "remove empty caption" / "always two real faces" requests are all satisfied at once — the `.face-empty` placeholder essentially never triggers anymore under normal use.

Also added `chHeadCy(headH) = CH_HEAD_BOTTOM_Y − headH` (`CH_HEAD_BOTTOM_Y = 2·CH_CANVAS_HALF − CH_FACE_MARGIN = 216`) so the head ellipse's BOTTOM edge is always pinned at y=216 regardless of `headH` — growth only extends the top edge upward. This gives two side-by-side faces a single shared baseline for head-height comparison (Alex: "морды должны быть выровнены... снизу, чтобы было проще судить о высоте лица").

### Dashed safety square per face, worst-case-verified — and a real bug found in the fix itself
Added a dashed `<rect>` (`CH_FACE_SAFE_SIZE = CH_HEAD_BOTTOM_Y − CH_FACE_MARGIN = 212`, from `[4,4]` to `[216,216]`) as the first child of each face's SVG — a guaranteed upper bound the face can never exceed, with minimal margin at the true worst-case (max-inflated) geometry.

Verified via `getBBox()` injection (`chNorm` monkey-patched to force `gcv`/`hit`/ear metrics to their true extremes) rather than just trusting the algebra — first pass found `head.bottom=246.1`, ~30px past the square's own `bottom=216`, an apparent violation. Root-caused, not guessed: `chApplySvgParams()` (the function driving every frame of the legend's live demo-sweep animation) updates the head ellipse's `rx`/`ry` on every call but never touched its `cy` attribute — so during a demo sweep the head's vertical position stayed frozen at whatever `chFaceSvg()`'s initial (real-data) render had set, while its size kept changing underneath. The bug only affected the animated update path; the plain initial page render was never actually unsafe.

Fixed with one line: `head.setAttribute("cy", cy.toFixed(1));` alongside the existing rx/ry sets. Re-verified three ways: (1) the horizontal worst case (`gcv`↑, `hit`↓) — ears land exactly on `x=4`/`right=216`; (2) the true vertical worst case (`gcv`↑, `hit`↑, maximizing `headH` to its 106 clamp ceiling) — head spans exactly `[4,216]`, `cy=110` reproducing the old symmetric system's own documented safe center exactly; (3) a real 20-frame sample of the actual live demo-sweep animation (not a synthetic single-frame override) — head's bottom edge stays pinned at `216` on every sampled frame throughout the sweep, top edge always within `[4,216]`.

### Files touched
`webapp/report.html` only.

### Known open / deferred
- The dashed-square feature and its fix are verified against synthetic mock data and the live demo-sweep animation, not yet seen by Alex against his own real play history.
- Live-deploy status not re-checked this session (see the standing "verify with curl+diff before asserting" rule above) — do that before assuming this work is or isn't already on `playoftime.online`.

## What Was Done (19 September 2026 — v13.87 → v13.90)

### Leaderboard table — sorting rewritten (asc/desc, correct order per column)
Alex: "в таблице лидборда не работает сортировка по возрастанию-убыванию, и часть столбцов вообще хер пойми как упорядочена."
- Root causes: (1) sorting was server-only and descending-only — repeat taps did nothing; (2) the **Luck** column displayed `luckyIndex` but the server sorted it by `combinedScore` (level*1000+task*100+SC), so the column looked random; (3) Acc% / Coins sorts were fine but there was no reverse.
- Fix (native only, no server change): the client fetches once (`sort=level`, same player set for every column) and sorts locally by exactly the value shown in the column (`sortedBestFirst()` in `LeaderboardRow.kt`; ties → level → quest → coins → playerId). Tap a new column = best first (▼); tap the active column again = ascending (▲) and back. `LeaderboardContract.State.descending` added; `SelectSort` no longer refetches.
- Rank number = real place in that column's ranking (ascending view shows N…1, not 1…N).
- Files: `LeaderboardRow.kt`, `LeaderboardContract.kt`, `LeaderboardViewModel.kt`, `LeaderboardScreen.kt`. Server `leaderboard.php` sort param is now unused by the app (left as-is; still fine for webapp).
- Build v13.88 (APK on Desktop). Build-verified only, not checked on device. Webapp Ranks table has its own sort — check it for the same Luck/combinedScore mismatch in a sync pass.

Follow-up (same day, v13.89): SC header coin is now centered in its column (arrow ▼/▲ overlaid at the right edge so it doesn't shift the center); `webapp/api/leaderboard.php` player cap raised 100 → 1000 so client-side sorting sees every player (**needs upload of leaderboard.php to the server**).

Follow-up (same day, v13.90): **Compare-with-player dialog** now shows Attempts and Started ("N d ago") for both players, plus a toggle "All history | Same level (N)" (shown only when the two players are on different levels; N = the lower level). Same-level mode compares BOTH players' stats at that level only (attempts, coins, hit rate, attempts/win, Lucky, start date of that level; Progress row hidden). Server: `webapp/api/leaderboard.php` now returns per player `firstAttemptAt` and `tiers{1..3}:{attempts,wins,scTotal,hitRatePct,luckyIndex,firstAttemptAt}` (grouped by attempt accuracy = game level, current epoch) - **leaderboard.php must be uploaded**; until then the dialog shows "—" for the new rows and the toggle does nothing useful. Native: `LeaderboardRow.tiers/firstAttemptAt`, `TierStats`, `LeaderboardScreen.kt` (`buildCompareMetrics(mine, other, tier)`).

## What Was Done (20 September 2026 — documentation only, no build)

Prepared the full handoff package for another Claude account working on this same computer: `C:\PlayOfTime\HANDOFF_2026-09-20.md` (copy in `webapp/handoff/`; tools in `webapp/handoff/tools/` — `make_snapshot.py`, `seed_leaderboard_sqlite.py`). It contains the live state (checked with `curl`: `report.html` identical to local, `leaderboard.php` live with `tiers`/`firstAttemptAt`, APK `v13.90` on Desktop), the table of accepted decisions (D29–D39 + standing rules), a file map for native/webapp/server, copy-paste verification commands, open items, and Alex's working rules. The Narrative Feature Decision Log artifact was extended with D35–D39 (15–19 Sep). A "START HERE" pointer was added at the top of both CLAUDE.md files. Fixed the 19 Sep section heading version range (v13.87 → v13.90; it wrongly said v13.88). Nothing in the app or server code changed today.

## What Was Done (21 September 2026 — quest block redrawn from Alex's sketch, v13.91)

Alex прислал эскиз блока квеста с размерами (1 «px» эскиза = 1 dp, экран 360 dp) и попросил взять с него ТОЛЬКО расстояния и выравнивания (цвета условные). `QuestProgress.kt` + `MainScreen.kt`:
- **Верхняя строка** 24 dp: подпись **«Quest L.T[.s]»** по центру (10sp; слово «Level» убрано — уровень уже в первой цифре), «?» 24 dp (было 26 + отступ 8) у правого края, центр текста на ~3 dp ниже центра «?».
- **Подпись → пипсы** 18 dp (от базовой линии до верха пипсов). **Пипсы:** шаг ячейки 10 dp (33 шт = 330 dp), кружок 5,5 dp, ряд по центру; на узком экране ячейка = width/33.
- **Низ пипсов → верх мишеней** 14 dp (→ верх цифр цели ровно 24 dp).
- **Мишени** 30 dp, ROW — перекрытие 25% (шаг 22,5 dp), группа на **истинном центре экрана** (сдвиг вправо от 09.09 для мишеней снят). Цель «3.00» — прижата ВПРАВО к группе, зазор 12 dp; подпись «3× in a row» — прижата ВЛЕВО, зазор 12 dp; вертикально центры цели/подписи = центру мишеней.
- **Низ** блока: от базовой линии цели до карточки секундомера 36 dp (16 dp в QuestProgress + 10 dp Spacer в MainScreen).
- Не проверено на устройстве (build-only).
- **Отчёт с мордочками переехал из Ranks в Analytics** (Alex 2026-09-21). Из таблицы Ranks убран вход (тап по СВОЕЙ строке больше ничего не открывает; по чужой — сравнение, как раньше). На Analytics блок сводки `SummaryHeader` переделан в две карточки в ряд: слева (2/3 ширины) цифры в две колонки — PLAY TIME / SESSIONS / ATTEMPTS / WINS и SERENITY COINS (с монеткой) / PACE OF PLAY / PRACTICE (тёмная плашка Pace of Play убрана), справа (1/3) кнопка «VIEW MY PROGRESS REPORT» (FaceIcon NICE 58 dp + оранжевый текст), тап открывает отчёт в WebView-диалоге; в режиме справки не срабатывает. `ReportDialog` вынесен в `ui_lib/ReportDialog.kt` (тело не менялось). Не проверено на устройстве.
- **Карточка отчёта на Analytics: мордочка выбранного периода, WINS/PRECISE** (Alex 2026-09-21). (1) Все значения левой карточки — один размер/начертание (14sp SemiBold). (2) Вместо смайлика на правой карточке — мордочка ВЫБРАННОГО периода: тот же `report.html` в новом режиме `?mode=face&fromMs=..&toMs=..&min=30` открывается маленьким WebView (`features/stats/ui/ReportFaceCard.kt`); страница считает по тем же попыткам, что и Analytics (границы периода = первая/последняя попытка из `AnalyticsData.periodFromMs/ToMs`), рисует одну мордочку на прозрачном фоне и сообщает нативу число найденных попыток через `PotFace.ready(count)`. Пока страница не ответила (нет сети / на сервере старый report.html) — показывается смайлик. Если попыток периода меньше 30 (`MIN_FACE_ATTEMPTS`) — страница размывает мордочку, вместо надписи «VIEW MY PROGRESS REPORT» текст «Please make N more attempts to see the report», тап отчёт не открывает. (3) Подпись «WINS» → «WINS/PRECISE», значение «побед/чистых троек» (`AnalyticsData.periodWins` — победы с допуском, новое `periodPrecise` — округлённая ошибка ровно 0). **Требует загрузки `webapp/report.html` на сервер.** Порог 30 и то, что при нехватке попыток отчёт не открывается, выбраны ассистентом.

## What Was Done (22 September 2026 — self-update button + LAST QUEST + FailCard face, v13.95 → v13.96)

Два запроса Alex.

### Самообновление вне Google Play
При каждом запуске (`MainViewModel.init`, один раз) приложение спрашивает
`https://playoftime.online/downloads/app_version.json` (статический JSON, без
PHP) — если там `versionCode` больше установленного (`BuildConfig.VERSION_CODE`),
в **левом верхнем углу главного экрана** появляется кнопка (`ui_lib/AppUpdateButton.kt`).
Тап скачивает APK по ссылке `apkUrl` из того же JSON в кэш приложения и сразу
открывает системную установку (`Intent.ACTION_VIEW` + FileProvider, тот же
authority, что у Excel-экспорта). Новое разрешение `REQUEST_INSTALL_PACKAGES`
в манифесте. `core/update/UpdateApi.kt` — best-effort, тот же принцип, что у
`LeaderboardApi`: любая сетевая ошибка → null, кнопка просто не появляется.

**Требует ручного шага после каждого релиза**: файлы `webapp/downloads/app_version.json`
+ APK под именем `play-of-time-latest.apk` нужно загружать на сервер вручную —
у этой сессии нет доступа к playoftime.online. Оба файла для сегодняшней сборки
(v13.95, versionCode 410) уже лежат в `webapp/downloads/` локально, включая
`README_update_deploy.md` с точной инструкцией, что и куда заливать после
каждого следующего релиза. Пока файлы не загружены — кнопка обновления нигде
не появится (это ожидаемо, не баг).

Дизайн-решения по ответам Alex: тап скачивает APK в приложении и сразу
предлагает установить (не открывает браузер); файлы для сервера готовит
Claude, загружает Alex/веб-сессия.

### LAST QUEST — финальный квест без индульгенции
`QuestProgress.kt`: если игрок на L3.5 (финальный квест игры) БЕЗ индульгенции
(`subStep == 0`) — заголовок вместо «Quest 3.5» показывает **«LAST QUEST»**.
В субквесте (`subStep > 0`, т.е. индульгированная версия) остаётся обычная
дот-нотация «Quest 3.5.N» — видно, что это ещё не чистый финал (выбор Claude,
подтверждён Alex). Раз в 3 минуты, пока экран открыт, подпись вспыхивает
зелёным (`resultGreenColor`) и плавно гаснет обратно (4 сек, `FastOutSlowInEasing`)
— бесконечный цикл через `LaunchedEffect`.

Не проверено на устройстве (build-only). Порог 30 попыток для карточки-мордочки
с прошлой сессии (21.09) тут не менялся.

### Файлы
`core/update/UpdateApi.kt` (новый), `ui_lib/AppUpdateButton.kt` (новый),
`core/KoinModule.kt`, `features/main/ui/MainContract.kt`, `MainViewModel.kt`,
`MainScreen.kt`, `AndroidManifest.xml`, `res/xml/file_provider_paths.xml`,
`features/main/ui/QuestProgress.kt`.
Webapp: `webapp/downloads/app_version.json` (новый), `webapp/downloads/play-of-time-latest.apk`
(новый, копия сегодняшней сборки), `webapp/downloads/README_update_deploy.md` (новый).

### Дополнение той же сессии — FailCard: мордочка вместо кольца-заглушки

Alex: "после неуспешной сессии, показывай морду этой сессии на промежуточном
экране, вместо херовины которая сейчас показывается в верхней части этого
промежуточного экрана." «Промежуточный экран» = `ui_lib/FailCard.kt` (показывается
после провала N-окна, до бабла); «херовина» = приглушённое кольцо "❍" — заглушка,
явно описанная в собственном комментарии кода как «нейтральный маркер паузы».

Заменено на мордочку (Chernoff-face) ЭТОГО провалившегося окна — тот же движок
`report.html?mode=face`, что уже используется в `ReportFaceCard.kt` (Analytics,
21.09). Вынесен переиспользуемый composable `ui_lib/SessionFaceView.kt`
(WebView + JS-мост `PotFace.ready(count)`, смайлик-заглушка пока страница не
ответила) — не интерактивный (без тапа/диалога, в отличие от ReportFaceCard),
просто визуальный якорь.

**Диапазон времени окна** — новые поля `FailCardData.fromMs`/`toMs`
(`getLocalTimeInMillis`, 0 = неизвестно). Считаются в `MainViewModel.onQuestWindowFailure()`
как min(startTimestamp)/max(endTimestamp) по `viewState.value.attempts` (это и
есть попытки провалившегося окна — тот же список, что даёt `phaseAttemptsBefore`).
Провалившееся окно всегда ≥ 33 попытки, так что мордочка практически всегда
успевает получить достаточно данных (min=1 передан явно, чтобы страница не
размывала лицо порогом в 30 попыток как на Analytics — здесь это неуместно,
данных заведомо достаточно).

Также отвечено на отдельный вопрос Alex: **нет**, старые версии APK не нужно
хранить на сервере — самообновление всегда читает ровно один `apkUrl` из
`app_version.json`, старые файлы можно просто перезаписывать/удалять.

Пересобрано: v13.95 → v13.96 (versionCode 411). Не проверено на устройстве.

### Известные нерешённые / отложенные
- FailCard-мордочка build-verified only — реальный провал окна на устройстве
  не воспроизводился в этой сессии, WebView-путь для fromMs/toMs не проверен
  живьём (тот же класс риска, что и остальной report.html-путь).
- Кнопка обновления не проверена на реальном устройстве — ни обнаружение
  новой версии (сервер ещё не в курсе), ни сам процесс скачивания/установки.
- Нет UI-индикатора ошибки, кроме тоста «Update failed — try again later» —
  если скачивание оборвётся, кнопка просто возвращается в исходное состояние.
- `LAST QUEST` — пульсация не проверена глазами (реальный 3-минутный цикл
  никто не досматривал в этой сессии).

## What Was Done (22–25 September 2026 — v14.06 → v14.12)

### 22 September — update button, session numbering, FailCard face
- **Self-update button invisible** — it was the first child of the main screen's outer `Box`, and the opaque content `Column` drew over it. Moved to be the last child (`MainScreen.kt`). Label "Update"/"Updating…" (`AppUpdateButton.kt`), kept short so it doesn't overlap the centered quest label (quest label position untouched, per Alex).
- **Session number now per calendar day** — main screen showed a lifetime global count ("Session #130" on a 10–15-session day). Root cause: three separate numbering formulas had drifted apart (`GetLogAttemptsUseCase` global, `AnalyticsUseCase` per-day since 23 July, `MainViewModel` raw DB id). One shared `core/utils/Utils.kt::perDaySessionNumbers()` now used by all three; `MainContract.State.currentSessionNumber` added.
- **FailCard face** — background "patch" removed, the face fills its container, then doubled (`faceDisplayDiameter = faceDiameter * 2f`, `FailCard.kt`).

### 25 September — streak-pressure research on real server data
Alex's hypothesis: landing a second target in a row is rare, it triggers reflection/fear of failure and should show up in play; and "on which attempt players pass two-in-a-row / three-in-a-row" could become a parameter.
- One-off read-only, aggregate-only script `webapp/api/research_streak_pressure.php` (ROW quests only, task 4/5 on levels 1–3). Win is recomputed like the game does (`is_true_three_win`, rounded to the level grid + tolerance of that attempt) — the `attempts.is_win` column is NOT reliable: native fills it from raw error without rounding, web differently.
- Results (16 298 attempts): attempts to pass — median L1.4 = 7, L1.5 = 5.5, L2.4 = 40, L2.5 = 55, L3.4 = 58 (3 players), L3.5 = 2 players. Clean-only (no eased tolerance): L2.5 median 365, L3.4 median 518 — ROW quests on L2/L3 are passed mostly via subquest easing. Some 2-attempt passes at L2/L3 are almost certainly left-hand auto-stop test runs (hand is not synced, can't be filtered).
- Reaction: on L2.5 after the first hit — larger error, more "early", shorter pause (rushing); after the second hit in a row — longest pause, lean flips to "late", 44% hit. Only 16 cases on L2, 8 on L3 — not conclusive.
- Conclusion given to Alex: "attempt at which two/three in a row is passed" — worth a parameter; "reaction to the second in a row" — not yet, too few cases.

### Exact attempt time on the server (was whole seconds)
- `sync.php`: new column `attempts.recorded_ms BIGINT NULL` (client start time, Unix ms). The column is created by `sync.php` itself on first request (self-migration, no DB console available). Old rows get the value only when their client resends them; never overwritten once set. `schema.sql` updated.
- Native: `LeaderboardSyncManager.backfillAll()` resets the sync cursor to 0 once (`LocalStorageService.isRecordedMsResendStarted`) so the whole local history is resent. Web already sends ms; its OLD attempts stay without ms (web does not resend history).
- Verified live 25.09: 4 080 attempts carry ms (Alex's phone history). ROW-quest attempts still have none — they come from other players/devices; exact pauses will accumulate as people play ROW quests on v14.12+. Research script left on the server to re-run in 2–4 weeks.

### Files
Native: `MainScreen.kt`, `AppUpdateButton.kt`, `Utils.kt`, `GetLogAttemptsUseCase.kt`, `AnalyticsUseCase.kt`, `MainContract.kt`, `MainViewModel.kt`, `FailCard.kt`, `LocalStorageService.kt`, `LeaderboardSyncManager.kt`.
Server: `webapp/api/sync.php`, `webapp/api/research_streak_pressure.php` (new, temporary), `webapp/api/schema.sql`.
APK: `play-of-time-v14.12.apk` on Desktop.
