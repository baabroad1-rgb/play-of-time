# Webapp ↔ Native parity audit

`webapp/app.js` vs native, both in this repository. Native source: `android/app/src/`
(live, cannot lag). Written spec: `docs/webapp_handoff.md` (lags at v12.87 — it is
hand-written, so when it and the Kotlin disagree, **the Kotlin wins**). Goal: webapp
maximally matches the native app in **look and behavior**.

**This file is the single place where parity status lives.** Keep it current; it is
what tells anyone how far the web is behind.

**2026-09-26 — зеркало исходников удалено.** До этого дня нативный код копировался
в `webapp/reference/native-src/*.kt` вручную, потому что веб-сессия не видела папку
Android-проекта. Теперь оба проекта в одном git-репозитории, копия не нужна и удалена.
Она отставала трижды: 2026-08-13 (Lucky Index и метка SubQuest расходились неделями,
незамеченно — зеркало показывало старые версии как «актуальные»), тогда же на 5 из 11
файлов, и к 2026-09-26 на 7 из 12, `AnalyticsUseCase.kt` — на 1061 строку. Карта
«что где определено» — в `webapp/README.md`.

**2026-08-13** (историческая запись): зеркало обновлено под живой код (отставало на
5 из 11 файлов — см. `TASKS.md` P0-3). Тогда же исправлены два настоящих багa паритета,
найденных при обновлении: формула Lucky Index и метка «SubQuest» в логе сессии
(`app.js` стал `20260813a`).

## Сверка веб ↔ натив 27.10.2026 (Алекс: «таблицу, что различается интерфейсно-функционально»)

Сверено по коду: натив `android/app/src/` против `webapp/app.20261025a.js`.

**27.10.2026 — все пункты перенесены в веб** (Алекс: «правим веб под натив по
всем пунктам»), `app.20261027a.js` / `styles.20261027a.css`. Проверено в Chromium
настоящими нажатиями RUN/STOP с управляемыми часами (L3.1 без лимита и закрытие на
99-й, карточка провала и sorry только по OK и только с Cheer-Ups, Rule 3, близнецы,
прогресс-бар) и прогоном экранов (листание периодов, лидерборд и сравнение на
подменённом ответе сервера, настройки, выгрузка Excel). Попутно найден и исправлен
№19 (в таблице ниже).

| № | Где | Натив | Веб | Вес |
|---|---|---|---|---|
| 1 | Квест L3.1 | без лимита попыток и без смягчения (Алекс 07.07) | окно 33 попытки, при провале смягчение 66/99 | игра |
| 2 | Автозакрытие сессии у квестов без лимита (L1.1, L1.4, L1.5, L2.1, L3.1) | 99 попыток | 33 попытки | игра |
| 3 | Победа в субквесте с шагом назад (Rule 3) | сессия засчитана, палец в дереве зелёный | палец вниз | игра |
| 4 | Чип «wow, twins!» | одинаковая величина промаха с любой стороны (−0.05 и +0.05) | только одинаковые со знаком (овалы в логе совпадают) | мелочь |
| 5 | Автомасштаб прогресс-бара | под последний результат: раздвигается/сужается, старт — медиана 30 последних попыток уровня | по худшей из 8 последних попыток сессии ×1.25 | вид |
| 6 | Beats per second | меняется 1–16 при включённом звуке или вибрации | замок, 2 (решение Алекса 30.07) | решение |
| 7 | Звук sorry и карточка провала | после тапа OK; только при включённых Cheer-Ups | звук сразу на последней попытке; карточка и при выключенных Cheer-Ups | мелочь |
| 8 | Analytics: листание периодов ‹ › и подпись (Session #N / дата / неделя / месяц) | есть (09.09) | нет, только текущий период | аналитика |
| 9 | Analytics: кнопка EARN MORE SERENITY внизу | есть | нет | мелочь |
| 10 | Analytics: карточка Distribution внизу | нет | есть | решение |
| 11 | Analytics: SC/att | +0.1 | +0.123 | мелочь |
| 12 | Лидерборд: список | таблица, 4 колонки сразу, сортировка тапом по заголовку и обратно | 4 кнопки сортировки и одна колонка | интерфейс |
| 13 | Лидерборд: сравнение | «All history / Same level (N)» (19.09), строки Attempts и Started | без переключателя и этих строк | интерфейс |
| 14 | Лог главного экрана: заголовок сессии | дата, время начала HH:mm, палец пройдена/нет | дата одна сверху, без времени и пальца | мелочь |
| 15 | Приветствие при первом запуске | новый расширенный текст | старый короткий | текст |
| 16 | Поздравление после квеста | 3 варианта фразы на параметр | 1 вариант | текст |
| 17 | Подбадривание после субквеста | абзац «Worth noting: the hits landed close enough together…» | нет | текст |
| 18 | Excel: названия колонок | Session number, Attempt number, Attempt result, ms, Error time, ms, Serenity coins | Session, Attempt, Attempt result, Error time, Serenity Coin | мелочь |
| 19 | Analytics: дерево сессий | вся история при любом периоде | только сессии выбранного периода | аналитика |

Уточнения, всплывшие при переносе:
- п. 8: в вебе Analytics → Session показывал только ещё открытую сессию (после её
  закрытия — пусто); теперь, как в нативе, последнюю сессию и листание назад.
- п. 17: текст подбадривания в нативе считается, но на экран не выводится (с 01.09
  вместо пузыря — жёлтая подсветка), так что абзац «Worth noting» не виден нигде.
- п. 18: в нативе «Attempt result, ms» и «Error time, ms» — целые миллисекунды
  (3042, 42); веб писал секунды строкой («3.04», «+0.04»). Приведено к нативу.
  Дата — формат натива YYYY-MM-DD, в местном времени. Натив писал дату и время в
  UTC (у московского игрока на 3 часа меньше) — по решению Алекса 27.10 натив
  тоже переведён на местное время (`ExportToExcelUseCase`).
- п. 6: в нативе в поле Beats per second можно было ввести 0 — StopWatch делит
  1000 на это число при создании, приложение падало при каждом запуске. Исправлено
  27.10 (Алекс: «да чини»): поле принимает только 1–16, сохранение и чтение
  ограничивают 1–16, уже сохранённый 0 читается как 2. Веб 0 не принимает.

Совпадает (проверено): формула SC; правила квестов 1–3, смягчение и откат; Lucky Index;
чипы ошибок; рожица на кнопке; About; справка (кроме лидерборда — п. 12); настройки,
экспорт и стирание; нижнее меню; графики Mean error / Faces / Lucky / Hit / Pace и все
правки 18–25.10; карточка провала и итоги сессии (тексты).
Платформа, не расхождение: самообновление APK, переезд с 14.x, Game Mode тача —
в вебе свои механизмы (смена имени скрипта, дизеринг по замеру).

Legend: ✅ matches · ⚠️ partial/diverges · ❌ missing. Priority P0 (fairness/core) →
P3 (polish). Items are worked top-down, one at a time.

## A. Behavior

| # | Area | Status | Note |
|---|---|---|---|
| B1 | **L3 dithering + echo/re-roll** (§3) | ✅ done | Implemented + verified: 200k-trial replica shows 3.000 goes 0% → ~1.2% (flat) after dither. L3-only, echo/re-roll, live cell estimate from coalesced pointer events, `touchCellMs` persisted. |
| B0 | **Persistence lost on refresh** (found during B5) | ✅ done | `normalizeDb()` called `getQuest()` reading module-level `db` in its TDZ → threw → wiped to default on every reload. Now uses the merged object. Native restores across restart; web now restores across refresh. |
| B4 | ROW worstWin reset on miss (§4) | ✅ done | Reset `windowMaxErrorMs=0` on a miss for adaptiveT quests → Rule-2 jump-to-baseline works. |
| B5 | Neighbor detection (§8) | ✅ done | Adjacent non-wins with \|errors\| one cell apart → yellow oval; verified alongside twin green oval. |
| B3 | Session auto-close length (§6) | ✅ ok | On review already correct: window-fail closes at effective N, capped at 99. |
| B9 | Histogram green = only exact-3.000 center (§6) | ✅ ok | On review already correct: uses exact `isTrueThree`, so only the center bucket is green. |
| B2 | Kernel event timestamp | ⚠️ | Uses `performance.now()` in pointerdown (good) but not `event.timeStamp`. Minor latency; revisit with progress-bar work. |
| B6 | Cheer-up trigger family (§10) | ✅ done | Full native flow: win → green **HOORAY!** → **GreetingView** → next quest with amber **cheer-up bubble**. Ported `describePhase`, rotating openers, all 11 triggers, `**bold**` highlight, praise, CTA gated L2.2+. Verified end-to-end (L1.1→L1.2). |
| B7 | `questWasIndulged` usage (§4) | ✅ done | Now selects AFTER_QUEST / AFTER_SUBQUEST_TO_BASELINE / AFTER_SIMPLIFIED_QUEST_DONE. |
| B6b | GreetingView 7-param interpretation table (§ GreetingView) | ✅ done | Precision/Luck/Excitation/Inhibition/Focus/Consistency/Pace session-vs-history table (unlocks L2.2+), verdict coloring + conclusion. Verified on L2.2. (Native's per-param rotating sentence banks condensed to one conclusion line.) |
| B8 | Vibration on number fixation (v12.34) | ✅ done | Haptic pulse fires when the number fixes (on record), not on the STOP press; gated by the Vibration setting. |
| CACHE | Cache-busting version query | ✅ done | `index.html` used a fixed `?v=20260712-webapp` → browsers never refetched changed CSS/JS. Bumped to `20260728-webapp`; **bump on every deploy**. |

## B. Look

| # | Area | Status | Note |
|---|---|---|---|
| L1 | Quest Progress archer metaphor (§6.1) | ✅ done | Ported native as inline SVG: layered bullseye targets (gap for IN_N/ONE_ONLY, 25% overlap for ROW), hit = green wash + check badge; depleting arrow bundle (11 piled / 1–3 separated); info line `Level N · quest/subquest T of 5` + `aim: <amber> [±t] <·|in row ×> <green p/c> · left <green>/N | no limit`. Verified L1.2 (2 targets, 11 arrows, correct line). |
| L2 | Distribution histogram (§6.6) | ✅ done | Matched native: thin adaptive bars (0.5–3px) one per error value, center-only green, zone gradient, **dynamic nice-step Y scale on the right + data-aligned gridlines** (removed fake `5 4 3 2 1`), maxCount floored at 5. Verified (ticks 1–5, 1 green bar). Drop animation still TODO (polish). |
| L3 | Progress bar over timer digits (§6.4) | ✅ done | Horizontal bar over digits, window 3.000±half, Auto scale per level (L1 ±600, L2 ±150, L3 ±60). Green center line, marker colored by result. Verified (3.2 s → marker 66.8%, late-blue). Settings toggle/manual presets still TODO (L9). |
| L4 | Analytics layout (§7) | ✅ done | Analytics label in sticky pill; Range card = attempts-ratio bar + gradient strip red→blue + green serenity line + inline P3/P97 + modes; Pace split into Learning/Play; hierarchical Year→Month→Day→Session tree with lucky/mean/att4win + ▲/▼ success finger. Verified. |
| L5 | Cheer-up bubble visual (§10) | ✅ done | Amber translucent bubble + × + CTA pill (done with B6). |
| L6 | Bottom nav balance arrow (§6.9) | ✅ done | Analytics tab shows a live red/blue/green balance arrow on all screens. |
| L7 | "?" interactive help mode (§6.2) | ✅ done | "?" opens a help overlay explaining Quest / Stopwatch / Distribution / Log / Top-bar. Verified open+close. |
| L8 | Level-complete / GreetingView (§6, §10) | ✅ done | Golden LEVEL COMPLETE block in GreetingView (done with B6). 7-param table = B6b below. |
| L9 | Settings screen (§11) | ✅ done | Native order: Cheer-ups, Sound, Vibration, Beats, Numeric time, Progress bar + scale chips, Playing hand, Touch-sampling meter, Erase-all (Excel + wipe). Verified. |
| L10 | About screen (§12) | ✅ done | About/Reaction/Serenity/Luck/Learn more/Credits + version line. Verified. |
| L11 | Face icon animation (§6.4) | ⚠️ | CSS face-badge approximates; native animated SVG slide + wink/mouth. Acceptable, revisit. |
| L12 | Top metrics bar (§6) | ✅ | Balance / hit rate / hand / Serenity — close enough. |

## What is already good (keep)
Quests table + victory conditions, SCM formula + level factor, sessions/periods
(touching-session logic), effective N/tolerance + caps, subquest Rule 1/2/3 skeleton,
twins green oval, error-pattern chip texts, log rows, export (JSON/CSV/Excel), lucky
index formula shape, geometric-mean profiles.

## Status — all audited items DONE
Behavior: B0 ✅ B1 ✅ B2 (ok) B3 (ok) B4 ✅ B5 ✅ B6 ✅ B6b ✅ B7 ✅ B8 ✅ B9 (ok).
Look: L1 ✅ L2 ✅ L3 ✅ L4 ✅ L5 ✅ L6 ✅ L7 ✅ L8 ✅ L9 ✅ L10 ✅ L11 (ok, CSS face).
Cache-busting ✅. Progress-bar-marker snap-to-notch fix ✅.

### Polish round 2 (done)
- ✅ Histogram "drop" grow animation on the freshly-landed bar.
- ✅ GreetingView per-parameter interpretation sentences (bold param + phrase) + conclusion.
- ✅ Serenity-coins tiered format (`0,85` / `5,2` / `234` / `1 234`) across top-bar, ΣSC, analytics, greeting.
- ✅ Top-bar Balance is now a leaning red/blue/green arrow (matches the nav indicator + native ±80° needle).
- ✅ Log SC column: edges computed (self-neighbour), newest row hidden, no more `0,00` placeholders.

- ✅ Session-end yellow SessionSummaryOverlay (attempts / clean threes / hit rate / best / early-late / time + Continue). Verified at the 33-cap.

## Latest-native-version alignment (28 Jul) — visually verified in real Chrome
The docs (v12.87) lagged the shipped app; aligned the main screen to current native:
1. ✅ Removed the top metrics bar (Balance → nav gauge, hit-rate/coins → Analytics, hand → Settings).
2. ✅ Removed the bottom period toggle (main screen is always TODAY; toggle lives on Analytics only).
3. ✅ Bottom nav rebuilt: exact native SVG icons (ic_run/stat/settings/about/export), slim 46px height, Analytics tab = semicircle BalanceGauge with needle.
4. ✅ Histogram area taller (42→58px).
5. ✅ Error-pattern/toast chip slimmed (6px vertical padding).
6. ✅ Real hooray.m4a / sorry.m4a sounds (from native res/raw) on win / fail.
7. ✅ Game launcher now shows the real app art + "▶ Tap to play".
8. ✅ Analytics reordered to native: SummaryHeader → CHANCE OF WIN → GEOMETRIC MEAN ÷ SPREAD → RANGE OF DEVIATIONS (+desc) → PACE OF LEARNING → tree.
- Bugs caught during visual pass: quest line `5/2` → capped `2/2`; Analytics/Settings/About values were dark-on-dark → set dark bg + light text base. Both fixed & re-verified.
- `.android-screen` converted from fixed 7-row grid to flex column (log grows, nav pinned).
- Version stamp → `?v=20260728c-webapp`.

## Launcher fix + app-run chrome (30 Jul) — verified in real Chrome
1. ✅ **Launcher "broken image" fixed.** `.game-launcher` used `aspect-ratio:1; height:60%`
   with an absolutely-positioned `.launcher-art` — collapses to 0 when the grid row has no
   definite height (deployed/other viewports), rendering as a broken/blank image. Rewritten
   so the `<img>` is **in-flow** (`display:block; width:100%; aspect-ratio:1; object-fit:contain`)
   and the button sizes from it (`width:clamp(180px,20vw,260px)`), so it can never collapse.
   PNG itself was always fine (192² RGBA, alpha-cropped corners — rounded-square, not circle).
2. ✅ **Black status bar + no browser chrome while playing.** `openGame()` now swaps
   `<meta theme-color>` → `#000000` and calls `requestFullscreen()` on the overlay (hides the
   address bar / browser nav on a real device tap). `closeGame()` exits fullscreen and restores
   the light `#f4f3ef`. A `fullscreenchange` listener closes the app if the user leaves fullscreen
   by gesture. (Manifest already `display:standalone` + black bg/theme for installed PWA.)
   Verified: theme-color `#f4f3ef`→`#000000` on open, back on close. Fullscreen is denied in the
   automation sandbox (no trusted gesture) but the wiring is correct for real devices.
- Version stamp → `?v=20260730a-webapp`. NOTE: `index.html` is itself unversioned, so the
  browser can serve a cached index referencing the OLD `?v=` — hard-reload (or `?nocache=`) when
  verifying, and on the live host set no-cache headers on `index.html`.

## Landing page + assets + progress-bar beak + haptics (30 Jul, vs native v12.90)
1. ✅ **Hero video layout fixed.** `.hero-media` had been lumped into the single-child
   centering flex group, so its 3 stacked children (label/caption/video) laid out in a
   **row** and squished ("съехала вёрстка"). Split it out to `display:flex;flex-direction:column`.
2. ✅ **Missing landing assets restored.** `assets/` physically had only app-art/hooray/sorry;
   `play-of-time-video.mp4`, `distribution.svg`, `serenity-curve.svg`, `sgg.jpg` were referenced
   but absent (grey video + broken images on local disk). Pulled from the live site
   `https://playoftime.online/assets/` into `assets/` (video 28.6 MB). NOTE: these are NOT in
   any repo — if the workspace is wiped, re-fetch from the live host.
3. ✅ **Image cropping fixed.** `object-fit:cover;min-height:240px` force-cropped the diagrams
   and portrait photo. Images now `height:auto` (natural aspect, zero crop); `.media-frame`
   `align-self:center` so the frame hugs the image (no grey margin); video reserves a 16:9 box
   with `object-fit:contain`. Verified: every image box aspect == natural aspect.
4. ✅ **App-run chrome = variant 2 (Fullscreen), chosen by Alex.** Kept the Fullscreen-API path
   (hides address bar + both system bars). Discussed the platform wall: web can't keep the top
   status bar while hiding only the bottom nav; only a native WebView/TWA could — rejected as
   overkill. `theme-color`→black on open (harmless in fullscreen), restored on close.
   ⚠️ Fullscreen only engages on a **real device tap** on the deployed site — Alex saw no
   fullscreen because playoftime.online still runs the OLD build; **needs deploy**.
5. ✅ **RUN/STOP press haptics.** `onRunButton` fires `navigator.vibrate(10)` on the RUN and
   STOP presses (via pointerdown = trusted gesture), gated by the Vibration setting. The
   Vibration setting stays **OFF by default** (`vibrationOn:false`) per Alex. Number-fixation
   pulse (15 ms) and metronome/fail pulses already existed. iOS never vibrates (no Web Vibration API).
6. ✅ **Progress bar = native fill + «клювик» (beak).** Replaced the dot marker (`pb-marker`,
   removed) with: a **fill** (`.pb-fill`) growing from the left to the current time
   (rounded-left/flat-right, colored white→result), and a down-pointing **beak** triangle
   (`.pb-beak`, 6 px base / 5 px tall) at the fill head with its tip on the track's top edge,
   colored to match. z-index: fill(1) < ticks(2) < green centre(3) < beak(4). `updateProgressBarDom`
   + the running `tick()` both set `pbFill.width` and `pbBeak.left`. At rest the beak sits at
   the start ("клювик в своём начале"). Verified via computed styles (green fill `999px/0`
   radius, green beak) — live screenshot blocked by a frozen-renderer glitch in the automation env.
- Version stamp → `?v=20260730f-webapp`.

## Session-end summary re-firing on every attempt — FIXED (30 Jul)
**Symptom (Alex):** "Session complete" pops up after *every* attempt.
**Root cause:** `closeSession(id, false)` set `isQuestComplete=false`, but `currentSession()`
only treated a session as closed when `isQuestComplete` was truthy. So a session closed by
window-fail or auto-close stayed "open" → every further attempt re-crossed the limit and
`updateQuestWindow` returned `type:"session"` again → summary re-fired endlessly.
**Fix:** added an explicit `closed` flag. `makeSession` seeds `closed:false`; `closeSession`
sets `closed:true` for ANY close (quest done / fail / auto-close); `currentSession()` returns
null on `closed || isQuestComplete`. Plus a `normalizeDb` migration that self-heals an
already-stuck last session (closes it if it reached its effective window/auto-close limit or
belongs to a different quest), so the message doesn't fire even once more on existing data.
Mirrors native (which nulls `currentSession` at session end via `awaitingSessionResult`;
`SessionSummaryOverlay.kt` still exists in v12.90, so showing the summary ONCE is correct).
Verified: app loads with no console errors; pure-function test of the migration+currentSession
logic passes all cases (stuck→closed, in-progress→open, indulged N within window→open,
cross-quest leftover→closed). Live screenshot blocked by a wedged automation renderer.

## Parity pass from Alex's native v12.90 screenshots (30 Jul)
Alex supplied 7 native screenshots (Play, Analytics all-time, Analytics month, Settings ×2,
About ×2). Aligned webapp to them:
- **Progress bar** ticks: 10 segments (tick every 10%), was 20.
- **Settings labels**: "Numeric time while running", "Seconds progress bar" (were shorter).
- **Progress-bar scale chips**: native set Auto · ±10 · ±20 · ±50 · ±100 · ±250 · ±500 · 2–4 s · 0–4 s
  (ms half-windows; were ±0.1/±0.3/±0.6). `progressWindow` already reads `Number(scale)` ms.
- **Playing hand**: "Left"/"Right" (were "L"/"R"); native description added.
- **Beats per second**: LOCKED per Alex — 🔒 dim fixed value "2" ("no more than 16"), slider removed.
- **Touch sampling rate**: native big "N Hz" card (green) + "Drag = ceiling · press&hold = baseline",
  live-updating from `touchCellCached` via pointer listeners, plus the full native dithering copy
  (5 paragraphs + 120/250/500/1000 Hz table). Replaced the terse "Measured cell / L3 dither" block.
- **Settings → Data**: reduced to a single "Erase all data & start over" (Alex) — the Excel/CSV/JSON
  export + JSON-import buttons removed (export lives in the bottom Export tab; native has only erase).
- **About**: repainted **amber** (`#F2B70D`, dark text) per Alex (native screen 6); panels flattened.
  Full native copy: About / Self-regulation, not reaction / Serenity / Luck Coefficient /
  **Beyond the game** (added) / Learn more / Credits; version line "Version 12.90 · 30 Jul 2026".
- Verified via DOM + no console errors (live screenshot still blocked by the wedged automation renderer).
- Version stamp → `?v=20260730i-webapp`.
- Still to check against the screenshots (not yet confirmed): Settings flat-list vs card look; the
  extra sticky "About" title (native has none); main-screen log green-DOT for tolerance wins;
  Analytics "GEOMETRIC MEAN ÷ SPREAD" "÷2.6" format + header PACE OF PLAY / PRACTICE wording.

## Log + Analytics parity (30 Jul, items 3–4)
- **Log green-dot for wins** — already correct: `renderLogRow` draws `.hit-dot` (green `--win`,
  centred) for `isQuestWinAttempt`, which counts tolerance wins via `attempt.toleranceMs`
  (e.g. 2.998/3.003 under ±0.003). No change needed. Non-wins keep the coloured error line.
- **GEOMETRIC MEAN ÷ SPREAD format** — native shows `−21ms ÷2.6` (division factor), web showed
  `−21ms · 2.6%`. Added `spreadFactor(stdPct) = (1 + stdPct/100).toFixed(1)` (GSD) and switched to
  `÷factor`. `formatMs` now emits `−21ms` (no space) to match; used only in the two deviation cards.
- **PLAY TIME / durations** — `formatDuration` now native compact `3h 34m 26s` / `1m 30s` / `26s`
  (was `3 h 34 min`, no seconds).
- **Card period tag** — each analytics card title now shows `/ session|today|month|all time` on the
  right (native), via `.card-period` + flex `.section-title`.
- Verified via DOM (titles carry "/ today", geom rows "0ms ÷1.0", PLAY TIME "0s") + no console errors.
- Version stamp → `?v=20260730j-webapp`.
- **Still bigger/open:** native PACE OF LEARNING (Month view, screen 3) is a real line chart with
  time axis + dots; web shows only the "SC/att +X" number. That's a charting feature — not built yet.

## Sound + vibration verification & PACE OF LEARNING chart (30 Jul)
- **Sounds verified**: both `hooray.m4a` (1.9 s) and `sorry.m4a` (1.0 s) decode via Web Audio
  `decodeAudioData` (AAC OK in Chrome). `playSuccessSound` fires on `result.type==="quest"`
  (every quest win, all levels/types — generic `onQuestWindowSuccess`); `playSorrySound` on
  `result.type==="fail"` (every adaptive-quest window fail). Both are intentionally NOT gated by
  the Sound toggle (event sounds, like native), and play inside the STOP gesture (autoplay OK).
  Rule-3 stepback ("contract") and session auto-close ("session") are silent by design.
- **Vibration verified**: `navigator.vibrate` supported; calls wired + gated by the Vibration
  setting at RUN/STOP press (10 ms), number fixation (15 ms), metronome tick (12 ms), too-early
  nack ([35,30,35]). Android only (iOS has no Web Vibration API). Desktop has no motor — verified
  by code + support check, not by feel.
- **PACE OF LEARNING line chart** (native Month screen 3): new `paceLearningSeries` (SC/att per
  day, or per session when the period is a single day) + `paceChartSvg` (orange polyline + dots,
  min/max y labels, first/mid/last date x labels, "● SC/att" legend). Shown above the SC/att value
  when ≥2 buckets exist; ≤1 bucket falls back to the number only. Verified: series buckets correctly
  (3 days → averaged 0.05/0.45/0.77), SVG renders (rising polyline, round dots), no console errors.
- Version stamp → `?v=20260730k-webapp`.

## Exact native colors + Analytics top toggle + sharp range + Sessions log tree (30 Jul)
- **Exact palette from native `ui_theme/Colors.kt`+`Theme.kt` (DARK)**: `--bg #000000`, card `--panel #1a1a1a`,
  sub-panel `--panel-3 #111111`, `--text #ffffff`, `--muted #a9a9a9`. **KEY FIX**: `--accent` was the
  sand `#e6a440`; native accent is `orangeColor #ff551c`. Split them: `--accent #ff551c` (orange —
  active nav/period-pill/toggle/hand/"?") and new `--sand #e6a440` (aim time `.qc-aim`, active "Auto"/
  scale `.chip.active` solid gold, coin). Active period/hand pill → solid orange + white text.
  `--early #de4264`, `--late #0ca8cc`, `--win #8eff51` already matched.
- **Analytics top row** = the full Session/Today/Month/All-time period toggle; removed the "Analytics"
  label. Root cause of the previously-broken toggle: a second `.period-toggle` rule (collapsed 96 px
  single-value pill, leftover from the old main screen) was overriding it — deleted, so the full
  toggle applies and switches the period for all cards below.
- **RANGE OF DEVIATIONS strip**: red→blue is now a hard edge at 50% (was a soft 45–55% blend), matching
  the crisp segments of the attempts-ratio bar above.
- **Sessions → "SESSIONS LOG"**, tree stays year→month→day→session; session node now reads
  "Session #N · HH:MM" (global sequential number) with a native subline "Xm Ys · Z% ✓ · LVL L · task T".
- **Vibration** (Alex clarified): the Settings "Vibration" toggle is a SEPARATE thing — the per-second
  metronome buzz. RUN/STOP is a distinct, **unconditional** two-shot (fires on start, fires on stop),
  NOT tied to any setting. Implemented: `pressHaptic()` = `navigator.vibrate(60)` with NO gate; called
  on RUN (after `startMetronome`) and on STOP/fixation (after `stopMetronome`'s `vibrate(0)`), so
  neither is clobbered. 60 ms because Android ignores <~20 ms. Metronome/too-early buzzes stay under
  the toggle. Verified by spying: a RUN press calls vibrate `[0, 60]` with the toggle OFF → 60 ms fires.
  ⚠️ Alex tests on **playoftime.online, which still runs the OLD build** — that is why "no vibration".
  It only reaches the phone AFTER DEPLOY.
- Verified: `--accent #ff551c`, full 4-button toggle, sharp 50% gradient, "SESSIONS LOG", no console errors.
- Version stamp → `?v=20260730l-webapp`.

## Tree scroll + Analytics distribution + geom parity (30 Jul)
- **Tree toggle no longer jumps to top**: `bindTree` saves `.analytics-content` `scrollTop` before
  `render()` and restores it after. Verified scrollTop 300 → 300 across a toggle.
- **Distribution histogram added to Analytics** under SESSIONS LOG. Extracted `buildDistribution`
  (shared bucketing) + `distributionChartHtml`; `mainMetrics` now also uses `buildDistribution`, so
  the main-screen and analytics histograms are byte-identical. New "DISTRIBUTION / <period>" card.
- **Main histogram geom strip**: removed the "Early"/"Absolute"/"Late" word labels (colours carry it).
- **Geom-mean parity fixed**: `formatGeom` used 1 decimal for centiseconds (18 ms → "0,0") and showed
  early as positive, so main "+0,0" disagreed with analytics "−18ms". The VALUES were already identical
  (both use `signedErrorMs` → same `geometricMean`/`deviationProfile`). Switched the main geom strip to
  the same `formatMs` (early negated) as analytics → both now read −18ms / −0.1ms / +18ms. Verified
  numerically main==analytics. (`formatGeom` now unused.)
- Version stamp → `?v=20260730q-webapp`. No console errors.

## 11-item UX batch (30 Jul, v...r)
1. **Game mode**: manifest → `"categories":["games"]`, `"display":"fullscreen"` + `display_override`.
   (Web has no touch-rate boost like native appCategory=game — symbolic + fullscreen launch.)
2. **Geom values coloured + un-bold**: `.geom-strip b` was `#fff/700` and overrode `.early/.late`.
   Now `font-weight:400`, `b.early`→red, `b.late`→blue, absolute white.
3. **Log date**: moved to the FRONT of the session header, white, `.session-row` now `position:sticky
   top:0` (stays while attempts scroll). Fixed the scroll itself — a duplicate `.android-log-wrap`
   set `overflow:hidden`, overriding `overflow-y:auto`; now scrolls.
4. **Per-day session numbering** on main: `buildLogRows` numbers sessions 1..N within each calendar
   day (was global). Main is TODAY, so after midnight the log empties and numbering restarts at #1.
5. **Per-session distribution histogram**: expanded tree sessions now show `distributionChartHtml`
   above their attempt rows (verified 21 bars + 8 rows).
6. **Chip under button**: `.cheer-toast` left-aligned (`align-self:flex-start`), font 13→11.
7. **Modes marked on strip**: new `modeTicks()` draws white ticks on `.rd-strip` at the top-early/late
   mode positions (0 at centre, edges at ±max(|P3|,|P97|)).
8. **Top safe-area**: `.android-screen` `padding-top: calc(env(safe-area-inset-top)+30px)` so the
   targets clear the camera/notch.
9. **Close → circular ×**: `.game-overlay-close` is now a 32px circle with "×", top-right of the bar.
10. **Smiley left**: `.face-badge` left 18→9 px.
11. **Twin/neighbor outline any length**: `buildLogRows` now marks RUNS (start/middle/end) not just
    pairs; CSS composes side borders + rounded top/bottom so any N consecutive outline as one pill.
    Verified a 3-in-a-row twin → start/middle/end, and a 3-neighbor run.
- All verified via DOM + no console errors. Version stamp → `?v=20260730r-webapp`.

## Settings regrouping (30 Jul)
- **Progress-bar group**: "Seconds progress bar" checkbox + its scale chips now share ONE card
  (scale directly under the checkbox). Playing-hand moved off this card.
- **Playing hand** moved UP into the first (general) card as a switch-row, and its CSS was fully
  rebuilt: the old `.hand-toggle` used `text-indent:19px` + a radial-gradient dot (mangled/invisible).
  Now a clear pill: dark bg + border, Left/Right white-ish, active = solid orange.
- **Touch sampling rate** text: keeps the first paragraph (up to "…equally likely."), the rest is
  hidden behind a caret toggle ("How dithering works" ▾/▴) — `.touch-more.open` reveals it,
  `touchMoreToggle` flips the caret. Verified none→block on tap.
- Verified via DOM + screenshot, no console errors. Version stamp → `?v=20260730s-webapp`.

## Per-session histogram aligned under the error diagram (30 Jul)
- Moved the per-session distribution histogram BELOW the attempt rows (error diagram) in the tree.
- Made their **0-axis and scale coincide exactly**: tree attempt rows now render error bars with a
  `scaleMaxMs` (= `buildDistribution(attempts).scaleMax`, same as the histogram), so a bar ends at
  `((dispErrMs/scaleMax+1)/2)` — the same x-formula the histogram uses. `.tree-distrib` is padded
  `76px`/`38px` (matching the attempt-row grid `20 6 44 6 |1fr| 6 32`) so the histogram plot band
  lines up with the graph column. Verified: 0-axis at x=757 on BOTH (diff 0px), and error-bar ends
  land exactly on their buckets (−40→693, −20→725, +20→788, +40→820). No console errors.
- Version stamp → `?v=20260730t-webapp`.

## Log scroll — no more caps (30 Jul)
- Main screen: removed the `.slice(0, 40)` cap on `buildLogRows` — the whole day's sessions now
  render and the log (`.android-log-wrap`, `overflow-y:auto`) scrolls. Verified: 6 sessions / 54
  attempts + 6 headers all shown, scrollHeight 1332 > client 212, scrolls to bottom.
- Analytics: already uncapped — `buildSessionTree` renders every session for the period (pick
  "All time" for everything ever), attempt rows use all of a session's attempts, and
  `.analytics-content` scrolls to the bottom (verified 6 session nodes, 1784px, scrolled fully).
- Version stamp → `?v=20260730u-webapp`. No console errors.

## Freehand marker ovals restored (native algorithm) (30 Jul)
- Alex: bring back the "loose" (non-rigid) twin/neighbor outlines. Ported native
  `SessionStatisticItem.drawFreehandOval` to JS (`freehandOvalSvg`): 12 seeded form-configs
  (tilt · rx/ry · bottom-start · double-loop), jittered bezier points via `mulberry32(seed)`,
  quadratic-through-midpoints path + tail overshoot. Seed = attempt.startTime.
- The oval is drawn ONCE on the run's TOP row (`groupHeight` = run length) and overflows down over
  the group, so ANY-length run (2, 3, 4…) gets one organic oval. Replaced the rigid composed-border
  rectangle. Green for twins, amber (`#f5c02e`) for neighbors. `OVAL_ROW_H = 22`.
- Verified via DOM: a run of 3 → one oval h=72, runs of 2 → h=50, valid bezier `d` paths, twin class.
  (Live screenshot blocked by the frozen-renderer capture glitch.) No console errors.
- Version stamp → `?v=20260730v-webapp`.

## Tree success indicator → thumb pictograms (30 Jul)
- Replaced the ▲/▼ "finger" triangles on day/session tree nodes with SVG thumb icons: thumb-UP
  filled green (`--win`), thumb-DOWN filled gray (`--muted`, was red). `finger()` now emits the
  Material thumb path. Verified: day 👍green, complete session 👍green, incomplete 👎gray.
- The expand carets (▾/▸) are kept separate (they're the expand affordance, as in native).
- Version stamp → `?v=20260730w-webapp`. No console errors.

### Still optional (need on-device eyes)
- Face-icon slide/wink animation fidelity (CSS approximation vs native SVG).
- Pixel-level review on a real phone (screenshots weren't available in this env).
- Fullscreen engage confirmation on a physical phone (sandbox blocks programmatic fullscreen).
- Progress-bar fill/beak live growth on a real device (automation rAF was throttled).

## Main-screen summary strip (v=20260730x)
- "Distrib." label moved out of the Σtries box down to the axis-value-labels row under the histogram (`.tb-distrib`).
- Σtries and ΣSC value blocks now vertically centred against the 58px histogram height (`.tb-count`/`.sb-count` height:58px, justify-center).
- ΣSC text label replaced by a gold coin-stack pictogram (`coinIcon()` SVG, `.coin-ico ellipse{fill:var(--sand)}`).
- Geom-mean beaks: removed the dashed vertical stems (`geom-markers` no longer emitted) and both horizontal lines (`.geom-band` border-top + `::after` deleted, background removed). Bare up-triangles now sit between the axis labels and the mean values; band shrunk to 12px. Grid rows → `auto 22px`.

## Shared geom-mean guides across diagram + histogram (v=20260731a)
- Main log error bars now share the histogram's dynamic scaleMax (renderLogRow given scaleMaxMs), so the error diagram has ONE consistent horizontal error axis (native dynamicScaleMaxMs model).
- Aligned the summary's middle chart column to the log row's .attempt-graph plot band: android-summary grid 70px/1fr/32px, padding 16 → chart [446,872], histogram [446,872], graph [446,872], 0-axis at 659 in all three.
- Hid the log scrollbar (scrollbar-width:none) so the row width — and thus the plot band + 0-axis — is identical on every platform and matches the histogram.
- Replaced the histogram-local geom-markers with ONE .geom-guides overlay in a new .diagram-stack wrapper (position:relative around log-wrap + summary). Three dashed lines (red early / white all / blue late) span the diagram top down to the histogram 0-axis (top:0, bottom:47px, inset left:92px right:54px = the plot band). Lines stop at the axis-labels row so the numbers can't break them. Beaks sit just below at the same x.
