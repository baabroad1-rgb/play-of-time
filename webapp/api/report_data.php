<?php
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "GET") json_error("GET only", 405);

// Backs report.html (tap your own leaderboard row -> personalized narrative
// report). Same ownership proof as sync.php/set_name.php: playerId + the
// device's own syncToken, hash_equals against players.sync_token — the
// token travels in the URL's query string (native opens it via
// uriHandler.openUri(...), see LeaderboardScreen.kt onSelectRow), which is a
// known, accepted tradeoff (browser-history/referrer exposure) given this
// system's anonymous, low-stakes nature.
$playerId = isset($_GET["playerId"]) ? trim((string) $_GET["playerId"]) : "";
$token = isset($_GET["token"]) ? trim((string) $_GET["token"]) : "";

if (!looks_like_id($playerId) || !looks_like_token($token)) {
    json_error("invalid playerId/token");
}

$pdo = pot_db();

$stmt = $pdo->prepare("SELECT sync_token, display_name, current_epoch FROM players WHERE id = ?");
$stmt->execute(array($playerId));
$player = $stmt->fetch();
if ($player === false) json_error("unknown player", 404);
if (!hash_equals((string) $player["sync_token"], $token)) json_error("token mismatch", 403);

$currentEpoch = (int) $player["current_epoch"];

// "Current" precision tier = the accuracy of the player's most recent
// attempt in their current epoch (matches the narrative engine's own
// D13 rule: compare only within one precision tier, the player's current
// one — levels aren't compared against each other).
$lastStmt = $pdo->prepare(
    "SELECT accuracy FROM attempts WHERE player_id = ? AND epoch = ? ORDER BY recorded_at DESC, id DESC LIMIT 1"
);
$lastStmt->execute(array($playerId, $currentEpoch));
$last = $lastStmt->fetch();

if ($last === false) {
    json_out(array("ok" => true, "displayName" => $player["display_name"], "acc" => null, "attempts" => array()));
}

$accuracy = (int) $last["accuracy"];
$accCodes = array(1 => "ds", 2 => "cs", 3 => "ms");
$accCode = isset($accCodes[$accuracy]) ? $accCodes[$accuracy] : "ds";

$rowsStmt = $pdo->prepare(
    "SELECT session_id, error_ms, recorded_at FROM attempts
     WHERE player_id = ? AND epoch = ? AND accuracy = ?
     ORDER BY session_id ASC, recorded_at ASC, id ASC"
);
$rowsStmt->execute(array($playerId, $currentEpoch, $accuracy));
$rows = $rowsStmt->fetchAll();

// n = attempt-number-within-session. MySQL 5.7 (this project's baseline, per
// schema.sql's own compatibility note) has no window functions, so this is a
// plain running counter per session_id instead of a ROW_NUMBER() query.
$counters = array();
$attempts = array();
foreach ($rows as $r) {
    $sid = (int) $r["session_id"];
    if (!isset($counters[$sid])) $counters[$sid] = 0;
    $counters[$sid]++;
    $ts = strtotime($r["recorded_at"] . " UTC");
    $attempts[] = array(
        "t" => gmdate("Y-m-d\TH:i:s\Z", $ts === false ? time() : $ts),
        "s" => $sid,
        "n" => $counters[$sid],
        "e" => (int) $r["error_ms"],
    );
}

json_out(array(
    "ok" => true,
    "displayName" => $player["display_name"],
    "acc" => $accCode,
    "attempts" => $attempts,
));
