# Deploy: personal results report page (tap-your-own-row on the leaderboard)

Context: Alex asked that tapping your OWN row in the native leaderboard opens
a browser page generating a personalized interpretation of your data — the
narrative report already discussed and iterated on the "Metric Comparison"
artifact (rewritten several rounds for shorter, hedge-free, subheaded text —
see the "Narrative Feature Decision Log" artifact, entry D28). This is that
page, made real: it fetches one player's actual attempts instead of an
embedded test dataset.

Two new files need uploading. Nothing else on the live site changes.

**Update (still same day):** `report.html` gained two more pieces since this
doc was first written — still not deployed yet, so this is just an amendment
to what's in the one file, not a new upload target. See the bottom of this
doc, "Later additions to report.html."

## 1. `webapp/report.html` → upload as `report.html` at the site root

So it resolves at `https://playoftime.online/report.html`, matching the URL
the native app opens. It is the full narrative-comparison engine (same
`PHRASES`/`CROSS_LINKS`/`narrate()` as the artifact, ported 1:1 so the two
never diverge in wording) minus the multi-test-player selector, with:

- `playerId`/`token` read from the URL's query string
  (`?playerId=...&token=...` — the native app supplies both when it opens
  this page, see section 3).
- A `fetch()` to `/api/report_data.php` on load; shows a loading message,
  then either the report, a "not enough attempts yet" message, or a load
  error, depending on what comes back.
- RU/EN language toggle (unchanged from the artifact) — kept, since it's a
  real, previously-approved feature, not test scaffolding.
- Period-vs-comparison-period picker (unchanged) — same "week ago vs. this
  week" style comparison as the artifact, just driven by the player's own
  real attempt timestamps instead of a fixed test date.

## 2. `webapp/api/report_data.php` → upload into `api/`

New endpoint, same ownership-proof pattern as `sync.php`/`set_name.php`:
`playerId` + `token` (the device's own `syncToken`) via GET, `hash_equals`
against `players.sync_token`. On success returns:

```json
{"ok": true, "displayName": "...", "acc": "ds|cs|ms",
 "attempts": [{"t": "2026-09-10T12:00:00Z", "s": 101, "n": 1, "e": -7}, ...]}
```

- **Precision-tier filtering**: only attempts at the player's *current*
  accuracy tier (the accuracy of their most recent attempt) are returned —
  matches the narrative engine's own established rule (Decision Log D13:
  compare only within one precision tier, never across levels).
- **Epoch-scoped**: only attempts in `players.current_epoch`, same
  convention `sync.php`/`leaderboard.php` already use — an Erase/Reset
  doesn't resurrect old-epoch history here either.
- **`n` (attempt-number-within-session)**: computed as a plain running
  counter per `session_id` in PHP, not a SQL window function — this
  project's schema note says MySQL 5.7, which has none.

Tested end-to-end against a seeded SQLite DB (`POT_TEST_DSN`, this project's
own local-test convention — see `db.php`) and a live local PHP server
serving `report.html` + this endpoint together over real HTTP: confirmed the
full pipeline (fetch → tier-filter → narrative render → chart → table →
language toggle) renders correctly with real per-player numbers. Also
checked the three error paths directly: wrong token → 403 `token mismatch`;
unknown player → 404 `unknown player`; malformed `playerId` → 400 `invalid
playerId/token`. No attempts yet at the current tier → `attempts: []`, page
shows the "not enough attempts" message rather than an empty chart.

## 3. Native side — already done, no action needed here

`LeaderboardScreen.kt`'s `onSelectRow` now checks
`row.playerId == state.myPlayerId`; on your own row it opens
`https://playoftime.online/report.html?playerId=<id>&token=<syncToken>` via
`LocalUriHandler` instead of showing the compare dialog. Compiled clean
(`compileDebugKotlin`), **not yet built into a fresh APK** — next
`assembleDebug` will include it.

## Known, accepted tradeoff: token in the URL

The `syncToken` travels as a URL query parameter, which means it can land in
browser history and in the `Referer` header of any outbound request/asset
this page happens to make. This is the same tradeoff already implicit in how
`playerId`/`syncToken` work everywhere else in this system (an anonymous,
low-stakes identity pairing, not an account with anything sensitive behind
it) — flagging it explicitly here rather than leaving it as a silent
decision, per Alex's own standing ask on this kind of design choice. If it
ever needs hardening, the fix is a short-lived one-time report token minted
by a new endpoint at leaderboard-tap time instead of reusing the long-lived
`syncToken` directly — not done here, no one has asked for it.

## Later additions to `report.html` (same day, before any of this was deployed)

**1. Sample-size disclaimer.** New `<p id="sampleNote">` right under the lede
— "Diagnostic accuracy improves with more attempts. Reasonably reliable data
starts to show up after roughly 500 attempts at Level 2." (RU: "Точность
интерпретации растёт вместе с числом попыток. Более-менее достоверные
данные появляются примерно после 500 попыток на втором уровне.") Wired into
both `render()` and the pre-fetch static-translation pass in `loadReport()`,
so it shows correctly even in the loading/error states.

**2. Period-picker respects the level boundary.** The report already only
ever fetches attempts at the player's *current* precision tier
(`report_data.php`, D13's rule) — but the period popover (Today/Yesterday/
Week/Month/All time) didn't know that, and could offer a preset whose
nominal date range reached earlier than any same-tier attempt exists. Fixed:
`openPeriodPopover()` now disables (greyed out, `disabled` attribute, not
just visually) any preset whose computed `.from` date is earlier than
`playerBounds().min` — which, since `DATA` is already tier-filtered, *is*
literally "the first attempt at this level." "All time" is never disabled
(by construction it always starts at that same boundary). The custom-date
inputs get a matching `min` attribute so a manually-typed date can't reach
past the boundary either. `setDefaultRanges()` (the range shown before the
user touches the menu at all) got the same clamp — a player with a very
short current-level history no longer starts with a comparison period that
silently reaches into the previous level's timeframe.

Verified live: seeded a throwaway SQLite player with only 8 days of
current-tier history, ran the real page against the real endpoint over
local HTTP, confirmed the disclaimer text renders, confirmed the initial
default comparison period no longer starts before the level's earliest
attempt (was reaching 5 days too far back before the fix), and confirmed
the popover correctly disables "Week"/"Month" (not enough same-tier history
for those anymore) while keeping "Today"/"Yesterday"/"All time" enabled.

## Later additions to `report.html` — 2026-09-12 (still same one file, still not deployed)

Four more rounds landed on this page the next day, all webapp-only (no
native changes, no new APK) — this is the CURRENT state of `report.html`,
not an amendment on top of what's described above:

1. **"Holding steady" narrative section.** `narrate()` now also comments on
   metrics that DIDN'T shift, not just ones that did — "good, keep it" or
   "room to improve," judged against the player's OWN historical weekly
   median (`historicalDistribution()`, new), never an invented universal
   threshold. New third section in the output, between "What changed" and
   "How they connect."
2. **Chart is now a vertical grouped bar profile**, not the horizontal
   before/after rows described above. 9 metrics side by side, each a glued
   (no-gap) before/after column pair, height = value/its-own-max — a shared
   0-100% vertical axis across all 9. Per-bar numeric text labels are gone
   (physically didn't fit on a 20px-wide bar) — exact numbers are in the
   hover tooltip and the "Show as table" view. Scrolls sideways in its own
   container when it doesn't fit the card width (same pattern as the data
   table's own horizontal scroll, described above).
3. **Amber palette** matching the in-game About screen exactly (`#F2B70D`
   background, `#2A1E00` text) instead of the light/dark-adaptive palette
   described above — this page is now single-theme on purpose, ignoring the
   OS light/dark toggle, same as About does.
4. **Page committed to one width** (640px `max-width`, no responsive
   breakpoints) instead of the old 1180px desktop-first layout, since this
   page's real, primary home is the in-app WebView popup (a phone-width
   Dialog), not a desktop browser tab.

None of this changes the upload instructions above — still the same
`report.html`, still needs uploading once, just with more content in it now.
Full derivation (exact bugs found, screenshots, DOM measurements) is in the
Narrative Feature Decision Log artifact, entries D32 through D34.

## Later additions to `report.html` — 2026-09-13/14 (Chernoff-face redesign, still the one file, still not deployed)

Long back-and-forth session replaced the whole "profile chart" concept.
Everything below is already live in the local `report.html` (edited directly,
not a mockup) — still needs the same single upload as everything above, just
with a lot more content now. No other files touched (no native changes, no
new/changed PHP — `report_data.php` is untouched from the version already
described above).

1. **`buildRadar()` (the vertical grouped-bar profile) is gone entirely**,
   replaced by two Chernoff faces ("was" / "is") — each of the 9 metrics
   drives one facial feature instead of one bar. Mapping is a config table
   (`CH_FEATURES`/`CH_SYMMETRIC`) specifically so a feature can be
   reassigned to a different metric, or one feature's sensitivity tuned, by
   editing one line — not by touching the render code (Alex asked for this
   explicitly). early/late are the one genuinely bipolar pair — the LEFT
   half of each face reacts only to `early`, the RIGHT half only to `late`
   (brow droop + eye narrowing on that side), so a real early/late imbalance
   shows up as facial asymmetry rather than a single shared dial. Geometry
   has hard safety clamps (eyes can't cross the midline, a brow can't
   overlap its own eye, etc.) verified by deliberately cranking the response
   range up and screenshotting the extreme case.
2. **Real bug found and fixed**: both faces used to be normalized against
   whichever period was CURRENTLY selected on the *other* side
   (`chFaceParams(set, otherPeriodSet, thisPeriodSet)`), so picking a
   different comparison period visibly shifted the "current" face even
   though its own underlying data never changed. Both faces now normalize
   against a shared, stable per-metric ceiling — this player's own
   historical max, reusing `historicalDistribution()` (already computed for
   the "Holding steady" narrative section, no new query) — so each face
   depends only on its own period's data. Verified directly: same period A,
   changed only period B, confirmed the left face's rendered geometry is
   bit-for-bit identical before/after.
3. **Interactive "How to read these faces" legend** (collapsed `<details>`,
   centered directly under the faces): tapping a row briefly draws the
   actual dimension it means on BOTH faces — a ↔/↕ arrow, or for the one
   "roundness" row a dashed reference circle — instead of just recoloring
   the shared shape (several rows share the same underlying SVG element,
   e.g. eye width vs. eye openness are the same ellipse's rx vs ry, so a
   flat color highlight alone couldn't tell them apart). Each legend row's
   facial-feature name is followed by the actual metric name it encodes,
   pulled live from `METRICS` (never retyped, can't drift). All 9
   descriptions got an editorial pass — caught two real mismatches in the
   process (the nose/`consist` row was describing something other than what
   that metric actually measures — feedback use between attempts, not
   "consistency"; same for the head-proportion/`range_span` row, which is
   about the spread of your biggest misses specifically, not "best vs.
   worst attempt") and reworded the pupil/`lucky` row so "luckier attempts"
   isn't ambiguous (states plainly: your result vs. what pure chance alone
   would produce).
4. **The separate early/late diverging bar (`buildRangeBar`) is gone** — the
   faces already encode that same pair via left/right asymmetry, the bar
   was pure duplication once the faces existed.
5. **Layout restructure**: page title + intro sentence now live INSIDE the
   card (were floating on the bare page above it); RU/EN toggle moved onto
   the title's own row; the standalone "biggest shift" headline block is
   gone entirely (was asked for, then explicitly asked to be removed again
   with nothing replacing it); each face's period label is now a real
   button sitting ABOVE that face (was a single sentence above both faces,
   then briefly a label below each face, then moved above per the final
   round) and IS the period picker — clicking it opens the same popover as
   before. Only the two faces stay pinned while scrolling now (`.sticky-top`
   — previously also pinned the sentence+headline that no longer exist).
   Vertical spacing tightened throughout (card padding, section margins) per
   an explicit "make it denser" pass.
6. **Period popover now also disables a preset that has ZERO attempts in
   its computed range**, not just one that crosses the precision-level
   boundary (the two conditions get distinct hover-tooltip text) — closes a
   real gap where e.g. "Yesterday" could be clicked and land on a day the
   player didn't play at all, showing the generic "not enough attempts"
   fallback in the faces area with no explanation of why. That fallback
   message still exists as a backstop for the free-form custom-date inputs,
   which can't be pre-disabled the way discrete preset buttons can.
7. **Lede trimmed** — dropped the trailing "Formulas match the app's own
   (SC, Lucky Index, hit rate, etc.); the precision level is fixed..."
   sentence; the sample-size disclaimer sentence is unchanged.

Verified throughout via a local mock-data snapshot technique (real
`report.html`, `fetch()` swapped for an inline dataset, opened as a Claude
Artifact so it's a real live page, not a screenshot) — confirmed visually
and via direct DOM/state inspection at each step, including the period-
independence fix above. Not verified: true narrow-phone (~375px) rendering
specifically — the headless testing tool available this session has its own
minimum browser width (~526px) that couldn't be gone under; the responsive
CSS (`flex:1 1 0`+`min-width:0` on the face columns) is the standard correct
pattern for this and held up fine down to that ~526px floor, but a real
phone check is still worth doing once this is live.

## Reference

Full derivation of the narrative engine's phrasing rules is in the "Metric
Comparison" / "Narrative Feature Decision Log" artifacts (ask Alex for the
links, or check memory — they're tracked there). The native-side change is
in `C:\PlayOfTime\CLAUDE.md`'s "Current Work" for 2026-09-11 once that
session's changelog entry is written.
