# Deploy: leaderboard Lucky Index + Hit Rate formula fix

Alex: "лаки индекс в лидборде не совпадает со значением в аналитикс. исправь. проверь так же остальные параметры."

Two of the leaderboard's four columns used formulas that genuinely diverge
from what the native Analytics screen shows for the same player — not a
display bug, a real formula mismatch. Both fixed. The other two columns
(Lvl, Coins) were audited and are fine — see "Other parameters checked"
below.

## Files to upload (both already live, both changed)

- `webapp/api/_lib.php` — added `is_true_three_win()`, a faithful PHP port
  of `AnalyticsUseCase.kt`'s `countTrueThree()`.
- `webapp/api/leaderboard.php` — rewritten to compute Lucky Index and Hit
  Rate from real per-attempt rows instead of SQL aggregates.

## 1. Lucky Index — root cause and fix

`leaderboard.php` was sizing the "outcome slots" denominator from the
**untrimmed** `MIN(error_ms)`/`MAX(error_ms)` across a player's whole
history — this was a known, explicitly-flagged simplification in the old
code's own comment ("a single outlier attempt can inflate... not claimed to
match the in-app headline number exactly"). The native Analytics screen
(`AnalyticsUseCase.kt`) instead uses the **3rd/97th percentile** of signed
errors, trimming outliers from each end before sizing the range — same rule
the project already applies to `Range of Deviations` and every other
Lucky Index call site (see the 13-14 August "diverges by multiples" fix
that already unified the other two implementations to this rule; the server
was the one holdout).

Fixed: `leaderboard.php` now pulls every attempt's `error_ms` per player
(scoped to `current_epoch`, same as before), sorts it, and computes the
P3/P97 index the same way (`(int)($n * 0.03)`/`(int)($n * 0.97)`, clamped to
`[0, n-1]` — PHP's `(int)` cast truncates toward zero the same way Kotlin's
`.toInt()` does, so this is a faithful port, not an approximation).

## 2. Hit Rate ("Acc%" column) — root cause and fix

This one was quietly wrong in a different way: `hitRatePct` was
`winsTotal / attemptsTotal * 100` — the raw `is_win` DB column, which is
tolerance-based and includes **every eased subquest win** across the
player's whole history (a win at ±10ms tolerance during an indulged
subquest counts the same as an exact 3.000). The native Analytics screen's
own "Hit rate" row (`CHANCE OF WIN` card) is a *different* number —
`trueThreeHitRatePct`, computed via `countTrueThree()`: each attempt's
duration is rounded to **its own accuracy level's grid** (deci=100ms,
centi=10ms, milli=1ms) and checked against `tolerance_ms` (+ a resolution
floor that's currently a permanent no-op, `TOUCH_RESOLUTION_FLOOR_MS = 0` in
`Utils.kt`). For a player with a long indulged-subquest history these two
numbers can be very different — `winsTotal`-based hit rate reads much more
generous than what the app itself calls "Hit rate."

Fixed: `leaderboard.php` now counts, per attempt, whether it passes
`is_true_three_win()` (the new `_lib.php` helper) and divides by
`attemptsTotal` — this is the exact same criterion `trueThreeHitRatePct`
uses, just computed server-side from synced rows instead of on-device.

## 3. Other parameters checked (per Alex's "проверь остальные" ask)

- **`scTotal`** — a raw sum of `attempts.sc`, same rollup `sync.php`
  already maintains and the same number the app's own top bar and Analytics
  "Serenity Coins" both read. No formula to diverge — left untouched.
- **`levelId`/`taskId`** — raw synced player state, not derived. Untouched.
- **`winsTotal`/`attemptsTotal`** — raw rollup counts. Not shown as a rate
  on their own anywhere on the leaderboard table itself (only combined into
  `hitRatePct`, which is now fixed), so no independent mismatch to chase.
  Left untouched — they're the correct inputs for other things (e.g. the
  native `ComparePlayersDialog`'s own client-side "Attempts per win," a
  separate, already-existing feature out of scope for this fix).
- **`combinedScore`** — leaderboard-only ranking key
  (`levelId*1000 + taskId*100 + min(99, scTotal)`), has no Analytics
  equivalent to match against. Untouched.

## Verified before writing this doc

Built two throwaway SQLite datasets and independently re-derived both
formulas in a standalone Python script (not just re-reading the PHP), then
ran the actual rewritten `leaderboard.php` against each over real HTTP and
diffed the server's numbers against the independently-computed expected
ones:

- **20-attempt case** (deliberately includes two exact clean-threes and two
  tolerance-eased "wins" that are NOT clean threes): server's
  `hitRatePct=20.0` and `luckyIndex=19.6` matched the hand-computed
  reference exactly — confirms the eased wins are correctly excluded from
  Hit Rate's numerator vs. what the old `winsTotal`-based formula would
  have given (100%, since all 4 of those rows had `is_win=1`).
- **100-attempt case** with 10 genuine outliers (5 each direction, far
  outside the normal spread): confirmed the P3/P97 trim actually changes
  the computed range vs. raw min/max (750ms span vs. 1000ms span in this
  run) and that the server's `luckyIndex` matches the trimmed-formula
  reference, not the untrimmed one.

Both test harnesses and their throwaway SQLite files were removed after the
checks passed — nothing left behind in the webapp tree.

## No native changes needed

Both fixed fields are server-computed and sent as-is in the JSON
(`LeaderboardRow.luckyIndex`/`hitRatePct` just mirror whatever the server
returns) — the native app needs no rebuild for this fix, only the two PHP
files need re-uploading.
