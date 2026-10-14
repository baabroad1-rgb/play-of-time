<?php
// Measurement leaderboard API — shared helpers. A copy of the game's
// webapp/api/_lib.php basics (JSON in/out, id/token checks), not a link:
// the measurement version stays independent of the game.
header("Content-Type: application/json; charset=utf-8");
header("Cache-Control: no-store");

function json_input() {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);
    return is_array($data) ? $data : array();
}

function json_out($data, $status = 200) {
    http_response_code($status);
    echo json_encode($data);
    exit;
}

function json_error($message, $status = 400) {
    json_out(array("error" => $message), $status);
}

function looks_like_id($value) {
    return is_string($value) && preg_match('/^[a-f0-9-]{20,40}$/i', $value) === 1;
}

function looks_like_token($value) {
    return is_string($value) && preg_match('/^[a-f0-9]{16,40}$/i', $value) === 1;
}

// Default name (Alex 2026-10-14): "Player 001", "Player 002", … by join order.
function measurement_player_name($seq) {
    return sprintf("Player %03d", (int) $seq);
}

// A typed name, cleaned: control characters out, at most 24 characters
// (the same cap as the input field). Empty means "give me my Player NNN
// back". Same rule as the game's api/_lib.php, copied, not linked.
function measurement_clean_name($raw) {
    $name = trim((string) $raw);
    $name = preg_replace('/[\x00-\x1F\x7F]/u', '', $name);
    $name = function_exists('mb_substr') ? mb_substr($name, 0, 24, 'UTF-8') : substr($name, 0, 24);
    $name = trim($name);
    return $name === "" ? null : $name;
}

// Finds the player or registers them (Alex 2026-10-14: saving a name failed
// with "unknown player" when the device had not registered yet — e.g. its
// first sync did not get through). Registration gives the next "Player NNN".
// Two first requests at once (two tabs) race on the insert; the loser's
// duplicate-key error is caught and it reads the row the winner made.
// Returns the row {seq, sync_token, display_name}, or json_error 403 when
// the token does not match.
function measurement_player(PDO $pdo, $playerId, $syncToken) {
    $sel = $pdo->prepare("SELECT seq, sync_token, display_name FROM m_players WHERE id = ?");
    $sel->execute(array($playerId));
    $player = $sel->fetch();
    if ($player === false) {
        $now = gmdate("Y-m-d H:i:s");
        try {
            $pdo->prepare("INSERT INTO m_players (id, sync_token, display_name, created_at, updated_at) VALUES (?, ?, '', ?, ?)")
                ->execute(array($playerId, $syncToken, $now, $now));
        } catch (Exception $e) { /* another request registered them first */ }
        $sel->execute(array($playerId));
        $player = $sel->fetch();
        if ($player === false) json_error("could not register player", 500);
        if ((string) $player["display_name"] === "") {
            $name = measurement_player_name($player["seq"]);
            $pdo->prepare("UPDATE m_players SET display_name = ? WHERE id = ? AND display_name = ''")->execute(array($name, $playerId));
            $player["display_name"] = $name;
        }
    }
    if (!hash_equals((string) $player["sync_token"], $syncToken)) json_error("token mismatch", 403);
    return $player;
}
