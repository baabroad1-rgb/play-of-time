<?php
// ONE-OFF RESEARCH SCRIPT (Alex + Claude, 2026-09-25, v2) — not a feature,
// not linked from the app. Delete once the questions below are answered.
//
// Only the ROW quests: task 4 = "two in a row", task 5 = "three in a row",
// on levels 1..3 (L1.4 L1.5 L2.4 L2.5 L3.4 L3.5).
//
// Q1 ("с какой попытки проходят"): for each player and each ROW quest —
//     the attempt number, counted from that player's FIRST attempt on the
//     quest, at which they first land the required hits in a row.
// Q2 ("как реагируют на вторую подряд"): the attempt right after N hits in a
//     row (N = 0, 1, 2) — hit rate, error size, early/late lean, pause.
//     N = 2 exists only in the "three in a row" quests: that is the attempt
//     after the second consecutive hit, the one Alex asks about.
//
// A "hit" is recomputed here exactly like the game does (duration rounded to
// the level's grid, within the tolerance active at that attempt) — the
// is_win column is NOT used: native and web fill it differently.
// "Clean" = same but with zero tolerance (a real 3.0 / 3.00 / 3.000).
// A streak never carries over a session boundary (the game's window resets).
//
// Output is aggregate only: no player ids, no raw rows.

require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "GET") json_error("GET only", 405);

$pdo = pot_db();
// Same self-migration as sync.php, so this script works even before the first sync.
try {
    $pdo->query("SELECT recorded_ms FROM attempts LIMIT 0");
} catch (Exception $e) {
    try { $pdo->exec("ALTER TABLE attempts ADD COLUMN recorded_ms BIGINT NULL"); } catch (Exception $ignored) {}
}
$stmt = $pdo->query(
    "SELECT player_id, session_id, local_id, level_id, task_id, accuracy,
            duration_ms, error_ms, tolerance_ms, recorded_at, recorded_ms
     FROM attempts
     WHERE task_id IN (4, 5) AND level_id BETWEEN 1 AND 3
     ORDER BY player_id, level_id, task_id, local_id
     LIMIT 500000"
);
$rows = $stmt->fetchAll();

function median_of($v) {
    $n = count($v);
    if ($n === 0) return null;
    sort($v);
    $m = intdiv($n, 2);
    return ($n % 2) ? $v[$m] : ($v[$m - 1] + $v[$m]) / 2.0;
}

$completion = array(); // quest => ["started"=>, "done"=>[attemptNo...], "doneInSession"=>[...], "cleanDone"=>[...], "notDoneAttempts"=>[...]]
$reaction = array();   // quest => streakBefore => list of points

$flush = function ($quest, $state) use (&$completion) {
    if ($quest === null) return;
    if (!isset($completion[$quest])) {
        $completion[$quest] = array("started" => 0, "done" => array(), "doneInSession" => array(), "cleanDone" => array(), "notDoneAttempts" => array());
    }
    $completion[$quest]["started"]++;
    if ($state["doneAt"] !== null) {
        $completion[$quest]["done"][] = $state["doneAt"];
        $completion[$quest]["doneInSession"][] = $state["doneInSession"];
    } else {
        $completion[$quest]["notDoneAttempts"][] = $state["attemptNo"];
    }
    if ($state["cleanDoneAt"] !== null) $completion[$quest]["cleanDone"][] = $state["cleanDoneAt"];
};

$groupKey = null;
$quest = null;
$state = null;

foreach ($rows as $r) {
    $level = (int) $r["level_id"];
    $task = (int) $r["task_id"];
    $need = $task === 4 ? 2 : 3;
    $key = $r["player_id"] . "|" . $level . "|" . $task;

    if ($key !== $groupKey) {
        $flush($quest, $state);
        $groupKey = $key;
        $quest = "L" . $level . "." . $task;
        $state = array(
            "attemptNo" => 0, "sessionNo" => 0, "session" => null,
            "streak" => 0, "cleanStreak" => 0,
            "doneAt" => null, "doneInSession" => null, "cleanDoneAt" => null,
            "prevTs" => null, "prevDur" => null, "prevHasMs" => false,
        );
    }

    if ($r["session_id"] !== $state["session"]) {
        $state["session"] = $r["session_id"];
        $state["sessionNo"] = 0;
        $state["streak"] = 0;
        $state["cleanStreak"] = 0;
        $state["prevTs"] = null;
        $state["prevDur"] = null;
    }

    $state["attemptNo"]++;
    $state["sessionNo"]++;

    $acc = (int) $r["accuracy"];
    $err = (int) $r["error_ms"];
    $win = is_true_three_win($acc, $err, (int) $r["tolerance_ms"]);
    $clean = is_true_three_win($acc, $err, 0);
    // Exact ms start time when the row has it (migrate_003), else whole seconds.
    $hasMs = $r["recorded_ms"] !== null;
    $ts = $hasMs ? ((float) $r["recorded_ms"]) / 1000.0 : (float) strtotime($r["recorded_at"]);
    $dur = (int) $r["duration_ms"];

    // Pause = from the previous STOP to this RUN. Counted only when both
    // attempts have exact ms time — whole-second timestamps are too coarse.
    $pause = null;
    if ($state["prevTs"] !== null && $hasMs && $state["prevHasMs"]) {
        $gap = $ts - $state["prevTs"] - $state["prevDur"] / 1000.0;
        if ($gap >= 0 && $gap <= 60) $pause = round($gap, 3);
    }

    // Reaction: only while the task is still open in this session.
    if ($state["doneAt"] === null) {
        $s = $state["streak"];
        if (!isset($reaction[$quest][$s])) $reaction[$quest][$s] = array();
        $reaction[$quest][$s][] = array("win" => $win ? 1 : 0, "err" => $err, "pause" => $pause);
    }

    $state["streak"] = $win ? $state["streak"] + 1 : 0;
    $state["cleanStreak"] = $clean ? $state["cleanStreak"] + 1 : 0;

    if ($state["doneAt"] === null && $state["streak"] >= $need) {
        $state["doneAt"] = $state["attemptNo"];
        $state["doneInSession"] = $state["sessionNo"];
    }
    if ($state["cleanDoneAt"] === null && $state["cleanStreak"] >= $need) {
        $state["cleanDoneAt"] = $state["attemptNo"];
    }

    $state["prevTs"] = $ts;
    $state["prevDur"] = $dur;
    $state["prevHasMs"] = $hasMs;
}
$flush($quest, $state);

$outCompletion = array();
ksort($completion);
foreach ($completion as $q => $c) {
    sort($c["done"]);
    sort($c["cleanDone"]);
    sort($c["notDoneAttempts"]);
    $outCompletion[$q] = array(
        "playersStarted" => $c["started"],
        "playersCompleted" => count($c["done"]),
        "attemptsToComplete_median" => median_of($c["done"]),
        "attemptsToComplete_all" => $c["done"],
        "attemptInItsSession_median" => median_of($c["doneInSession"]),
        "cleanOnly_playersCompleted" => count($c["cleanDone"]),
        "cleanOnly_attemptsToComplete_median" => median_of($c["cleanDone"]),
        "notCompleted_attemptsSoFar" => $c["notDoneAttempts"],
    );
}

$outReaction = array();
ksort($reaction);
foreach ($reaction as $q => $byStreak) {
    ksort($byStreak);
    foreach ($byStreak as $s => $pts) {
        $abs = array(); $signed = array(); $pauses = array(); $wins = 0; $early = 0;
        foreach ($pts as $p) {
            $abs[] = abs($p["err"]);
            $signed[] = $p["err"];
            $wins += $p["win"];
            if ($p["err"] < 0) $early++;
            if ($p["pause"] !== null) $pauses[] = $p["pause"];
        }
        $n = count($pts);
        $outReaction[$q]["after_" . $s . "_in_a_row"] = array(
            "n" => $n,
            "hitRatePct" => round($wins / $n * 100, 1),
            "medianAbsErrorMs" => median_of($abs),
            "medianSignedErrorMs" => median_of($signed),
            "earlyPct" => round($early / $n * 100, 1),
            "pauseN" => count($pauses),
            "meanPauseSec" => count($pauses) ? round(array_sum($pauses) / count($pauses), 3) : null,
            "medianPauseSec" => count($pauses) ? round(median_of($pauses), 3) : null,
        );
    }
}

// Is exact ms time arriving at all? Whole table, not only ROW quests.
$diag = $pdo->query(
    "SELECT COUNT(*) AS total,
            SUM(CASE WHEN recorded_ms IS NOT NULL THEN 1 ELSE 0 END) AS with_ms,
            MAX(recorded_at) AS last_attempt_at,
            MAX(CASE WHEN recorded_ms IS NOT NULL THEN recorded_at END) AS last_ms_attempt_at
     FROM attempts"
)->fetch();

json_out(array(
    "generatedAt" => gmdate("c"),
    "exactTimeCheck" => array(
        "attemptsTotal" => (int) $diag["total"],
        "attemptsWithExactMs" => (int) $diag["with_ms"],
        "lastAttemptAt" => $diag["last_attempt_at"],
        "lastAttemptWithMsAt" => $diag["last_ms_attempt_at"],
    ),
    "rowsScanned" => count($rows),
    "completion" => $outCompletion,
    "reaction" => $outReaction,
));
