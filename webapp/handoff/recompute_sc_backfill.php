<?php
// One-off backfill: recompute attempts.sc for every existing row using the
// EXACT client formula (app.js calculateScm/recalcScm), then recompute each
// player's sc_total from the corrected sums.
//
// Why this is needed: before the 2026-09-03 sync.php fix, every attempt was
// synced with a transient "edge-case" sc value (computed as if it had no
// next-neighbour, because at sync time it usually didn't yet) that the
// client later silently finalized to a different, generally lower, value
// once the next attempt existed — but that correction was never re-sent.
// Every attempts.sc row already stored on the server predates the fix and
// carries that stale edge-case value. This script recomputes it properly,
// server-side, from each player's own stored attempt sequence (ordered by
// local_id, which is each player's true chronological attempt order).
//
// Usage (CLI, if whoever runs this has shell access):
//   php recompute_sc_backfill.php            (dry run — reports deltas, writes nothing)
//   php recompute_sc_backfill.php --apply     (writes the corrected values)
//
// Usage (HTTP, if nobody applying this has CLI/SSH — e.g. Alex's Cowork
// session, which only reaches this server as a normal web client):
//   GET /api/recompute_sc_backfill.php?token=zYmaCmVolKRluEy91qS10IZPKPYvmI3n
//     (dry run)
//   GET /api/recompute_sc_backfill.php?token=zYmaCmVolKRluEy91qS10IZPKPYvmI3n&apply=1
//     (writes the corrected values)
//   The token is a one-time value hardcoded below, only so this can't be
//   triggered by a stray crawler/bot while it sits in a public folder for
//   the few minutes it takes to run this once. DELETE THIS FILE after the
//   apply run succeeds — don't leave a write-capable endpoint parked in
//   api/ longer than it takes to use it once.
//
// Safe to re-run either way: recomputing from the current attempts table is
// idempotent (running it twice in a row with --apply produces zero further
// deltas the second time).

$isCli = PHP_SAPI === "cli";
if (!$isCli) {
    header("Content-Type: text/plain; charset=utf-8");
    $token = isset($_GET["token"]) ? (string) $_GET["token"] : "";
    if (!hash_equals("zYmaCmVolKRluEy91qS10IZPKPYvmI3n", $token)) {
        http_response_code(403);
        echo "forbidden\n";
        exit;
    }
}

require __DIR__ . "/db.php";

const AIM_TIME = 3000;

function calc_scm($prevDurMs, $currDurMs, $nextDurMs, $accuracy) {
    $tPrev = abs($prevDurMs - AIM_TIME);
    $tCurr = abs($currDurMs - AIM_TIME);
    $tNext = abs($nextDurMs - AIM_TIME);
    $denominator = 3 * $tCurr + $tPrev + $tNext
        + abs($currDurMs - $nextDurMs)
        + abs($currDurMs - $prevDurMs)
        - abs($nextDurMs - $prevDurMs) / 3
        + 1;
    $levelFactor = pow(10, $accuracy - 3);
    return 99 / $denominator * $levelFactor;
}

$apply = $isCli ? in_array("--apply", $argv, true) : isset($_GET["apply"]);

$pdo = pot_db();

$players = $pdo->query("SELECT id, sc_total AS old_sc_total FROM players")->fetchAll();

$updAttempt = $pdo->prepare("UPDATE attempts SET sc = ? WHERE id = ?");
$updPlayer = $pdo->prepare("UPDATE players SET sc_total = ? WHERE id = ?");

$totalRowsChanged = 0;
$totalPlayersChanged = 0;

foreach ($players as $player) {
    $playerId = $player["id"];
    $stmt = $pdo->prepare(
        "SELECT id, local_id, duration_ms, accuracy, sc FROM attempts WHERE player_id = ? ORDER BY local_id ASC"
    );
    $stmt->execute(array($playerId));
    $attempts = $stmt->fetchAll();
    if (!$attempts) continue;

    $n = count($attempts);
    $newScTotal = 0.0;
    $playerRowsChanged = 0;

    for ($i = 0; $i < $n; $i++) {
        $prev = $i > 0 ? $attempts[$i - 1] : $attempts[$i];
        $curr = $attempts[$i];
        $next = $i < $n - 1 ? $attempts[$i + 1] : $attempts[$i];

        $newSc = calc_scm(
            (float) $prev["duration_ms"],
            (float) $curr["duration_ms"],
            (float) $next["duration_ms"],
            (int) $curr["accuracy"]
        );
        $oldSc = (float) $curr["sc"];
        $newScTotal += $newSc;

        if (abs($newSc - $oldSc) > 1e-9) {
            $playerRowsChanged++;
            if ($apply) {
                $updAttempt->execute(array($newSc, $curr["id"]));
            }
        }
    }

    if ($playerRowsChanged > 0) {
        $totalPlayersChanged++;
        $totalRowsChanged += $playerRowsChanged;
        $oldTotal = (float) $player["old_sc_total"];
        printf(
            "player %s: %d/%d attempts changed, sc_total %.6f -> %.6f (%s%.6f)\n",
            $playerId, $playerRowsChanged, $n, $oldTotal, $newScTotal, $newScTotal >= $oldTotal ? "+" : "", $newScTotal - $oldTotal
        );
        if ($apply) {
            $updPlayer->execute(array($newScTotal, $playerId));
        }
    }
}

echo "---\n";
echo ($apply ? "APPLIED" : "DRY RUN") . ": $totalPlayersChanged player(s), $totalRowsChanged attempt row(s) " .
    ($apply ? "had" : "would have") . " their sc corrected.\n";
if (!$apply) {
    echo "Re-run with --apply to write these changes.\n";
}
