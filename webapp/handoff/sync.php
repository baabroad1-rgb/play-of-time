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

try {
    $pdo->beginTransaction();

    $stmt = $pdo->prepare("SELECT sync_token, seq, display_name, created_at FROM players WHERE id = ?");
    $stmt->execute(array($playerId));
    $existing = $stmt->fetch();

    $now = gmdate("Y-m-d H:i:s");
    $levelId = isset($input["levelId"]) ? (int) $input["levelId"] : 1;
    $taskId = isset($input["taskId"]) ? (int) $input["taskId"] : 1;
    $name = isset($input["name"]) ? sanitize_name($input["name"]) : null;
    $resolvedName = $name;

    if ($existing === false) {
        // First time this device has ever synced — register it.
        $ins = $pdo->prepare(
            "INSERT INTO players (id, sync_token, display_name, level_id, task_id, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)"
        );
        $ins->execute(array($playerId, $syncToken, $name, $levelId, $taskId, $now, $now));
        if ($name === null) {
            // No custom name yet — assign playerNNN_join_DD.MM.YYYY instead
            // of leaving it blank ("Anonymous").
            $seq = (int) $pdo->lastInsertId();
            $resolvedName = default_player_name($seq, $now);
            $nameFix = $pdo->prepare("UPDATE players SET display_name = ? WHERE id = ?");
            $nameFix->execute(array($resolvedName, $playerId));
        }
    } else {
        if (!hash_equals((string) $existing["sync_token"], $syncToken)) {
            $pdo->rollBack();
            json_error("token mismatch", 403);
        }
        if ($name === null && $existing["display_name"] === null) {
            // Backfill: this row predates default names, or was cleared
            // before "clear" started reverting to one — same result either way.
            $name = default_player_name((int) $existing["seq"], (string) $existing["created_at"]);
        }
        $resolvedName = $name !== null ? $name : (string) $existing["display_name"];
        if ($name !== null) {
            $upd = $pdo->prepare("UPDATE players SET level_id = ?, task_id = ?, display_name = ?, updated_at = ? WHERE id = ?");
            $upd->execute(array($levelId, $taskId, $name, $now, $playerId));
        } else {
            $upd = $pdo->prepare("UPDATE players SET level_id = ?, task_id = ?, updated_at = ? WHERE id = ?");
            $upd->execute(array($levelId, $taskId, $now, $playerId));
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
    $localIds = array();
    foreach ($attempts as $a) {
        if (is_array($a) && isset($a["localId"])) $localIds[] = (int) $a["localId"];
    }
    $existingSc = array(); // local_id => currently stored sc
    if ($localIds) {
        $placeholders = implode(",", array_fill(0, count($localIds), "?"));
        $chk = $pdo->prepare("SELECT local_id, sc FROM attempts WHERE player_id = ? AND local_id IN ($placeholders)");
        $chk->execute(array_merge(array($playerId), $localIds));
        foreach ($chk->fetchAll() as $row) $existingSc[(int) $row["local_id"]] = (float) $row["sc"];
    }

    $insAtt = $pdo->prepare(
        "INSERT INTO attempts
            (player_id, local_id, session_id, level_id, task_id, accuracy, duration_ms, error_ms, is_win, tolerance_ms, sc, recorded_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    );
    $updAtt = $pdo->prepare("UPDATE attempts SET sc = ? WHERE player_id = ? AND local_id = ?");

    $inserted = 0;
    $updated = 0;
    foreach ($attempts as $a) {
        if (!is_array($a) || !isset($a["localId"])) continue;
        $lid = (int) $a["localId"];
        $sc = (float) (isset($a["sc"]) ? $a["sc"] : 0);

        if (array_key_exists($lid, $existingSc)) {
            // Seen before — only write if the SC value actually moved, so a
            // plain retry (identical batch, e.g. a beacon resend) doesn't
            // burn a write for nothing.
            if (abs($existingSc[$lid] - $sc) > 1e-9) {
                $updAtt->execute(array($sc, $playerId, $lid));
                $updated++;
            }
            continue;
        }

        $recordedAtMs = isset($a["recordedAt"]) ? (float) $a["recordedAt"] : 0;
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
            gmdate("Y-m-d H:i:s", (int) round($recordedAtMs / 1000))
        ));
        $inserted++;
    }

    // Recompute rollups from the attempts table itself, so they can never
    // drift even if a batch only partially applied.
    $roll = $pdo->prepare("SELECT COUNT(*) AS cnt, COALESCE(SUM(sc), 0) AS sc_sum, COALESCE(SUM(is_win), 0) AS win_sum FROM attempts WHERE player_id = ?");
    $roll->execute(array($playerId));
    $r = $roll->fetch();
    $updRoll = $pdo->prepare("UPDATE players SET attempts_total = ?, sc_total = ?, wins_total = ? WHERE id = ?");
    $updRoll->execute(array($r["cnt"], $r["sc_sum"], $r["win_sum"], $playerId));

    $pdo->commit();
    json_out(array("ok" => true, "inserted" => $inserted, "updated" => $updated, "displayName" => $resolvedName));
} catch (Exception $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    json_error("sync failed", 500);
}
