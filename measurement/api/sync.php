<?php
// POST {playerId, syncToken, measurements: [...]} → {displayName, synced}
//
// Identity as in the game: a random id + a paired secret token made once on
// the device. The first sync registers the player and gives them the next
// "Player NNN" name; later syncs must bring the same token. Measurements are
// upserted by (player, local id), so re-sending one is harmless.
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "POST") json_error("POST only", 405);

$input = json_input();
$playerId = isset($input["playerId"]) ? trim((string) $input["playerId"]) : "";
$syncToken = isset($input["syncToken"]) ? trim((string) $input["syncToken"]) : "";
if (!looks_like_id($playerId) || !looks_like_token($syncToken)) json_error("invalid playerId/syncToken");

$list = isset($input["measurements"]) && is_array($input["measurements"]) ? $input["measurements"] : array();
if (count($list) > 500) json_error("too many measurements");

$pdo = m_db();
$now = gmdate("Y-m-d H:i:s");

$sel = $pdo->prepare("SELECT seq, sync_token, display_name FROM m_players WHERE id = ?");
$sel->execute(array($playerId));
$player = $sel->fetch();
if ($player === false) {
    $ins = $pdo->prepare("INSERT INTO m_players (id, sync_token, display_name, created_at, updated_at) VALUES (?, ?, '', ?, ?)");
    $ins->execute(array($playerId, $syncToken, $now, $now));
    $sel->execute(array($playerId));
    $player = $sel->fetch();
    $name = measurement_player_name($player["seq"]);
    $pdo->prepare("UPDATE m_players SET display_name = ? WHERE id = ?")->execute(array($name, $playerId));
    $player["display_name"] = $name;
} elseif (!hash_equals((string) $player["sync_token"], $syncToken)) {
    json_error("token mismatch", 403);
}

$num = function ($v, $min, $max) {
    if ($v === null || !is_numeric($v)) return null;
    $v = (float) $v;
    return ($v < $min || $v > $max) ? null : $v;
};

$del = $pdo->prepare("DELETE FROM m_measurements WHERE player_id = ? AND local_id = ?");
$add = $pdo->prepare("INSERT INTO m_measurements
    (player_id, local_id, level_id, attempts, wins, late_avg_ms, early_avg_ms, per_minute, per_win, started_ms, ended_ms)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
$synced = 0;
$pdo->beginTransaction();
foreach ($list as $m) {
    if (!is_array($m)) continue;
    $localId = isset($m["id"]) ? (int) $m["id"] : 0;
    $level = isset($m["levelId"]) ? (int) $m["levelId"] : 0;
    $attempts = isset($m["attempts"]) ? (int) $m["attempts"] : 0;
    $wins = isset($m["wins"]) ? (int) $m["wins"] : 0;
    $started = isset($m["startTime"]) ? (int) $m["startTime"] : 0;
    $ended = isset($m["endTime"]) ? (int) $m["endTime"] : 0;
    if ($localId < 1 || $level < 1 || $level > 3 || $attempts < 1 || $attempts > 1000 ||
        $wins < 0 || $wins > $attempts || $started <= 0 || $ended < $started) continue;
    $del->execute(array($playerId, $localId));
    $add->execute(array($playerId, $localId, $level, $attempts, $wins,
        $num(isset($m["lateAvgMs"]) ? $m["lateAvgMs"] : null, 0, 2000),
        $num(isset($m["earlyAvgMs"]) ? $m["earlyAvgMs"] : null, 0, 2000),
        $num(isset($m["perMinute"]) ? $m["perMinute"] : null, 0, 600),
        $num(isset($m["perWin"]) ? $m["perWin"] : null, 1, 1000),
        $started, $ended));
    $synced++;
}
$pdo->prepare("UPDATE m_players SET updated_at = ? WHERE id = ?")->execute(array($now, $playerId));
$pdo->commit();

json_out(array("displayName" => $player["display_name"], "synced" => $synced));
