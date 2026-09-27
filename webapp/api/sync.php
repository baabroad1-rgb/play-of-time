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

// Self-migration (2026-09-25): exact start time in ms. Nobody has DB-console
// access, so the column is added by the first sync after this file lands.
// Runs before the transaction — ALTER commits implicitly in MySQL. A parallel
// request that loses the race gets "duplicate column" — ignored.
try {
    $pdo->query("SELECT recorded_ms FROM attempts LIMIT 0");
} catch (Exception $e) {
    try { $pdo->exec("ALTER TABLE attempts ADD COLUMN recorded_ms BIGINT NULL"); } catch (Exception $ignored) {}
}

// Self-migration (2026-09-27): which app build the player is actually running.
// Added because nothing in a sync said WHICH app it came from, and during the
// move to the renamed package (online.playoftime) that was the one thing we
// needed to know: who is still on the old app and therefore still needs the
// bridge version kept alive. NULL means a build older than 15.02 / 14.93 —
// those don't send it. Same pattern as recorded_ms above.
try {
    $pdo->query("SELECT app_version FROM players LIMIT 0");
} catch (Exception $e) {
    try { $pdo->exec("ALTER TABLE players ADD COLUMN app_version VARCHAR(16) NULL"); } catch (Exception $ignored) {}
}

try {
    $pdo->beginTransaction();

    $stmt = $pdo->prepare("SELECT sync_token, seq, display_name, created_at, current_epoch FROM players WHERE id = ?");
    $stmt->execute(array($playerId));
    $existing = $stmt->fetch();

    $now = gmdate("Y-m-d H:i:s");
    $levelId = isset($input["levelId"]) ? (int) $input["levelId"] : 1;
    $taskId = isset($input["taskId"]) ? (int) $input["taskId"] : 1;
    $name = isset($input["name"]) ? sanitize_name($input["name"]) : null;
    $resolvedName = $name;
    $currentEpoch = 0; // matches players.current_epoch's own DEFAULT for a brand-new row
    // Set once at first sync and never touched again (Alex, 2026-09-04): only
    // the webapp sends platform: "web" explicitly; anything else (including
    // nothing, i.e. the native app) defaults to the column's own DEFAULT
    // 'native' (see migrate_002_add_platform).
    $platform = (isset($input["platform"]) && $input["platform"] === "web") ? "web" : "native";
    // Версия приложения. В отличие от platform, пишется при КАЖДОЙ
    // синхронизации: она меняется, когда игрок обновляется, и нам нужно
    // текущее значение, а не первое. Формат жёстко ограничен — это строка из
    // клиента, и в базу она попадает как есть.
    $appVersion = null;
    if (isset($input["appVersion"]) && is_string($input["appVersion"])
        && preg_match('/^[0-9]{1,3}\.[0-9]{1,3}$/', $input["appVersion"])) {
        $appVersion = $input["appVersion"];
    }

    if ($existing === false) {
        // First time this device has ever synced — register it.
        $ins = $pdo->prepare(
            "INSERT INTO players (id, sync_token, display_name, level_id, task_id, platform, app_version, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );
        $ins->execute(array($playerId, $syncToken, $name, $levelId, $taskId, $platform, $appVersion, $now, $now));
        if ($name === null) {
            // No custom name yet — assign a short generated name instead of
            // leaving it blank ("Anonymous").
            $resolvedName = generate_short_name($pdo);
            $nameFix = $pdo->prepare("UPDATE players SET display_name = ? WHERE id = ?");
            $nameFix->execute(array($resolvedName, $playerId));
        }
    } else {
        if (!hash_equals((string) $existing["sync_token"], $syncToken)) {
            $pdo->rollBack();
            json_error("token mismatch", 403);
        }
        $currentEpoch = (int) $existing["current_epoch"];
        if ($name === null && $existing["display_name"] === null) {
            // Backfill: this row predates default names, or was cleared
            // before "clear" started reverting to one — same result either way.
            $name = generate_short_name($pdo);
        }
        $resolvedName = $name !== null ? $name : (string) $existing["display_name"];
        // COALESCE(?, app_version): клиент, который версию не присылает
        // (сборки старее 15.02 / 14.93), не должен затирать уже известную —
        // иначе игрок, синхронизировавшийся один раз со старого устройства,
        // терял бы отметку.
        if ($name !== null) {
            $upd = $pdo->prepare("UPDATE players SET level_id = ?, task_id = ?, display_name = ?, app_version = COALESCE(?, app_version), updated_at = ? WHERE id = ?");
            $upd->execute(array($levelId, $taskId, $name, $appVersion, $now, $playerId));
        } else {
            $upd = $pdo->prepare("UPDATE players SET level_id = ?, task_id = ?, app_version = COALESCE(?, app_version), updated_at = ? WHERE id = ?");
            $upd->execute(array($levelId, $taskId, $appVersion, $now, $playerId));
        }
    }

    $attempts = isset($input["attempts"]) && is_array($input["attempts"]) ? array_slice($input["attempts"], 0, 500) : array();

    // Attempts the client already sent before are UPDATED, not skipped —
    // 2026-09-03 fix (see TASKS.md "Ranks metrics checked" entry). The
    // client's recalcScm() retroactively finalizes an attempt's `sc` once it
    // gets a real next-neighbour, one sync cycle after that attempt was
    // first sent; the client now deliberately resends the previously-synced
    // attempt alongside anything new so that correction reaches the server.
    // Every other column is an immutable fact about the attempt (recorded
    // once, never recomputed client-side) — only `sc` is ever allowed to
    // change on a resend. Plain SELECT-then-INSERT/UPDATE on purpose, not a
    // native upsert: this file is exercised against both MySQL (production)
    // and SQLite (POT_TEST_DSN) and the two engines don't share upsert syntax.
    //
    // `local_id` alone stays the dedupe key (NOT (local_id, epoch)) —
    // 2026-09-03 "Erase preserves id counters" fix means a player's local_id
    // never repeats across their own erases/epochs either, so this is still
    // safe and simpler than adding epoch to the key.
    $localIds = array();
    foreach ($attempts as $a) {
        if (is_array($a) && isset($a["localId"])) $localIds[] = (int) $a["localId"];
    }
    $existingSc = array(); // local_id => currently stored sc
    $existingMsMissing = array(); // local_id => true if recorded_ms still NULL
    if ($localIds) {
        $placeholders = implode(",", array_fill(0, count($localIds), "?"));
        $chk = $pdo->prepare("SELECT local_id, sc, recorded_ms FROM attempts WHERE player_id = ? AND local_id IN ($placeholders)");
        $chk->execute(array_merge(array($playerId), $localIds));
        foreach ($chk->fetchAll() as $row) {
            $existingSc[(int) $row["local_id"]] = (float) $row["sc"];
            if ($row["recorded_ms"] === null) $existingMsMissing[(int) $row["local_id"]] = true;
        }
    }

    $insAtt = $pdo->prepare(
        "INSERT INTO attempts
            (player_id, local_id, session_id, level_id, task_id, accuracy, duration_ms, error_ms, is_win, tolerance_ms, sc, epoch, recorded_at, recorded_ms)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    );
    $updAtt = $pdo->prepare("UPDATE attempts SET sc = ? WHERE player_id = ? AND local_id = ?");
    // Old rows (before migrate_003) get their exact ms time filled in when the
    // client resends them — only ever from NULL, never overwritten.
    $updMs = $pdo->prepare("UPDATE attempts SET recorded_ms = ? WHERE player_id = ? AND local_id = ? AND recorded_ms IS NULL");

    $inserted = 0;
    $updated = 0;
    foreach ($attempts as $a) {
        if (!is_array($a) || !isset($a["localId"])) continue;
        $lid = (int) $a["localId"];
        $sc = (float) (isset($a["sc"]) ? $a["sc"] : 0);
        $recordedMs = isset($a["recordedAt"]) ? (int) round((float) $a["recordedAt"]) : 0;

        if (array_key_exists($lid, $existingSc)) {
            if (isset($existingMsMissing[$lid]) && $recordedMs > 0) {
                $updMs->execute(array($recordedMs, $playerId, $lid));
                $updated++;
            }
            // Seen before — only write if the SC value actually moved, so a
            // plain retry (identical batch, e.g. a beacon resend) doesn't
            // burn a write for nothing. A resend never moves this row to
            // today's epoch — it was inserted under whatever epoch was
            // current back then, and only `sc` is ever allowed to change.
            if (abs($existingSc[$lid] - $sc) > 1e-9) {
                $updAtt->execute(array($sc, $playerId, $lid));
                $updated++;
            }
            continue;
        }

        $recordedAtMs = (float) $recordedMs;

        $insAtt->execute(array(
            $playerId,
            $lid,
            (int) (isset($a["sessionId"]) ? $a["sessionId"] : 0),
            (int) (isset($a["levelId"]) ? $a["levelId"] : $levelId),
            (int) (isset($a["taskId"]) ? $a["taskId"] : $taskId),
            (int) (isset($a["accuracy"]) ? $a["accuracy"] : 0),
            (int) (isset($a["durationMs"]) ? $a["durationMs"] : 0),
            (int) (isset($a["errorMs"]) ? $a["errorMs"] : 0),
            empty($a["isWin"]) ? 0 : 1,
            (int) (isset($a["toleranceMs"]) ? $a["toleranceMs"] : 0),
            $sc,
            $currentEpoch,
            gmdate("Y-m-d H:i:s", (int) round($recordedAtMs / 1000)),
            $recordedMs > 0 ? $recordedMs : null
        ));
        $inserted++;
    }

    // Recompute rollups from the attempts table itself, so they can never
    // drift even if a batch only partially applied. Scoped to the player's
    // CURRENT epoch only (2026-09-03, see TASKS.md "десятая правка") — older
    // epochs' attempts are kept forever but no longer count toward the
    // visible Ranks/Coins total, matching what local Analytics "All Time"
    // can possibly show after an Erase/Reset (it lost that history too).
    $roll = $pdo->prepare("SELECT COUNT(*) AS cnt, COALESCE(SUM(sc), 0) AS sc_sum, COALESCE(SUM(is_win), 0) AS win_sum FROM attempts WHERE player_id = ? AND epoch = ?");
    $roll->execute(array($playerId, $currentEpoch));
    $r = $roll->fetch();
    $updRoll = $pdo->prepare("UPDATE players SET attempts_total = ?, sc_total = ?, wins_total = ? WHERE id = ?");
    $updRoll->execute(array($r["cnt"], $r["sc_sum"], $r["win_sum"], $playerId));

    $pdo->commit();
    json_out(array("ok" => true, "inserted" => $inserted, "updated" => $updated, "displayName" => $resolvedName));
} catch (Exception $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    json_error("sync failed", 500);
}
