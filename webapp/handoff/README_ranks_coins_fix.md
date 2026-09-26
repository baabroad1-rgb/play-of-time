# Ranks "Coins" mismatch — server-side fix (ready to apply)

Context for whoever picks this up: Play of Time's webapp syncs each played
attempt to a leaderboard ("Ranks"). Alex noticed his in-app coin total
(Serenity Coins) doesn't match what Ranks shows for him. This was
diagnosed, reproduced, and fixed on the client side already (in this same
session — **`app.20260823l.js`, not yet live on the server** — supersedes
an earlier `k.js` that had a review-caught regression and was never
uploaded, see `TASKS.md`'s 2026-09-03 sixth update if curious; client and
server changes are staged together, neither has shipped yet, see the
deployment-order warning below), and the two files in this folder are the
matching server-side half. **Both are already written, syntax-checked
(`php -l`), and tested end-to-end against a real PHP + SQLite run (not
just reviewed) — they're ready to drop in and run, not a draft.**

## Deployment order matters — read this before uploading `sync.php`

Do not put this `sync.php` live before `app.20260823k.js` +
`index.html` are live. A second, related bug was found after this
server-side fix was first drafted: the client's "Erase all data" button
used to restart local attempt ids at 1 without resetting the server-synced
high-water mark, so a player who erases and replays could end up sending
an old, already-used local id again. Under the OLD `sync.php` (skip-if-
seen) that was harmless. Under THIS `sync.php` (update-if-seen) it would
silently overwrite a real historic attempt's `sc` with an unrelated new
attempt's value, while every other column on that row stays stuck on the
old attempt — a corrupted row, not just a stuck sync. `app.20260823k.js`
fixes the id-reuse at the source (erase now preserves the id counters), so
once it's live this `sync.php` is fully safe. If for some reason the
client can't go live first, hold off on this file too and ask — don't
apply just one half.

## The bug, in one paragraph

Every attempt's Serenity Coin value (`sc`) is computed from its neighbours
in the player's local attempt history (`calculateScm(prev, curr, next)` in
`app.js`). The newest attempt has no real `next` yet, so it's temporarily
computed using itself as a stand-in — a materially different (generally
higher) value than what it settles to once the real next attempt exists.
The client used to sync 1200ms after each attempt, which (given the game's
own ~3-second-per-attempt pacing) is almost always *before* the next
attempt exists — so the server was permanently stuck with that transient,
inflated value for nearly every attempt, because `sync.php` skipped any
`local_id` it had already seen instead of updating it, and the client only
ever resent brand-new attempt ids.

## What already changed (client, not your job, just context)

`app.js`'s `pendingAttemptsForSync()`: filter changed from `a.id > lastId`
to `a.id >= lastId`, so the client now always resends the most-recently
synced attempt too, alongside anything new. That's the only attempt whose
`sc` can still be "pending finalization" — proven by how `calculateScm`'s
neighbour references settle (each attempt's `scm` only ever changes once,
exactly when it stops being the newest).

## What you need to do

### 1. Replace `api/sync.php` with `sync.php` in this folder

Diff summary: the block that used to `SELECT local_id ... WHERE local_id IN
(...)` and skip any match now also selects the currently-stored `sc`, and
UPDATEs it in place (only when the incoming value actually differs) instead
of skipping. No other behavior changed — insert path, players
upsert/rename logic, rollup recompute, transaction handling, all untouched.
Plain `SELECT` + `INSERT`/`UPDATE`, deliberately not a native `ON DUPLICATE
KEY UPDATE` — this file is meant to keep running against both MySQL
(production) and SQLite (`POT_TEST_DSN`, the existing local test harness
convention documented in `db.php`), and those two don't share upsert syntax.

Upload this the same way any other `api/*.php` file goes live on this
project (Sprutio file manager / whatever the current deploy path is) —
follow the project's DEPLOYMENT RULE in `TASKS.md`, same as any other file:
don't mark it confirmed-live until it's actually been checked working.

### 2. Run `recompute_sc_backfill.php` once, against production, to fix already-corrupted data

The 27 players already on the live leaderboard (as of 2026-09-03) all have
this drift baked into their stored `attempts.sc` values from before today's
fix — the fix above only stops it from getting worse going forward, it
doesn't retroactively correct history.

CLI, if whoever's running this has shell access:

```
php recompute_sc_backfill.php            # dry run — prints what WOULD change, writes nothing
php recompute_sc_backfill.php --apply    # writes the corrected sc + sc_total values
```

**No CLI/SSH? This file also works as a plain HTTP GET**, gated by a
one-time token hardcoded in the file itself (`zYmaCmVolKRluEy91qS10IZPKPYvmI3n`)
so it can't be triggered by a stray bot while it's sitting in a public
folder — added specifically because neither Code's session nor Alex's
Cowork session has CLI/SSH/DB creds for this server, only normal web
access:

```
GET /api/recompute_sc_backfill.php?token=zYmaCmVolKRluEy91qS10IZPKPYvmI3n           (dry run)
GET /api/recompute_sc_backfill.php?token=zYmaCmVolKRluEy91qS10IZPKPYvmI3n&apply=1   (writes)
```

Either way: run the dry run first and eyeball the output — one line per
affected player, old and new `sc_total`. Then run `--apply` /
`&apply=1`. It's safe to run more than once (idempotent — a second run
reports 0 changes, verified in testing, CLI and HTTP paths both).

It uses whatever `db.php`/`config.php` already point at (same DB connection
as every other endpoint here). Whoever uploads it: **delete it (or move it
out of `api/`) right after the apply run succeeds** — a write-capable
endpoint doesn't need to stay parked in a public folder longer than the one
time it takes to use it, token or not.

### 3. Verify

- Hit `GET api/leaderboard.php` before and after the backfill for a couple
  of known players and confirm `scTotal` visibly changed (should generally
  go *down*, matching the bug's direction).
- Have Alex open the app, check his own in-app Serenity Coins total, then
  check his Ranks "Coins" entry — they should now match (small floating-
  point rounding aside).
- Play a couple of fresh attempts after both pieces are live and confirm
  the two numbers stay in sync going forward, not just at backfill time.

## Files in this folder

- `sync.php` — drop-in replacement for `api/sync.php`.
- `recompute_sc_backfill.php` — one-time migration script, run once then
  remove.
- This README.

Full write-up of the investigation, the reproduction that found this, and
the exact numbers involved are in `TASKS.md` — search for "2026-09-03" —
if you want the full derivation instead of just the fix.
