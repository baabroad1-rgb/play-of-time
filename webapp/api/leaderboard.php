<?php
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "GET") json_error("GET only", 405);

$allowed = array("level", "sc", "hitrate", "combined");
$sort = isset($_GET["sort"]) && in_array($_GET["sort"], $allowed, true) ? $_GET["sort"] : "level";

$pdo = pot_db();

// Base per-player rollups — raw synced values, no formula divergence with
// the native app to worry about (sc_total/attempts_total/wins_total are the
// exact same rollup sync.php already maintains and the app's own top bar
// reads).
$playerStmt = $pdo->query(
    "SELECT id AS playerId, display_name AS displayName, level_id AS levelId, task_id AS taskId,
            sc_total AS scTotal, attempts_total AS attemptsTotal, wins_total AS winsTotal
     FROM players
     WHERE attempts_total > 0"
);
$players = array();
foreach ($playerStmt->fetchAll() as $row) {
    $players[$row["playerId"]] = array(
        "playerId" => $row["playerId"],
        "displayName" => $row["displayName"],
        "levelId" => (int) $row["levelId"],
        "taskId" => (int) $row["taskId"],
        "scTotal" => (float) $row["scTotal"],
        "attemptsTotal" => (int) $row["attemptsTotal"],
        "winsTotal" => (int) $row["winsTotal"],
    );
}

// Lucky Index and Hit Rate (Alex 2026-09-11: "лаки индекс в лидборде не
// совпадает со значением в аналитикс") both need raw per-attempt data to
// match the native Analytics screen's own formulas (AnalyticsUseCase.kt) —
// aggregates alone can't reproduce a percentile-trimmed range or a
// per-attempt rounding-grid check. Scale here is small (a few thousand rows
// across ~30 players), so pulling every row and reducing in PHP is simpler
// and more honest than approximating in SQL (this project's schema is
// MySQL 5.7-compatible on purpose — no window functions to lean on anyway).
$attStmt = $pdo->prepare(
    "SELECT a.player_id AS playerId, a.error_ms AS errorMs, a.accuracy AS accuracy, a.tolerance_ms AS toleranceMs,
            a.recorded_at AS recordedAt, a.sc AS sc, a.is_win AS isWin
     FROM attempts a
     JOIN players p ON p.id = a.player_id AND a.epoch = p.current_epoch
     WHERE p.attempts_total > 0"
);
$attStmt->execute();

$errorsByPlayer = array();    // playerId => [error_ms, ...], for the P3/P97 range
$luckyItems = array();        // playerId => [[accuracy, error_ms, tolerance_ms, isGreen], ...] (Lucky Index)
$tolerantTrueThree = array(); // playerId => count passing is_true_three_win() (Hit Rate numerator)
$firstAt = array();           // playerId => earliest recorded_at (Alex 2026-09-19: "как давно начал")
$tiers = array();             // playerId => accuracyTier(1..3) => per-level accumulators (same-level comparison)

foreach ($attStmt->fetchAll() as $a) {
    $pid = $a["playerId"];
    if (!isset($players[$pid])) continue;
    $err = (int) $a["errorMs"];
    $errorsByPlayer[$pid][] = $err;
    $isTolerantWin = is_true_three_win((int) $a["accuracy"], $err, (int) $a["toleranceMs"]);
    $luckyItems[$pid][] = array((int) $a["accuracy"], $err, (int) $a["toleranceMs"], $isTolerantWin);
    if ($isTolerantWin) {
        $tolerantTrueThree[$pid] = (isset($tolerantTrueThree[$pid]) ? $tolerantTrueThree[$pid] : 0) + 1;
    }

    $rec = $a["recordedAt"];
    if (!isset($firstAt[$pid]) || $rec < $firstAt[$pid]) $firstAt[$pid] = $rec;

    // Per-level (accuracy tier) accumulators — accuracy 1/2/3 == game level 1/2/3.
    $tk = (int) $a["accuracy"];
    if (!isset($tiers[$pid][$tk])) {
        $tiers[$pid][$tk] = array("errors" => array(), "items" => array(), "tol" => 0, "wins" => 0, "attempts" => 0, "sc" => 0.0, "first" => $rec);
    }
    $t =& $tiers[$pid][$tk];
    $t["errors"][] = $err;
    $t["items"][] = array($tk, $err, (int) $a["toleranceMs"], $isTolerantWin);
    $t["attempts"]++;
    if ($isTolerantWin) $t["tol"]++;
    if ((int) $a["isWin"] === 1) $t["wins"]++;
    $t["sc"] += (float) $a["sc"];
    if ($rec < $t["first"]) $t["first"] = $rec;
    unset($t);
}

// Shared Hit Rate / Lucky Index math (same formulas the overall numbers use,
// so a per-level number is directly comparable to the overall one).
function lb_hit_and_lucky($items, $attempts, $tolerantWins) {
    return array(
        $attempts > 0 ? round($tolerantWins / $attempts * 100, 1) : 0.0,
        lucky_index($items)
    );
}

function lb_iso($mysqlUtc) {
    return $mysqlUtc ? str_replace(" ", "T", $mysqlUtc) . "Z" : null;
}

$rows = array();
foreach ($players as $pid => $p) {
    $errors = isset($errorsByPlayer[$pid]) ? $errorsByPlayer[$pid] : array();
    sort($errors);
    $n = count($errors);

    // Range of Errors: 3rd/97th percentile on SIGNED errors — same index
    // math as AnalyticsUseCase.kt's rangeEarlyMs/rangeLateMs
    // ((sorted.size * 0.03/0.97).toInt().coerceIn(0, size-1)); PHP's (int)
    // cast truncates toward zero the same way Kotlin's .toInt() does.
    $rangeEarlyMs = 0;
    $rangeLateMs = 0;
    if ($n > 0) {
        $loIdx = min($n - 1, max(0, (int) ($n * 0.03)));
        $hiIdx = min($n - 1, max(0, (int) ($n * 0.97)));
        $rangeEarlyMs = $errors[$loIdx];
        $rangeLateMs = $errors[$hiIdx];
    }

    $attemptsTotal = $p["attemptsTotal"];
    $tolerantWins = isset($tolerantTrueThree[$pid]) ? $tolerantTrueThree[$pid] : 0;

    // Hit Rate — matches Analytics' "CHANCE OF WIN" card's Hit rate row
    // (trueThreeHitRatePct), NOT the raw is_win/winsTotal column — is_win
    // includes every tolerance-eased subquest win across a player's whole
    // history, which reads as a much more lenient number than what the app
    // itself shows as "Hit rate".
    $p["hitRatePct"] = $attemptsTotal > 0 ? round($tolerantWins / $attemptsTotal * 100, 1) : 0.0;

    // Quest progress is the primary axis, Serenity Coins break ties within
    // the same quest (capped so the tiebreak never spills into the next
    // quest's digit range).
    $p["combinedScore"] = $p["levelId"] * 1000 + $p["taskId"] * 100 + min(99, $p["scTotal"]);

    // Lucky Index: green attempts against their chance by the spread of each
    // level (Alex 2026-10-24, lucky_index() in _lib.php).
    $p["luckyIndex"] = lucky_index(isset($luckyItems[$pid]) ? $luckyItems[$pid] : array());

    $p["firstAttemptAt"] = isset($firstAt[$pid]) ? lb_iso($firstAt[$pid]) : null;

    $tierOut = array();
    if (isset($tiers[$pid])) {
        foreach ($tiers[$pid] as $tk => $t) {
            $hl = lb_hit_and_lucky($t["items"], $t["attempts"], $t["tol"]);
            $tierOut[(string) $tk] = array(
                "attempts" => $t["attempts"],
                "wins" => $t["wins"],
                "scTotal" => round($t["sc"], 2),
                "hitRatePct" => $hl[0],
                "luckyIndex" => $hl[1],
                "firstAttemptAt" => lb_iso($t["first"]),
            );
        }
    }
    $p["tiers"] = (object) $tierOut; // {} not [] when empty

    $rows[] = $p;
}

usort($rows, function ($a, $b) use ($sort) {
    if ($sort === "sc") return $b["scTotal"] <=> $a["scTotal"];
    if ($sort === "hitrate") return $b["hitRatePct"] <=> $a["hitRatePct"];
    if ($sort === "combined") return $b["combinedScore"] <=> $a["combinedScore"];
    if ($a["levelId"] !== $b["levelId"]) return $b["levelId"] <=> $a["levelId"];
    if ($a["taskId"] !== $b["taskId"]) return $b["taskId"] <=> $a["taskId"];
    return $b["scTotal"] <=> $a["scTotal"];
});

json_out(array("sort" => $sort, "players" => array_slice($rows, 0, 1000)));
