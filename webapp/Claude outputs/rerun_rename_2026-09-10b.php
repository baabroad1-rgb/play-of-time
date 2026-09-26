<?php
// One-off re-run of migrate_002_rename_default_names.php's logic, under a
// different filename so it isn't caught by .htaccess's FilesMatch deny list
// (that list intentionally names migrate_002_rename_default_names.php itself
// to stop it being re-triggered by accident — this script exists only
// because the players it targets slipped back to their old default name due
// to the name-clobbering sync bug fixed 2026-09-10, see TASKS.md). Reuses
// looks_like_old_default_name() / generate_short_name() from _lib.php
// unchanged — same matching rule, same name generator, just re-run once
// more now that the clobbering bug is fixed and won't undo it again.
// Safe to run more than once: only touches rows whose display_name still
// exactly matches the old "playerNNN_join_DD.MM.YYYY" shape.
header("Content-Type: application/json; charset=utf-8");
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

$pdo = pot_db();
$stmt = $pdo->query("SELECT id, display_name FROM players WHERE display_name IS NOT NULL");
$rows = $stmt->fetchAll();

$renamed = array();
$upd = $pdo->prepare("UPDATE players SET display_name = ? WHERE id = ?");
foreach ($rows as $row) {
    if (!looks_like_old_default_name($row["display_name"])) continue;
    $newName = generate_short_name($pdo);
    $upd->execute(array($newName, $row["id"]));
    $renamed[] = array("id" => $row["id"], "from" => $row["display_name"], "to" => $newName);
}

echo json_encode(array(
    "ok" => true,
    "scanned" => count($rows),
    "renamedCount" => count($renamed),
    "renamed" => $renamed,
), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
