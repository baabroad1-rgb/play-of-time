# Play of Time — Native App Changes, 9–10 September 2026

For the webapp/Cowork session — what changed on the native Android side
(`ru.iflex.timemvp`) since the last confirmed sync, versions **v13.61 → v13.74**.
Grouped by day, with a distilled action list for webapp parity at the bottom.
Everything below is build-verified (compiles clean); items marked **on-device
verified** were also checked live on the emulator, everything else is
code-review-verified only.

---

## 9 September 2026 (v13.61 → v13.70)

### Week as a real entity in the Sessions tree (Analytics)
`PeriodFilter.WEEK` (rolling 7-day filter) already existed and was already
wired into the Analytics period pill. What was missing: the *Sessions tree*
(Year → Month → Day, no Week level) didn't have a Week row.
- New `SessionWeekNode` sits between Month and Day: `buildTree()` groups each
  month's day nodes by `Calendar.WEEK_OF_YEAR`, label = numeric day-range
  (`"1–7"`) since the month name already shows on the parent row. Weeks that
  straddle a month boundary land in two separate nodes (one per month) —
  intentional.
- New `TreeWeekRow` (indent 18dp, between Month's 12dp and Day's 24dp) —
  same lucky/mean/att4win columns as every other tree level.
- Auto-expand-to-most-recent-session now also computes/expands the correct
  week key.

### Analytics "?" → "×" help-icon toggle
Main screen's "?" already flipped to "×" in help mode; Analytics's own "?"
(period pill) never got the same treatment. One-line fix, same pattern.

### Main screen — deliberate rightward shift of histogram/log/progress-bar/targets
The 22–23 August coaxial-centering work made left/right margins symmetric
around the histogram, but the left column (numbers/time) is always visually
dense while the right column (SC values) usually isn't — "equal" margins
never read as equal. Shifted the whole plot group right on purpose:
- `CHART_SC_PAD` (right inset) reduced from 64dp back to **32dp** (its
  original pre-22-Aug width) — landed here after trying 12dp first and
  Alex calling it "too far." Net center shift = 16dp.
- `StopWatchButton`'s progress bar, and `QuestProgress`'s targets row
  ("мишени" — initially missed in the first pass, caught and fixed same day)
  both got the identical 16dp shift so histogram/log/progress-bar/targets
  stay mutually coaxial, just moved together.
- **Known, accepted, still open**: the bottom-nav Analytics tab icon is
  aligned to the *old* true center from the 23 August coaxiality project —
  it's no longer coaxial with this shifted group. Not fixed, flagged only.

### Period-offset paging (day/week/month/session back-forward)
`PeriodFilter.rangeMsForOffset()` + a new `getAttemptsInRange` DAO query +
`AnalyticsUseCase`/`AnalyticsData` gained a full "page back/forward" feature,
with `‹ ›` chevrons flanking the period-context caption on Analytics (caption
also enlarged/centered). Build-verified only — not re-walked live on-device
after the same day's later layout-shift changes landed on top of it.

### `PeriodFilter.label` — "Today" → "Day"
Confirmed via grep this is read only on the Analytics pill, not the main
screen — doesn't touch main-screen text.

### Twin/neighbor-shot log-row outline — legibility (superseded 10 Sept, see below)
Reduced the outline stroke's opacity across several iterations that day
(1.0 → 0.45 → 0.30 → 0.05). **This exact value was wrong and got corrected
the next day — see the 10 September entry below for the current, real
behavior.** Mentioning only so the history makes sense if you're diffing.

---

## 10 September 2026 (v13.70 → v13.74)

### 1. Twin/neighbor-shot outline — corrected opacity, then made intersection-aware
Two rounds of fixes on the same feature (hand-drawn freehand oval marking
twin-shot/neighbor-shot pairs in the attempt log, `SessionStatisticItem.kt`):

**Round 1 — fixed a misread instruction.** The 9 September "0.05" value was a
misunderstanding: "reduce brightness by 5%" was implemented as "final alpha
= 0.05" (outline nearly invisible) instead of "keep 95% of original" (alpha
= 0.95). Corrected to **0.95**.

**Round 2 — intersection-aware dimming (current, final behavior).** Alex's
follow-up: keep the outline at its normal brightness everywhere, but dim it
to 30% specifically where the stroke geometrically crosses the "#"/time text
columns (where digits actually sit) — not a flat reduction everywhere.
Implemented via two clipped draw passes of the *same* stroke path:
```kotlin
clipRect(textRegion, clipOp = ClipOp.Difference) { drawPath(path, color.copy(alpha = 0.95f), stroke) }
clipRect(textRegion, clipOp = ClipOp.Intersect)  { drawPath(path, color.copy(alpha = 0.30f), stroke) }
```
`textRegion` = the "#" + time column rect (`x: 0..20dp+timeTextWidthPx`,
full row height for the group). Outside that rect: 0.95 alpha (full,
unchanged). Inside it (over the digits): 0.30 alpha. Color/hue untouched
throughout both rounds — only opacity ever changed.

### 2. Help-mode scroll — real architectural fix (two attempts, second one correct)
Alex reported: in help mode, Analytics doesn't scroll (should). This had
already been "fixed" once earlier the same day with a touch-slop-aware
gesture detector — that fix was **necessary but not sufficient**.

**Root cause (confirmed, not guessed):** the tap-catching overlay lived as a
**sibling** of the scrollable LazyColumn (both children of the same parent
Box), not as its **ancestor**. Compose doesn't guarantee two independent
sibling pointer-input handlers observe each other's gesture consumption —
so even a well-behaved, non-consuming touch-slop check on the overlay side
couldn't reliably let the LazyColumn's own scroll gesture win underneath it.

**Fix:** restructured `HelpTapOverlay.kt` into two pieces:
- `Modifier.helpTapGestures(zones, questionRect, origin, onSelect, onExit)`
  — a `Modifier` extension carrying the touch-slop-aware tap-detection logic,
  applied **directly on the same outer Box that already wraps the scrollable
  content** on both MainScreen and StatScreen (a true ancestor now, not a
  sibling). Compose's pass ordering (Main pass: inside-out) then guarantees
  the LazyColumn, as the descendant, gets first crack at consuming a drag —
  our ancestor-level handler correctly bails once it sees that consumption.
- `HelpTapOverlayVisual(...)` — the purely visual remainder (selection
  border + description bubble), no pointer input of its own.

Both `MainScreen.kt` and `StatScreen.kt` were updated to apply
`.helpTapGestures(...)` to their own outer content Box and call
`HelpTapOverlayVisual` instead of the old `HelpTapOverlay`. **On-device
verified?** No — build-verified only, not yet re-tested live on emulator
after this change.

### 3. Help mode — tapping Sessions-tree rows now shows help instead of expanding them
Same underlying bug class as #2, different manifestation: Alex reported that
tapping tree rows (log/months/weeks/days) in help mode didn't show help text
— it just expanded/collapsed the row normally. Root cause: each tree row
(`TreeYearRow`/`TreeMonthRow`/`TreeWeekRow`/`TreeDayRow`/`TreeSessionRow`)
has its own `.clickable{}` for expand/collapse, and being a *descendant* of
the outer Box, it consumes the tap *before* the ancestor-level
`helpTapGestures` handler's turn — so the row's own toggle always won.
**Fix:** every tree-row composable now takes a `helpMode: Boolean = false`
param and disables its own clickable while help mode is active
(`.clickable(enabled = !helpMode) { ... }`) — a disabled `clickable` doesn't
consume the tap, so it falls through cleanly to the ancestor's help-zone
handler, which shows the "Sessions" help bubble instead. Not on-device
verified this session.

### 4. Quest-highlight animation — hold until first RUN, then fade to zero
Alex: keep the quest-area amber highlight visible at full strength until the
player starts playing; only once they press RUN on the *first* attempt of a
session should it gradually fade away.

Previous behavior (from a same-day-earlier fix) had the highlight flash to a
peak brightness on appearing, then **auto-settle down to a faint steady
level within 900ms regardless of user action** — which read as "the
highlight already faded" well before the player had done anything, exactly
contradicting what was wanted.

**Fix:** replaced the whole animation with a single controlled
`Animatable<Float>`:
- On appearing: fades in to peak brightness (220ms) and **holds there
  indefinitely** — no auto-decay while waiting.
- On RUN with `attempts.isEmpty()` (start of the first round): fades the
  same `Animatable` from its current value down to **0** over 1500ms —
  the only path that produces a full fade-to-zero.
- Any *other* reset of the trigger (a new quest arriving before RUN, an
  immediate reset on quest failure, etc.) still clears quickly (160ms) — the
  long fade is reserved specifically for "the player started playing."
- Replaced the old `AnimatedVisibility`+`fadeIn`/`fadeOut` machinery
  entirely with direct alpha control (cleaner, and those imports are now
  removed as dead).

### 5. New Settings toggles (both default ON)
- **"Salute & result sounds"** — separate from the existing Cheer-Ups
  toggle (that one gates bubble text/highlight; this is sound-only). Gates
  the salute fireworks sound + ambient bed on the GreetingView (visual
  animation still plays even when muted — only sound is affected), plus the
  HOORAY! and "sorry" (quest-fail) sound effects.
- **"Twin/neighbor outline"** — gates whether the freehand ovals from item
  #1 above draw at all in the attempt log.
- Both wired end-to-end: `LocalStorageService` (two new boolean prefs,
  default true) → `SettingsContract`/`SettingsViewModel` → two new
  `ToggleBox` rows in `SettingScreen.kt`.

### 6. Real bug fix — intermittent sound cutoff ("plays for a few ms, then stops")
Alex asked what could cause this; root-caused and fixed, not just diagnosed.
`playRawIfAvailable()` (used for HOORAY!/sorry sounds) created each
`MediaPlayer` as a bare local variable with nothing else holding a
reference. If Android's GC ran shortly after `.start()` — plausible right at
quest-completion, when a burst of `setState`/animation objects gets
allocated — the player could be garbage-collected mid-playback, cutting the
sound short. Classic, well-documented `MediaPlayer` gotcha. **Fix:** keep
played instances in a class-level `MutableList<MediaPlayer>` until their own
completion callback releases them, so they can't be collected early.

### 7. Text/label fixes
- "Modes" (Analytics — Range of Deviations card) renamed to **"Top-frequency
  error"** everywhere it's user-facing: the card label itself, and the
  matching help-bubble description text. The label's reserved layout width
  is now measured from the real new text (was a hardcoded 42dp sized for
  the old, shorter word — would have clipped).
- Touch-sampling "Sample rate" readout and its explanatory paragraph in
  Settings now show **seconds** (`0.004 s`) instead of milliseconds
  (`4.0 ms`) — same numbers, different unit, per Alex's explicit ask.
- About screen: "Semyon Kulikov" → **"Semion Kulikov"** (spelling fix, `i`
  not `y`).

### Build
v13.70 → v13.74 (four builds today, each after a batch of fixes). APK on
Desktop: `play-of-time-v13.73.apk` is the last one actually delivered to
Alex; v13.74 exists only as the auto-bumped *next* version number, no build
was produced at that number yet.

---

## Distilled action list — what's likely webapp-relevant

**Probably wants a webapp equivalent (native-only feature or fix, not yet
ported):**
- Item 6 above (MediaPlayer GC bug) — check whether webapp's own audio
  playback (HTMLAudioElement / Web Audio) has an analogous "reference held
  only locally, could be cut short" pattern. Different platform, different
  failure mode likely, but worth a sanity check since the *symptom*
  (`sometimes cuts off early`) is the kind of thing that could show up
  independently on web too.
- Item 5 — new Settings toggles for salute/result sounds and twin/neighbor
  outline. If webapp has equivalent settings (sound toggle, log-row
  highlighting), these two new options may be worth mirroring there too.
- Item 7 — "Modes" → "Top-frequency error" label rename, and the
  Kulikov spelling fix (webapp's own About/Credits text almost certainly has
  the same "Semyon" typo — this project's convention has been to keep
  Credits text in sync between native and webapp).

**Native-only, not applicable to webapp (different UI paradigms — scroll
gesture arbitration, Jetpack Compose LazyColumn internals, Android
`clickable`/`pointerInput`):**
- Items 2 and 3 (help-mode scroll fix, tree-row help-tap fix) — these are
  Compose-specific gesture-arbitration bugs; webapp's DOM-based click/scroll
  handling doesn't have the same failure mode. No action needed unless
  webapp's own help-mode overlay (if it has one) shows a similar symptom —
  worth a quick manual check, not a code port.
- Item 4 (quest-highlight hold-then-fade animation timing) — if webapp has
  its own equivalent highlight/cheer-up visual, the *behavioral spec*
  (hold at full strength until first RUN, then 1.5s fade to zero) is worth
  matching even though the CSS/JS implementation would be entirely
  different code.

**Cosmetic, low priority, port only if convenient:**
- Item 1 (twin/neighbor outline intersection-dimming) — webapp's own
  Canvas-based log rendering, if it draws similar twin/neighbor markers,
  could use the same "normal opacity outside text, 30% opacity where it
  crosses digits" idea. Not urgent.

---

*Generated from the native session's own direct changes today — not
independently re-verified against the live webapp. Cross-check against
`webapp/TASKS.md`'s own ledger before acting on anything above.*
