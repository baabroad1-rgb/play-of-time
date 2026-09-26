<?php
// One-off, read-only export: dumps a single player's full raw attempt
// sequence as JSON, ordered by local_id (= their true chronological order).
// Requested by Alex 2026-09-05 for an offline analysis of one real player's
// timing patterns (early/late bias, drift, clustering) — needs per-attempt
// error_ms, not the aggregates leaderboard.php already exposes.
//
// GET attempts_export.php?playerId=<uuid>
//
// playerId is already effectively public — leaderboard.php's own JSON
// response includes every player's raw playerId today, so this endpoint
// doesn't expose anything at a different trust level, just a different
// shape (per-attempt instead of aggregate) for ONE player at a time.
//
// One-off tool, same convention as recompute_sc_backfill.php: drop into
// api/, use it, then delete it (or move it out of api/) — it doesn't need
// to stay around as a permanent public surface.

require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "GET") json_error("GET only", 405);

$playerId = isset($_GET["playerId"]) ? trim((string) $_GET["playerId"]) : "";
if (!looks_like_id($playerId)) json_error("invalid playerId");

$pdo = pot_db();

$p = $pdo->prepare("SELECT id, display_name, level_id, task_id, attempts_total, sc_total, wins_total, created_at FROM players WHERE id = ?");
$p->execute(array($playerId));
$player = $p->fetch();
if (!$player) json_error("player not found", 404);

$stmt = $pdo->prepare(
    "SELECT local_id, session_id, level_id, task_id, accuracy, duration_ms, error_ms, is_win, tolerance_ms, sc, recorded_at
     FROM attempts WHERE player_id = ? ORDER BY local_id ASC"
);
$stmt->execute(array($playerId));
$attempts = $stmt->fetchAll();

json_out(array(
    "ok" => true,
    "player" => $player,
    "attemptCount" => count($attempts),
    "attempts" => $attempts
));
