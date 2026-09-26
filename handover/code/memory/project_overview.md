---
name: project-overview
description: "Play of Time Android app — tech stack, project layout, current focus areas, and durable working preferences from Alex"
metadata: 
  node_type: memory
  type: project
  originSessionId: 0a189dfe-905e-4ed9-983f-0c1f416bf451
---

# Play of Time

Native Android stopwatch-based training app rooted in Gellerstein's psychotechnics. User taps RUN, waits ~3 seconds, taps STOP, tries to hit exactly 3.000 seconds. Tracks accuracy, calculates Serenity Coin Metric (SCM), progresses through 15 quests across 3 levels.

## Stack
- Kotlin, Jetpack Compose, Room, Koin, MVI (BaseViewModel + Contract)
- Project path: `C:\PlayOfTime\time_mvp-quest_dev`
- Build: `.\gradlew assembleDebug` — JDK at `C:\jdk-21.0.5+11`
- Emulator: `Medium_Phone_API_36.1` (1080x2400). Physical debug device connected via adb, package `ru.iflex.timemvp.quest`
- Auto-versioning in `app/version.properties` — bumps patch after every successful build. APK named `play-of-time-v<versionName>.apk`

**Why the versioning matters:** The auto-bumper runs AFTER a successful build, so the APK on disk uses the PRE-bump version. If `version.properties` shows `versionPatch=54`, the newest APK is `v11.53.apk` (not v11.54). This trips me up regularly — verify by listing `app/build/outputs/apk/debug/` before reporting the APK path.

**How to apply:** after every build, run `ls -t app/build/outputs/apk/debug/` and report the top result. Never compute the version from `version.properties` alone.

## Layout
- `features/main/` — main stopwatch screen (MainViewModel/MainScreen/MainContract)
- `features/stats/` — Analytics screen (StatScreen.kt + StatsViewModel + AnalyticsUseCase)
- `features/settings/`, `features/about/` — small screens
- `ui_lib/` — shared Compose components (StopWatchButton, CheerUpBubble, HandToggle, SessionSummaryOverlay)
- `core/utils/` — quests (`Quest.kt`), accuracy levels, cheer-up enum/messages, error-pattern detection
- `core/data/LocalStorageService.kt` — SharedPreferences wrapper (single Koin binding, sole persistence for non-DB state)
- `database/TimeDatabase.kt` — Room DB (TryDao, SessionDao, StatisticDao). All queries live here directly (Room migrations MIGRATION_1_2 … MIGRATION_5_6 in KoinModule)
- `use_case/` — business logic
- `scratchpad/` — Python tooling (excel_to_db.py etc.), NOT part of the app

## Reference documentation (Google Drive, folder "play of time", ID `1aC9cv460P-xKaI97zOKANlyBLrJUmk8C`)
- `measures.docx` — metric formulas
- `parametr_table.docx` — 38 parameters with exact calculations
- `list_of_quests.docx`, `quests_oder.pdf` — quest system spec, adaptation flowchart
- `cheer_ups.docx` — cheer-up spec (superseded by in-code work as of 2 July 2026)
- `основной экран.pdf`, `Аналитический экран.pdf` — screen design prototypes

Cross-check work against these when adding new features. Most of the "invisible" domain rules live in these docs, not in code comments.

## Domain shorthand

- **Quest** — one of 15 challenges: L{levelId}.{taskId}, taskId ∈ 1..5, levelId ∈ 1..3
- **Subquest** — same quest but with relaxed conditions (larger N or non-zero t). Enters after a window failure at baseline; can nest up to 3 steps deep (N=99 or t=3ms max)
- **Baseline** — the original (non-relaxed) parameters of a quest
- **Rule 1 / 2 / 3** — subquest win outcomes: (1) already at baseline → advance; (2) subquest win with baseline-quality result → jump to baseline + advance; (3) subquest win but not baseline-quality → one step tighter, stay in subquest
- **Clean three / true three** — exact 3.000 s hit (endTime - startTime == aimTime). Different from "tolerance win" which counts wins within ±t. Always cross-check which one a metric uses when reading formulas — most use clean threes
- **N-window** — the current adaptive-attempts window (33 / 66 / 99). One session = one N-window since v11.11
- **HOORAY / RESET / OK button states** — post-event display modes of the main stopwatch button

## Active focus areas (as of 2 July 2026)

Recent session finished a big cheer-up system rework and analytics polish. Both are live in v11.54.

Open threads (from CLAUDE.md "What Still Needs To Be Done"):
1. Main-screen design alignment against `основной экран.pdf` — not yet compared
2. Sparkline logic per period — currently always shows last 5 sessions, design wants variability per period
3. Analytics screen: PACE OF LEARNING sparklines / typography verification against prototype
4. Quest 2.2 first-attempt bug — never reproduced, unclear symptom
5. TEMP DEBUG hack in `MainViewModel.kt` (left-hand auto-stop for L2/L3 tuning) — Alex will tell when to remove
6. About screen and Google Play prep content — from `about4googleplay_rus_and_eng.docx`

## Sensitive files — don't casually delete

- `SessionStatisticItemState.kt` — referenced by `GetLogAttemptsUseCase`, do not remove even if it looks unused
- `deflection` field in `TryDatabaseModel` — kept for DB schema compatibility, no longer written to but code paths still touch it
- `HandToggle` — legitimate feature (tracks user's playing hand for later stats), do not confuse with the deleted blind-mode `deflection` UI
- Python scripts in `scratchpad/` — one-off tools, kept as historical inventory. Do not port them into the app path
