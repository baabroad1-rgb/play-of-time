<?php
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "POST") json_error("POST only", 405);

$input = json_input();
$playerId = isset($input["playerId"]) ? trim((string) $input["playerId"]) : "";
$syncToken = isset($input["syncToken"]) ? trim((string) $input["syncToken"]) : "";

if (!looks_like_id($playerId) || !looks_like_token($syncToken)) {
    json_error("invalid playerId/syncToken");
}

$pdo = pot_db();

$stmt = $pdo->prepare("SELECT sync_token, seq, created_at FROM players WHERE id = ?");
$stmt->execute(array($playerId));
$existing = $stmt->fetch();
if ($existing === false) json_error("unknown player — sync at least once before setting a name", 404);
if (!hash_equals((string) $existing["sync_token"], $syncToken)) json_error("token mismatch", 403);

$name = sanitize_name(isset($input["name"]) ? $input["name"] : "");
// Clearing the field reverts to a short generated name rather than leaving
// it blank ("Anonymous").
if ($name === null) $name = generate_short_name($pdo);

$upd = $pdo->prepare("UPDATE players SET display_name = ?, updated_at = ? WHERE id = ?");
$upd->execute(array($name, gmdate("Y-m-d H:i:s"), $playerId));

json_out(array("ok" => true, "displayName" => $name));
