# Native → web sync: main-screen quest panel (pips, layout, cheer-up highlight)

Context: native (`android`) went through a multi-round redesign of
the main-screen quest-info panel (`QuestProgress.kt`) between 2026-08-26 and
2026-09-03, driven by Alex's own reference sketches, plus a related change to
how the cheer-up bubble behaves. Nothing here has been ported to the webapp
yet as far as native can tell — the last confirmed web sync (per
`TASKS.md`'s 2026-08-17 entry) predates all of it. This file is the
consolidated spec to port. Full derivation/history, if wanted, is in
`C:\PlayOfTime\CLAUDE.md` under the dated sections named below — this file
is the "just the current state" version so a re-read of the whole changelog
isn't required.

## 1. Arrow bundle → attempt "pips" row (the main ask)

The old quiver-of-arrows visualization for "attempts remaining in the
window" is gone entirely, replaced by a row of small dots — one per
attempt-slot in the current window (33, or 66/99 under indulgence).

**Rendering rule per dot:**
- Already-played attempt → filled circle, colored by that attempt's result
  (green = win, red = early, blue = late), oldest attempt on the left/top.
- Not-yet-played slot → small hollow ring (stroke outline, not filled) in a
  muted/neutral color (native uses base text color at ~43% alpha).

**Layout — single row when window = 33:**
- Exactly 33 dot-slots spread evenly across the full available width.
- Dot diameter ≈ 60% of the per-slot cell width, clamped to a sane min/max
  (native: clamp between ~1.8dp and 6dp — i.e. never vanishes, never bloats
  past the cell).
- Ring stroke width for hollow (unplayed) dots ≈ 45% of the dot's own
  radius, with a small minimum so it doesn't disappear at tiny sizes.

**Layout — multiple rows when window > 33 (indulged, 66 or 99):**
- Wrap into additional rows of 33 slots each, stacked downward. First row =
  oldest 33 attempts of the window, last row = most recent (current) 33.
- Every **odd** row (1-indexed: 2nd row, 4th row, …) is shifted right by
  **half a cell width** relative to the row above/below it — this makes a
  dot in that row sit visually *over the gap* between two dots in the
  neighbouring row, not directly underneath one (brick/masonry stagger, not
  a plain grid). Alex's own phrasing for this: "точка над пространством
  между точками" (a dot above the space between dots).
- Row height and the gap between rows are kept compact/tight (native: 7dp
  row height, 2dp gap) — this is a thin strip, not a tall block.

This whole element replaces the mechanic-label-only version if your current
port still has the arrows or an earlier interim version — remove that and
build this instead.

## 2. Panel layout, full vertical order (top to bottom)

This is the final settled order as of 2026-09-03, after two rounds of
Alex reordering things — port this exact order, not an earlier one if your
notes have something different:

1. **"Level X · Quest X.Y[.Z]"** — one single line, centered horizontally
   across the full panel width. Small/muted text (native: 10sp, medium
   weight, ~63% alpha). The quest number is a three-part mask
   `level.task.substep`, where the third segment (substep) is present ONLY
   when the player is currently inside an indulged subquest (substep ∈
   {1,2,3}); omit that segment entirely at baseline. Example: `Level 2 ·
   Quest 2.4` at baseline, `Level 2 · Quest 2.4.1` one indulgence step in.
2. A noticeably larger vertical gap here (native: 12dp) — Alex explicitly
   asked for more breathing room than an earlier draft had (was 2–6dp,
   looked "stuck together").
3. **The pips row** from section 1.
4. A smaller gap (native: 6dp).
5. **The centered complex**: aim time (green, bold — e.g. `3.00` or `3.00
   ±0.01` with the tolerance suffix dimmer/smaller), then the targets
   row (see section 3), then the mechanic-count label (`"2×"` bold + `" in
   a row"` or `" in total"` at ~85% alpha depending on whether the quest is
   a streak-type (ROW) or a count-type (IN_N / ONE_ONLY) quest), then —
   only while a quest was *just* completed and the completion screen hasn't
   been dismissed yet — a small hand-drawn-style green checkmark to the
   right of all of it.

## 3. Coaxial centering — important, don't skip

The centered complex in step 5 above must be **horizontally centered across
the full panel width**, and that panel width must be the same full width
used by the stopwatch button and the histogram — NOT narrowed by a
neighboring "?" help icon or any other sibling element. This is the same
axis-alignment principle from the 2026-08-23 bottom-nav/histogram sync (if
that already made it to web, this is the identical rule applied to one more
element): the "0" of the stopwatch button's aim tick, the log row's error
bar, the histogram, and now this quest-panel complex should all sit on the
same vertical line — true screen center, not shifted by an asymmetric
sibling's width.

Concretely: if your web version puts a help/"?" icon next to this panel in
a way that eats real horizontal space on one side only, that will shift the
apparent center of the aim+targets+label group away from true center. Fix
is the same pattern as before: give the centered group the panel's full
width and float the help icon as an overlay at the right edge, not as a
width-consuming flex sibling.

## 4. Targets — one detail if not already ported

Native change from 2026-08-23 (separate from today's pips work, but
mention in case it didn't make it over): a target that's been hit no longer
shows a static checkmark badge — its whole circular body becomes a live
copy of the spinning animated brand-mark logo (same one used on the About
screen / app icon animation). No badge overlay on top of it (that was tried
and removed the same day — didn't fit inside the circle). If web's targets
still show a static "hit" checkmark fill, this is the current native
behavior to match, but it's optional/lower-priority than sections 1–3.

## 5. Cheer-up bubble → plain fade highlight (behavior change, not just visual)

The amber popup bubble that used to show congratulatory/transition text at
the top of the main screen is **gone** from the live UI. It's replaced by a
plain amber glow — a soft-colored rounded rectangle behind the quest panel,
**no text at all**, radius ~14dp, color `#F5C02E` at 14% opacity.

- Trigger: shows whenever there's a pending post-event state (quest
  cleared, level cleared, subquest entered/deepened/capped/stalled/stepped
  back, etc. — i.e. whatever previously would have picked a cheer-up
  message to show).
- Dismiss: automatically, the moment the player starts their very first
  attempt of a new session (RUN pressed while the attempt list is still
  empty) — same timing the old bubble used to auto-dismiss on.
- Fade timing: fades in over ~220ms. Fades out over ~1.5s (slow, deliberate)
  specifically on that session-start dismissal — there's no longer a
  separate fast "×"-tap dismissal path since there's no tappable bubble UI
  left to tap.
- The underlying cheer-up text-generation code was **not deleted** on
  native (`CheerUpMessages.kt`/`CheerUpBubble.kt` still exist, just unused)
  in case Alex wants it back — worth knowing in case the web port has an
  equivalent "disable but don't delete" option rather than ripping the
  code out.

## 6. Not relevant to port

These native changes from the same window are Android-specific and have no
web equivalent — skip them:
- Bottom-nav balance gauge reactivity fix (StateFlow vs. stale getter) —
  purely a Compose/Kotlin state-observation bug, not something JS would
  have in the same shape. Worth a quick independent check only if your own
  Ranks/balance indicator is known to sometimes show stale numbers until a
  manual refresh — different bug, different codebase, just flagging the
  symptom class.
- `targetSdk`/`compileSdk` bump to 36, manifest permission cleanup, debug
  build minify settings, WAV→FLAC re-encode, telemetry scaffold (Firebase
  prep) — all native build/platform housekeeping, nothing to mirror.

## Reference

Full day-by-day derivation (why each layout decision landed where it did,
Alex's exact feedback quotes) is in `C:\PlayOfTime\CLAUDE.md`, sections
dated **26 August – 1 September 2026** and the untitled continuation
entries dated **2026-09-01** through **2026-09-03** at the top of the file
(most recent work is appended at the very end of the "What Was Done" list,
above "What Still Needs To Be Done").
