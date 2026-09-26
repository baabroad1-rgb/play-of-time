# Play of Time — Web App (project brief)

Web port of the native Android "Play of Time" 3-second timing trainer. Goal:
**behave the same way as the native app.** This file is the brief for anyone
(human or agent) continuing the web build. Read `reference/docs/webapp_handoff.md`
first — it is the authoritative, platform-independent spec.

## Stack
- Plain static site: `index.html` + `app.js` (vanilla JS, no framework/build) + `styles.css`.
- Persistence: `localStorage` (key `play-of-time.web.v1`), JSON blob (`db`).
- Audio: Web Audio API (synthesized tones — hooray/sorry/nack/metronome).
- Serve with any static server (`python -m http.server`). No compile step.

## Source of authority (when web vs native disagree)
`reference/native-src/*.kt`  →  `reference/docs/webapp_handoff.md`  →  `app.js`.
Never invent behavior; port it from native. Verify formulas against the `.kt`.

**The mirror can lag — don't treat it as guaranteed-current.** It's a cached
snapshot of `C:\PlayOfTime\time_mvp-quest_dev`, refreshed by hand, not
automatically. It already went stale twice silently (2026-08-13: Lucky Index
+ SubQuest-label formulas drifted for weeks before anyone noticed, because
the mirror still showed the old versions as "current"). Refreshed again on
2026-08-13 — all 11 files match live source as of that date. For anything
correctness-critical (a formula, a state machine, an enum's exact values),
treat the mirror as a fast first read, then diff the specific file(s) against
live native source before shipping a fix based on it. `webapp_handoff.md`
itself lags further still (see `PARITY_AUDIT.md` header) — same caveat, more so.

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

## Web-specific cautions (spec §15)
- `event.timeStamp` resolution is clamped (~1 ms + jitter) as anti-timing defense
  — another reason to dither rather than fight it.
- 60 Hz rAF ≠ input rate; pointer events coalesce — read `getCoalescedEvents()`.
- Background tabs throttle timers — account for it in play-time ticker.
