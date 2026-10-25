# Play of Time — Web App (project brief)

> **🔜 Открытое — `docs/TASKS.md`, раздел «Расчистка бэклога 25.10.2026»**: черты поведения сделаны 25.10 (7 черт, `behavioral_profile.py`); чистка базы — перед выкладкой в Google Play (Алекс: «потом»).
>
> **START HERE:** [`../docs/HANDOFF_2026-09-29.md`](../docs/HANDOFF_2026-09-29.md)
> — состояние проекта, что живое, что открыто, как работать с Алексом.
> Читать перед любой работой.

Web port of the native Android "Play of Time" 3-second timing trainer. Goal:
**behave the same way as the native app.** This file is the brief for anyone
(human or agent) continuing the web build. Read `../docs/webapp_handoff.md`
first — it is the authoritative, platform-independent spec.

## Stack
- Plain static site: `index.html` + `app.js` (vanilla JS, no framework/build) + `styles.css`.
- Persistence: `localStorage` (key `play-of-time.web.v1`), JSON blob (`db`).
- Audio: Web Audio API (synthesized tones — hooray/sorry/nack/metronome).
- Serve with any static server (`python -m http.server`). No compile step.

## Source of authority (when web vs native disagree)
`../android/app/src/**/*.kt`  →  `../docs/webapp_handoff.md`  →  `app.js`.
Never invent behavior; port it from native. Verify formulas against the `.kt`.

**No more mirror — read the live Kotlin.** Until 2026-09-26 this section pointed at
`reference/native-src/*.kt`, a hand-refreshed copy that existed only because the two
projects lived in separate folders. Both are now in one git repository, so the real
source is one path away and cannot lag. The mirror is deleted; its history is in git.

That mirror went stale three times: 2026-08-13 (Lucky Index + SubQuest-label formulas
drifted for weeks unnoticed — the mirror kept showing old versions as "current"),
again later that day on 5 of 11 files, and by 2026-09-26 on 7 of 12 files, with
`AnalyticsUseCase.kt` 1061 lines behind. That is the cost of a manual copy, and it is
why the copy is gone rather than refreshed a fourth time.

A file-by-file map of what defines what lives in `README.md` («Где определено
поведение»). `../docs/webapp_handoff.md` still lags the Kotlin (see
`../docs/PARITY_AUDIT.md` header) — it is a written spec, not generated, so when it
and the `.kt` disagree, the `.kt` wins.

## The mechanic (one loop)
RUN → wait ~3 s → STOP → error vs **3.000 s** → Serenity Coins → quest progress.
Precision tightens by level: tenths (L1) → hundredths (L2) → thousandths (L3).

## Non-negotiable invariants (do not break)
- **Duration must use monotonic time.** `performance.now()` for elapsed; take the
  event timestamp as early as possible in the `pointerdown` handler. NEVER
  `Date.now()` for durations (non-monotonic under NTP).
- **True three = `|record_ms − 3000| ≤ toleranceMs`.** Baseline tolerance = 0.
- **SCM per attempt** (see CalculateScmUseCase.kt):
  `SCM = 99 / denom · 10^(accuracyOrdinal − 3)`, denom uses this + both neighbors.
  Peaks: L1→0.99, L2→9.9, L3→99. Level factor is deliberate; keep it.
- **Histogram green = only the exact-3.000 center bucket**, even inside indulged
  subquests. Other buckets red (early) / blue (late).
- **Indulgence: only ONE adaptation at a time.** IN_N quests adapt N (±33, cap
  {33,66,99}); ROW quests adapt t (±1 ms, cap {0,1,2,3} for most — L3.4/L3.5
  raised to cap {0..9} on 2026-08-08, mirror this per-quest, not globally).
  Never both.
- **`isWin` is frozen at record time** under the conditions then active; a green
  attempt stays green after later contraction.
- **Playing hand** is tracked per attempt for stats but does not affect gameplay.

## Quest state machine (spec §4)
15 quests, strictly linear. Subquest exit on window success:
- Rule 1: win already at baseline → advance.
- Rule 2: baseline-quality win in subquest (ROW: worstWinError ≤ baseT; IN_N:
  lastWinIndex ≤ baseN) → jump to baseline + advance.
- Rule 3: subquest win, not baseline-quality → step back one indulgence notch.
Window failure → grow the adaptive param one step (to cap); next session uses it.

Persisted quest keys: `currentLevelId, currentTaskId, currentN,
currentToleranceMs, questWindowAttempts, questWindowWins, worstWinErrorMs,
lastWinIndex, questWasIndulged`.

## Parity status
**2026-08-13**: this section used to list 4 items ("L3 dithering + echo/re-roll",
"Neighbor detection", "AFTER_SUBQUEST_* cheer-ups + describePhase()",
"questWasIndulged") as open gaps — checked `app.20260813a.js` directly and all
four are implemented (dithering/echo at ~line 164, neighbor detection + "wow,
neighbors!" at ~line 1986, `describePhase()` at ~line 1323, indulgence tracked
as `db.quest.wasIndulged` — same concept as native's `questWasIndulged`,
different variable name — at ~line 601/895/925/950). Don't maintain a second
gap list here — it silently went stale while `PARITY_AUDIT.md` (which tracks
the same ground, in more detail) stayed current. **`PARITY_AUDIT.md` is the
one place to check current parity status; this file no longer duplicates it.**

## Working style (owner: Alex / Alexandr Belyshkin)
- Confirm one change at a time (row-by-row); no big batches for approval.
- Show a mockup/screenshot of visual changes before writing code.
- Use domain vocabulary (precision, focus, threes, cluster, aim) in user-facing
  text — no tech jargon ("window", "attempt limit", "adaptive parameter").
- Bugs beat features. Reproduce, then fix.

## Страница отчёта с мордами (`report.html`)

Отдельная страница, не часть игрового экрана. Открывается по ссылке с
`playerId`+`token`, данные берёт из `api/report_data.php`. Её же в режиме
`?mode=face&fromMs=..&toMs=..&min=..` показывает нативное приложение в
карточке на экране аналитики — правка страницы меняет и то, и другое.

**Морды Чернова.** Метрики игрока за период отображаются чертами лица
(`chFaceParams`/`chFaceSvg`). Обе морды — текущий период и период
сравнения — рисуются в ОДНОМ `viewBox` (`chFaceCanvasBox` по большей из
двух голов), иначе при разных размерах головы слои разъезжаются по
вертикали и на шве получается ступенька. Пунктирной рамки вокруг морды
нет с 28.09.2026, но кадр по-прежнему считается — он задаёт `viewBox`.

**Шторка.** Морды лежат друг на друге, граница двигается пальцем.
`--curtain` — положение шва в процентах ширины кадра, он же положение
ручки: 0 — ручка слева, лицо целиком от АКТУАЛЬНОГО периода («сегодня»);
100 — ручка справа, лицо целиком от периода сравнения. Покой всегда на
краю. Тап уводит к противоположному краю, отпущенная на середине —
доезжает к ближнему, палец всегда сильнее доводчика.

Три вещи, каждая из которых чинила реальный баг, увиденный глазами:
- у верхнего слоя ОБЯЗАН быть непрозрачный фон (`background: var(--surface)`),
  иначе нижняя голова просвечивает и лицо выглядит двойным;
- шов и ручка показываются по очереди (`data-edge`), иначе на полпути ручка
  садится ровно на нос;
- положение шторки ПЕРЕЖИВАЕТ `render()` — иначе выбор периода тут же
  возвращал бы её к «сегодня».

**Одна кнопка периода** под лицом (29.09.2026). Показывает период того
лица, которое занимает бОльшую часть кадра, переключается ровно на
середине (`paint()` в `wireCurtain`). Тап открывает тот же выбор периода
для видимой стороны. Две кнопки остаются ровно в одном случае: у стороны
нет данных и шторки нет — иначе игроку нечем выбраться из пустого периода
(баг 17.09.2026, не потерять при следующей переделке).

Умолчания: слева «сегодня» (если сегодня не играли — ближайший день с
попытками, подпись честная), справа текущая календарная неделя с
понедельника, по понедельникам прошлая целиком.

## Два железных правила веба

**1. Меняешь JS или CSS — меняй ИМЯ ФАЙЛА.** Веб-версия обновляет себя,
сравнивая имя подключённого в `index.html` скрипта. Правка «в том же
файле» до игроков не доедет: они останутся на своей кэшированной копии.
Поэтому в папке и лежат 14 версий `app.2026xxxx.js` — это не мусор, это
механизм. Живая та, что подключена в `index.html`.

**2. `report.html` не править вслепую.** До 27.09.2026 страницу меняли, ни
разу не отрисовав, и это стоило нескольких кругов «не видишь что лицо
криво отрисовано». Перед выкладкой поднимается локальный стенд и страница
снимается в браузере:

```
python3 -m http.server 8123          # из webapp/, плюс подделка api/report_data.php
```

Chromium в контейнере уже стоит:
`/opt/pw-browsers/chromium-*/chrome-linux/chrome`, Playwright настроен
(`PLAYWRIGHT_BROWSERS_PATH`), `playwright install` запускать не надо.
**Данные стенда должны давать РАЗНЫЕ размеры голов у двух периодов** — на
почти одинаковых баг с просвечивающей мордой не виден, проверено на своей
шкуре.

Выложенную страницу из контейнера не посмотреть: прокси режет
`playoftime.online`. Сверка «что реально на сервере» — workflow
`check-api.yml`, он сравнивает выложенный `report.html` с репозиторием
побайтно.

**Что исключено из выкладки:** `api/config.php`, `api/.htaccess`, корневой
`.htaccess` — они настраиваются на сервере, выкладка их затирала и клала
лидерборд (26.09.2026). Перед добавлением чего-либо в выкладку проверь, не
настраивается ли это на сервере.

## Web-specific cautions (spec §15)
- `event.timeStamp` resolution is clamped (~1 ms + jitter) as anti-timing defense
  — another reason to dither rather than fight it.
- 60 Hz rAF ≠ input rate; pointer events coalesce — read `getCoalescedEvents()`.
- Background tabs throttle timers — account for it in play-time ticker.
