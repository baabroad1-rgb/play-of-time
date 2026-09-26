# Report Page (`report.html`) — Full Context Summary for Cowork

**Covers:** 2026-09-10 through 2026-09-18 (the whole build-out of this feature — supersedes the three earlier partial handoff docs in this folder: `report_page_deploy_2026-09-11.md`, `leaderboard_formula_fix_2026-09-11.md`, `report_page_update_2026-09-15.md`. Those are kept for historical detail; this doc is the one to read first.)

## Deploy status — CONFIRMED LIVE, verified just now (2026-09-18)

No action needed. Checked directly:

```
curl -s https://playoftime.online/report.html | diff - webapp/report.html
→ 0 lines difference (byte-identical)

curl -s -o /dev/null -w "%{http_code}" https://playoftime.online/api/report_data.php   → 400 (correct — no token supplied, matches its real auth check)
curl -s https://playoftime.online/api/leaderboard.php                                   → 200, real JSON with luckyIndex/hitRatePct fields
curl -s -o /dev/null -w "%{http_code}" https://playoftime.online/api/_lib.php           → 403 (correct — shared include, not a standalone endpoint)
```

Every file this whole feature touches — `report.html`, `api/report_data.php`, `api/leaderboard.php`, `api/_lib.php` — is live and responding as designed. The earlier docs' "unconfirmed whether this ever got pushed" caveats are now resolved: it did.

**If report.html on the server ever looks stale again**, that exact `curl | diff` check above is the fast way to confirm one way or the other — don't assume either "auto-syncs" or "needs manual upload," a session earlier this week (2026-09-15/16) got burned trusting a stale assumption in both directions. Always check live before asserting deploy status.

## What this feature is

A personal results page for one player — two Chernoff faces side by side ("subject" period vs. a comparison period), each face's 9-ish psychological measures encoded as facial features (head size/shape, eyes, brows, ears, pupils, nose, mouth), plus a plain-English narrative underneath ("Here's how you're doing") and a raw data table. Reached from the native app's Ranks tab (in-app WebView, `ReportDialog` in `LeaderboardScreen.kt`) or a direct browser link. Single self-contained HTML file, no build step — the file **is** the deployable artifact.

## Full arc, by theme (not strictly chronological — related work grouped together)

### 1. Core build (2026-09-10/11)
- `report.html` built from the earlier "Metric Comparison" artifact prototype: `state.player` fixed to `"me"`, real async `loadReport()` reading `playerId`+`token` from the URL and fetching from `report_data.php`.
- `api/report_data.php` (new): validates the token (`hash_equals`, same pattern as `sync.php`), filters to the player's **current precision tier only** (deci/centi/milli — never mixes levels) and the current epoch, computes per-session attempt-numbering in plain PHP (no window functions — MySQL 5.7 baseline).
- Moved from "opens in system browser" to an in-app WebView dialog (`LeaderboardScreen.kt`, native) — same page, same live `fetch()` on every open, no caching layer anywhere.
- Same session: two real formula bugs found and fixed on the **leaderboard** (not this page, but same PHP files) — Lucky Index was using an untrimmed min/max range instead of the P3–P97 trim the native Analytics screen uses; Hit Rate was counting every tolerance-eased win instead of only exact "true three" hits. Both fixed in `api/_lib.php` / `api/leaderboard.php`, verified against hand-computed reference datasets.

### 2. UX iteration — period picker, pagers, legend merge (2026-09-11 → 09-15)
- Sample-size disclaimer added ("reasonably reliable data starts to show up after ~500 attempts at Level 2").
- Period picker enforces the precision-tier boundary at the UI level (disables presets that would reach before the level started), not just in the data.
- "Holding steady" section added — narrate() now comments on metrics that *didn't* move too, judged against the player's own historical weekly-bucket median (not an invented absolute threshold).
- Vertical grouped-bar profile chart replacing an earlier horizontal design (rejected live, rebuilt).
- The standalone "How to read these faces" legend panel was **merged** into the narrative itself: every metric name is now a tap target (shows what the parameter means), a 🙂 icon + short phrase follows each paragraph (shows how the facial feature reacts; tapping animates it).
- **Tapping the drawn feature directly on either face** now animates it and shows the same tooltip, anchored to the actual tap point. Elements driven by more than one metric (eyes, head, mouth) cycle through all of them on repeated taps.
- Persistent ‹›pager added to both faces' own labels (Week/Month stepping for the comparison side; a merged "Day" pager — defaulting to Yesterday — for the subject side).
- Visual redesign: two-tone background merged into one flat cream fill, content column widened 640px → 880px.
- `range_span` ("Control Under Pressure") fully removed from every surface (table, narrative text, cross-metric callouts) — was only partially removed in an earlier pass.

### 3. Geometry safety fixes (2026-09-15, later sessions)
- Found and fixed three real overlap bugs: mouth corners could poke past the head's own sides on a small head; the smile curve could extend below the chin; a tilted nose could collide with a deep frown. All three verified via a full grid sweep (161,051 parameter combinations) plus rendered worst-case screenshots — not just derived on paper.
- Metric-tooltip widened, repositioned to always show above (not below) the tapped element.
- (Native-only, not this file, flagged for completeness) `ReportDialog` in `LeaderboardScreen.kt` — title bar text removed, outer margins/insets tightened to match every other native screen's own Scaffold padding, close-button size reduced.

### 4. Theoretical scales + outlier handling + "optimal zone" highlighting (2026-09-16)
- Replaced the relative (compared-periods-only) scale most metrics used with a **fixed theoretical scale** (`CH_THEORY_MAX`) for 14 of 16 underlying metrics — e.g. ±500ms is "the norm" for early/late/tail/mode deviations, a derived closed-form constant (≈203.65%) for the geometric-CV family (pure Gaussian motor noise, worked out analytically, not guessed). **`lucky` (mouth/smile) is the one metric still without a theoretical scale** — still open, flagged below.
- Attempts more than 500ms from the 3.000s aim are now excluded as outliers from the deviation-magnitude metrics (not from hit-rate/lucky, where a big miss is still a real miss).
- New "optimal zone" feature: a facial trait recolors green when it's reading as genuinely excellent. Iterated through several real design rounds (color-only → several candidate "laughing eye" shapes, hand-picked and refined) before landing on a sharp-cornered "lens" eye shape for eyes specifically that are in the zone; every other optimal trait just recolors, no shape change.

### 5. Bug fixes + the bottom-anchored/safety-square system (2026-09-17)
- **Tooltip positioning made flexible** (measures real space above/below instead of assuming there's room) and **recolored to match the native About-screen amber**.
- **Real click-through bug found and fixed**: `fill="none"` stroke-only elements (brow/nose/mouth lines) only have a hit-test area on their thin visible stroke — a tap near-but-not-on them fell through to the solid head ellipse underneath and triggered the wrong demo animation. This was NOT reproducible with synthetic clicks locally — only found by testing the actual published Artifact link with a real tap. Fixed with invisible wide-stroke "hit overlay" twins painted after the head.
- **Missing escape route from the empty-data state** — the period-picker controls used to disappear entirely along with the face when a side had no data, leaving no way out. Fixed: the label/pager row always renders, only the face itself falls back to a placeholder.
- **Stale "Today" bug**: three different places computed "today" three different ways; paging back then forward to Today could silently show yesterday's data under a Today label. Unified under one `REAL_TODAY` constant.
- **Faces bottom-anchored + a dashed "safety square" added** around each face — a rectangle proving the face geometry can never exceed it, tightest at the true worst-case (max-inflated) parameter combination. Verified via `chNorm` override injection reading real `getBBox()` values, not just algebra.
- Found and fixed a real bug in that same safety-square work: the function driving the legend's live demo-sweep animation updated the head's size every frame but never its vertical position — so during a demo the head could visually drift past the square. One-line fix, re-verified via a 20-frame sample of the actual running animation.

### 6. Today, 2026-09-18 — anchor direction, per-head dynamic square, ranked highlighting, absolute values in the narrative
This was a dense, iterative session — several requests corrected each other in sequence, all resolved by the end:
- Tried pinning the head's **top** edge at a fixed position (to make the caption-to-head gap minimal and constant) — this genuinely worked for that one goal, but broke the established "both faces share a bottom baseline for at-a-glance height comparison" rule from 09-16/17. **Reverted.**
- Landed on the real fix: the dashed square (and the SVG's own viewBox) now **resizes per-render to snugly fit whatever the CURRENT head actually is** — zero slack between the square and the head on any side — while the bottom edge stays pinned exactly as before. Structurally: the two faces' SVGs and their labels were split into two independent flex rows (squares row, bottom-aligned; labels row, below) so a square-height difference between the two faces can't perturb their shared bottom line. Verified numerically: both faces' square-bottoms now coincide to sub-pixel precision, and the safety-square worst-case guarantee (head can never cross it) still holds.
- **"Optimal zone" highlighting changed from an absolute threshold to a ranking**: instead of "green if ≥97% goodness," it's now "the 3 best-scoring traits on this face turn green, always exactly 3, regardless of their absolute value." A trait driven by two metrics (head, mouth, each eye) is scored by its *weaker* metric, same rule as before, just feeding a sort instead of a yes/no.
- **Absolute before/after values added to the narrative**, inside the same colored percentage pill (not a separate sentence) — e.g. `-40.1% (32.6 ms→19.5 ms)`. **Skipped for the 5 metrics whose own native unit is already percent** (hit rate, state steadiness, feedback use, pace of learning, error stability) — showing two different percentages side by side in one pill read as one confusing number, not two distinct ones; those keep just the % change.
- Two small layout fixes: the sample-size disclaimer moved from near the top down to just above the data table; the "Based on Chernoff faces" credit line's divider flipped from above the text to below it.

## Known open items

- **`lucky` (mouth/smile) still has no theoretical scale** — the one metric still on the old relative-to-compared-periods fallback (see #4 above). Needs its own dedicated round to define a real bound, same as was done for the other 14 metrics.
- Everything in this document is verified against **synthetic mock data** (`scratchpad/make_snapshot.py` + a local `http.server`, or the published Artifact link) — real numeric checks (`getBBox()`, `getBoundingClientRect()`, live click testing, curl+diff against the live server), not eyeballing. None of it has been re-checked against a real player's own actual history on the live site yet — worth a look next time someone's there with real data.
- Popover/menu interactions on this page **must be verified with real mouse clicks**, not synthetic `.click()`/`dispatchEvent()` calls — a real `stopPropagation` bug on this exact page (2026-09-15) only reproduced under real click-event-bubble timing; every earlier automated test using synthetic clicks kept "passing" while it was actually broken. If Cowork touches this page's interaction code, carry this practice forward.

## Files in scope

- `webapp/report.html` — the whole feature, single file, confirmed live (see top of this doc).
- `webapp/api/report_data.php`, `webapp/api/leaderboard.php`, `webapp/api/_lib.php` — confirmed live, no pending changes.
- Native `LeaderboardScreen.kt`'s `ReportDialog` composable — cosmetic dialog-chrome changes only (title removed, margins tightened), not part of this webapp file, only relevant if Cowork's own work ever touches the native side.
