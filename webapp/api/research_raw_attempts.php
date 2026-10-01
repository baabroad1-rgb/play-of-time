<?php
// RESEARCH TOOL (Alex + Claude, 2026-10-01) — not a feature, not linked from
// the app. Written for the "behavioral trajectory" exploration in
// docs/BEHAVIORAL_TRAJECTORY_DESIGN.md: trying candidate per-player trait
// patterns (feedback-blindness, clutch fragility, overcorrection, …) needs
// the raw attempt sequence, not an aggregate — unlike research_streak_pressure.php,
// which only ever returns summary numbers.
//
// No PII: only the columns below, never display_name or sync_token.
// player_id is the same opaque client-generated UUID already public on the
// leaderboard — this endpoint adds no new exposure of who a player is, only
// of the shape of their attempts.
//
// GET only, no free-form SQL — the only inputs are the few typed filters
// below, each cast/clamped before use. A `key=` query param gates against
// casual discovery of the URL; it's friction, not a real secret (nothing
// behind it is sensitive on its own) — change it here if it ever leaks
// somewhere public.
//
// Usage: /api/research_raw_attempts.php?key=...&kind=attempts[&level=1][&task=4][&since=2026-09-01][&limit=50000]
//        /api/research_raw_attempts.php?key=...&kind=players

require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "GET") json_error("GET only", 405);

$RESEARCH_KEY = "pot-research-2026-10-01-7f3c9a";
if (($_GET["key"] ?? "") !== $RESEARCH_KEY) json_error("forbidden", 403);

$pdo = pot_db();
// Same self-migration as sync.php / research_streak_pressure.php, so this
// works even against a DB that hasn't taken a recorded_ms write yet.
try {
    $pdo->query("SELECT recorded_ms FROM attempts LIMIT 0");
} catch (Exception $e) {
    try { $pdo->exec("ALTER TABLE attempts ADD COLUMN recorded_ms BIGINT NULL"); } catch (Exception $ignored) {}
}

$kind = $_GET["kind"] ?? "attempts";

if ($kind === "players") {
    $stmt = $pdo->query(
        "SELECT id AS player_id, level_id, task_id, sc_total, attempts_total,
                wins_total, app_version, created_at, updated_at
         FROM players
         ORDER BY created_at"
    );
    json_out(array(
        "generatedAt" => gmdate("c"),
        "kind" => "players",
        "rows" => $stmt->fetchAll(),
    ));
}

if ($kind !== "attempts") json_error("kind must be 'attempts' or 'players'", 400);

$levelId = isset($_GET["level"]) ? (int) $_GET["level"] : null;
$taskId  = isset($_GET["task"])  ? (int) $_GET["task"]  : null;
$since   = isset($_GET["since"]) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $_GET["since"]) ? $_GET["since"] : null;
$limit   = isset($_GET["limit"]) ? max(1, min(200000, (int) $_GET["limit"])) : 50000;

$where  = array();
$params = array();
if ($levelId !== null) { $where[] = "level_id = ?";    $params[] = $levelId; }
if ($taskId  !== null) { $where[] = "task_id = ?";     $params[] = $taskId; }
if ($since   !== null) { $where[] = "recorded_at >= ?"; $params[] = $since . " 00:00:00"; }
$whereSql = $where ? ("WHERE " . implode(" AND ", $where)) : "";

$stmt = $pdo->prepare(
    "SELECT player_id, session_id, local_id, level_id, task_id, accuracy,
            duration_ms, error_ms, is_win, tolerance_ms, sc, recorded_at, recorded_ms
     FROM attempts
     $whereSql
     ORDER BY player_id, session_id, local_id
     LIMIT $limit"
);
$stmt->execute($params);
$rows = $stmt->fetchAll();

json_out(array(
    "generatedAt" => gmdate("c"),
    "kind"        => "attempts",
    "filters"     => array("level" => $levelId, "task" => $taskId, "since" => $since, "limit" => $limit),
    "rowCount"    => count($rows),
    "rows"        => $rows,
));
