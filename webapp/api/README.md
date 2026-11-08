# Play of Time — API backend

Small PHP + MySQL backend for the shared leaderboard (native app and webapp both
sync to the same server). See `../../TASKS.md` for the current bug/task backlog
and `webapp/CLAUDE.md` for the overall project brief.

## Setup (fresh server)

1. Create an empty MySQL database via your hosting panel's "MySQL Databases"
   section (Alex's host: manage the DB user's password via phpMyAdmin's
   "User accounts" tab — phpMyAdmin itself doesn't create/host databases,
   your hosting panel's DB section does that part).
2. Run `schema.sql` against it (phpMyAdmin > select the database > "SQL" tab,
   paste and run — or "Import" the file directly), then run
   `migrate_001_add_seq.sql` the same way, in that order.
3. Give `config.php` the real credentials WITHOUT ever putting them in this
   local/synced project folder — see `config.example.php` for the two ways
   to do that (env vars, or an external file outside whatever you upload).
   `config.php` as checked into this folder is a safe placeholder on
   purpose: uploading/overwriting it can never leak or break production
   credentials, because it never carries the real ones.

## Files

- `config.php` — safe to deploy always; loads real credentials from an env
  var or an external file, never carries them itself. `config.example.php` —
  documents the two setup options in detail.
- `db.php` — opens/caches one PDO connection per request.
- `_lib.php` — shared JSON helpers + `sanitize_name()` / `looks_like_id()` /
  `looks_like_token()` validators.
- `sync.php`, `set_name.php`, `leaderboard.php` — the three endpoints the
  clients actually call (see `.htaccess` — everything else is blocked from
  direct access).
- `schema.sql`, `migrate_001_add_seq.sql` — run once, in order, on a new DB.
- `doppelganger_diary.php` — Doppelgänger's diary written by a language model
  (Claude Haiku via OpenRouter, Alex 2026-11-08). The app POSTs one day's
  facts + the rival's memory, gets `{entry, memory}`. System prompt —
  `doppelganger_character.txt`. **Key**: `openrouter_key.php` with
  `<?php return 'sk-or-…';`, created by hand on the server, never in git
  (`.gitignore`) and never deployed over (`deploy-web.yml` exclude), like
  `config.php`; without it the endpoint answers 503 and the app keeps its
  rule-based notes. Limits: 10 per player and 2000 total per UTC day,
  counted in `_dg_quota/` (created on first call, denied to the web by its
  own `.htaccess`, excluded from deploy).
