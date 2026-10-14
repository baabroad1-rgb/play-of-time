<?php
// GET ?level=1..3[&playerId=…] → {rows: [...]}
//
// One row per player: their LATEST complete measurement at that level.
// Ordered by attempts per win (fewer is better; no wins last). Other players'
// ids are never returned — only names; "you" marks the caller's own row.
require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

$level = isset($_GET["level"]) ? (int) $_GET["level"] : 1;
if ($level < 1 || $level > 3) json_error("invalid level");
$me = isset($_GET["playerId"]) ? trim((string) $_GET["playerId"]) : "";

$pdo = m_db();
$q = $pdo->prepare("SELECT p.id AS pid, p.display_name, m.late_avg_ms, m.early_avg_ms, m.per_minute, m.per_win, m.ended_ms
    FROM m_measurements m
    JOIN m_players p ON p.id = m.player_id
    WHERE m.level_id = ?
      AND m.ended_ms = (SELECT MAX(m2.ended_ms) FROM m_measurements m2 WHERE m2.player_id = m.player_id AND m2.level_id = ?)");
$q->execute(array($level, $level));

$rows = array();
$seen = array();
foreach ($q->fetchAll() as $r) {
    if (isset($seen[$r["pid"]])) continue; // two measurements ending the same ms
    $seen[$r["pid"]] = true;
    $rows[] = array(
        "name" => $r["display_name"],
        "you" => $me !== "" && $r["pid"] === $me,
        "lateAvgMs" => $r["late_avg_ms"] === null ? null : (float) $r["late_avg_ms"],
        "earlyAvgMs" => $r["early_avg_ms"] === null ? null : (float) $r["early_avg_ms"],
        "perMinute" => $r["per_minute"] === null ? null : (float) $r["per_minute"],
        "perWin" => $r["per_win"] === null ? null : (float) $r["per_win"],
        "endedMs" => (int) $r["ended_ms"],
    );
}
usort($rows, function ($a, $b) {
    $x = $a["perWin"]; $y = $b["perWin"];
    if ($x === null && $y === null) return $b["endedMs"] - $a["endedMs"];
    if ($x === null) return 1;
    if ($y === null) return -1;
    return $x < $y ? -1 : ($x > $y ? 1 : 0);
});
json_out(array("rows" => $rows));
