# Play of Time — Web App workspace

Self-contained workspace to continue the **web version** of Play of Time so it
behaves the same way as the native Android app.

## What's here

```
webapp/
├── index.html              ← landing page + embedded game shell (#app)
├── app.js                  ← the game (vanilla JS, ~1770 lines) — the "previous attempt"
├── styles.css              ← all styling
├── manifest.webmanifest    ← PWA manifest
├── icon.svg                ← app icon
├── CLAUDE.md               ← project brief: rules/invariants for continuing (read first)
└── reference/
    ├── docs/
    │   ├── webapp_handoff.md   ← AUTHORITATIVE platform-independent spec (v12.87). The source of truth.
    │   └── touch_sampling.md   ← dithering / honest tap-timing math (spec §3 backing)
    └── native-src/             ← canonical Kotlin that defines exact behavior/formulas
        ├── Quest.kt                 15 quests, victory conditions, indulgence caps/steps
        ├── CalculateScmUseCase.kt   Serenity Coins formula (+ level factor)
        ├── RecalculateScmUseCase.kt full-history recalc
        ├── AnalyticsUseCase.kt      analytics metrics (lucky, range, modes, geom mean)
        ├── MainMetricsUseCase.kt    main-screen metrics (histogram, geom means)
        ├── GetLogAttemptsUseCase.kt log rows, twins/neighbor, win-by-tolerance
        ├── CheerUpMessages.kt       all cheer-up texts + describePhase()
        ├── CheerUpTrigger.kt        trigger enum (incl. AFTER_SUBQUEST_* family)
        ├── ErrorPattern.kt          7 rhythm patterns
        ├── LocalStorageService.kt   persisted quest/session keys
        ├── MainContract.kt          UI state shape
        └── Utils.kt                 constants (dither cell, aim, etc.)
```

The provenance chain (native → spec → web) means: when web behavior must match
native, the order of authority is **native-src → webapp_handoff.md → app.js**.

## How to run

Pure static files — no build step. Serve the folder and open it:

```bash
python -m http.server 8000
```

Then open `http://localhost:8000/`. The game is at the bottom of the page
("Tap to launch"); it also runs full-screen in the `#app` shell. The game needs
no assets (audio is Web Audio synth, icons are inline SVG). The landing page's
video/images (`assets/…`) are not bundled — pull them from playoftime.online if
you want the full page.

## Parity status vs spec (v12.87)

`app.js` is versioned `20260712` — it predates the July timing/subquest-cheerup
work. Present: quests + indulgence, SCM + level factor, sessions/periods,
analytics (lucky/range/modes/geom mean), twins, log, export (JSON/CSV/Excel),
error patterns, base cheer-ups.

**Known gaps to close for full parity** (see CLAUDE.md for detail):

1. **§3 L3 dithering + echo/re-roll** — MISSING. Highest priority: without it,
   "exactly 3.000" on L3 is unfair (the browser input grid rarely lands on it).
   Needs `event.timeStamp`/`performance.now()` measurement +
   `getCoalescedEvents()` cell estimation.
2. **§8 Neighbor detection** (yellow oval, |errors| differ by exactly one cell) — MISSING.
3. **§10 `AFTER_SUBQUEST_*` cheer-up family + `describePhase()`** — MISSING (only base cheer-ups).
4. **§4 `questWasIndulged`** flag — MISSING (needed to pick AFTER_QUEST vs AFTER_SIMPLIFIED_QUEST_DONE).

Verify each gap on device against `reference/native-src/` before implementing.
