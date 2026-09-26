<?php
// One-off migration (Alex 2026-09-11): the old "playerNNN_join_DD.MM.YYYY"
// default name is being replaced by a short syllable-based one
// (generate_short_name() in _lib.php). New assignments already use it
// (sync.php / set_name.php) — this script re-names EXISTING players who are
// still on the old default. Players who set a custom name are left alone:
// a row is only touched if its display_name matches the old default's exact
// shape (looks_like_old_default_name()), never just "any name that looks odd".
//
// Run ONCE, from the command line on the server (not through the browser —
// see .htaccess, this file is denied direct HTTP access):
//   php migrate_002_rename_default_names.php
//
// Safe to re-run: anything already renamed no longer matches the old
// pattern, so a second run just finds zero rows and does nothing.

require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if (php_sapi_name() !== "cli") {
    http_response_code(403);
    exit("CLI only.\n");
}

$pdo = pot_db();

$rows = $pdo->query("SELECT id, display_name FROM players WHERE display_name IS NOT NULL")->fetchAll();

$upd = $pdo->prepare("UPDATE players SET display_name = ?, updated_at = ? WHERE id = ?");
$now = gmdate("Y-m-d H:i:s");

$migrated = 0;
$skipped = 0;

foreach ($rows as $row) {
    if (!looks_like_old_default_name($row["display_name"])) {
        $skipped++;
        continue;
    }
    $newName = generate_short_name($pdo);
    $upd->execute(array($newName, $now, $row["id"]));
    echo $row["id"] . "  " . $row["display_name"] . "  ->  " . $newName . "\n";
    $migrated++;
}

echo "\nDone. Migrated {$migrated} row(s), left {$skipped} custom-named row(s) untouched.\n";
