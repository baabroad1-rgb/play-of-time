<?php
// POST {playerId, syncToken, name} → {displayName}
//
// Lets a player save themselves under a name of their own (Alex
// 2026-10-14). Same rules as the game's set_name.php: only the device that
// holds the player's token can rename them; an empty name gives back the
// player's own "Player NNN".
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "POST") json_error("POST only", 405);

$input = json_input();
$playerId = isset($input["playerId"]) ? trim((string) $input["playerId"]) : "";
$syncToken = isset($input["syncToken"]) ? trim((string) $input["syncToken"]) : "";
if (!looks_like_id($playerId) || !looks_like_token($syncToken)) json_error("invalid playerId/syncToken");

$pdo = m_db();
$sel = $pdo->prepare("SELECT seq, sync_token FROM m_players WHERE id = ?");
$sel->execute(array($playerId));
$player = $sel->fetch();
if ($player === false) json_error("unknown player", 404);
if (!hash_equals((string) $player["sync_token"], $syncToken)) json_error("token mismatch", 403);

$name = measurement_clean_name(isset($input["name"]) ? $input["name"] : "");
if ($name === null) $name = measurement_player_name($player["seq"]);

$pdo->prepare("UPDATE m_players SET display_name = ?, updated_at = ? WHERE id = ?")
    ->execute(array($name, gmdate("Y-m-d H:i:s"), $playerId));

json_out(array("displayName" => $name));
