<?php
header("Content-Type: application/json; charset=utf-8");

function json_input() {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);
    return is_array($data) ? $data : array();
}

function json_out($data, $status = 200) {
    http_response_code($status);
    echo json_encode($data);
    exit;
}

function json_error($message, $status = 400) {
    json_out(array("error" => $message), $status);
}

// Strip control characters and cap length. Shared by sync.php (name arrives
// bundled with attempts) and set_name.php (name arrives alone).
function sanitize_name($raw) {
    $name = trim((string) $raw);
    if ($name === "") return null;
    $name = preg_replace('/[\x00-\x1F\x7F]/u', '', $name);
    $name = function_exists('mb_substr') ? mb_substr($name, 0, 30, 'UTF-8') : substr($name, 0, 30);
    return $name === "" ? null : $name;
}

// The name a player gets before (or after clearing) a custom one — no more
// bare "Anonymous": playerNNN (join order) + the date they first synced.
// SUPERSEDED 2026-09-11 by generate_short_name() below for NEW assignments —
// too long for a leaderboard row (Alex: "торопыга" moment, see TASKS.md).
// Kept here (not deleted) because migrate_002_rename_default_names.php
// pattern-matches this exact format to find rows still on the old default.
function default_player_name($seq, $createdAtSql) {
    $ts = strtotime($createdAtSql . " UTC");
    if ($ts === false) $ts = time();
    return sprintf("player%03d_join_%s", (int) $seq, gmdate("d.m.Y", $ts));
}

// Matches ONLY the exact old-style default ("player017_join_10.09.2026") —
// used by the migration to find rows nobody has ever renamed. A real
// player-typed name matching this exact shape is astronomically unlikely
// and not worth guarding against.
function looks_like_old_default_name($name) {
    return is_string($name) && preg_match('/^player\d{3}_join_\d{2}\.\d{2}\.\d{4}$/', $name) === 1;
}

// Syllable-based short pronounceable default name (Alex 2026-09-11) — the
// ONE shared algorithm for both native and webapp: neither client generates
// its own name anymore, both just display whatever sync.php/set_name.php
// hands back, so there's nothing to keep in sync between two codebases.
// Opening + vowel [+ closing] fragments, capitalized, ~4-8 chars. Uniqueness
// is checked for real against the players table (this is the one place that
// CAN see every player, unlike a per-device client-side check).
function generate_short_name(PDO $pdo) {
    $openings = array("Br", "Th", "Kr", "Fl", "Vor", "Zan", "Sil", "Dra", "Gr", "Or", "Mor", "Tal", "Sel", "Rin");
    $vowels   = array("a", "o", "e", "i", "u", "ee", "ai", "oa", "ie", "ou", "y", "ae");
    $closings = array("n", "th", "rk", "x", "s", "rn", "d", "lm", "ra", "sh", "k", "nor");

    $check = $pdo->prepare("SELECT 1 FROM players WHERE display_name = ? LIMIT 1");

    for ($attempt = 0; $attempt < 20; $attempt++) {
        $candidate = build_name_candidate($openings, $vowels, $closings);
        $check->execute(array($candidate));
        if ($check->fetch() === false) return $candidate;
    }
    // Exhausted retries — append a short numeric suffix as a last resort.
    return build_name_candidate($openings, $vowels, $closings) . random_int(10, 99);
}

function build_name_candidate($openings, $vowels, $closings) {
    $opening = $openings[array_rand($openings)];
    $vowel   = $vowels[array_rand($vowels)];
    // ~60% of names get a closing fragment, the rest stay open — reads more
    // varied than always concatenating all three fragments.
    $closing = (random_int(0, 99) < 60) ? $closings[array_rand($closings)] : "";
    // Plain ucfirst(), not mb_*: every fragment above is ASCII on purpose, and
    // mbstring isn't guaranteed present on the host (sanitize_name() above
    // already guards the same way for exactly this reason).
    return ucfirst($opening . $vowel . $closing);
}

// Mirrors AnalyticsUseCase.kt's countTrueThree() exactly (Alex 2026-09-11:
// leaderboard.php's Lucky Index and Hit Rate columns must match the native
// Analytics screen's own numbers, not approximate them) — a "win" for Hit
// Rate purposes is the attempt's duration rounded to ITS OWN accuracy grid
// (deci=100ms/centi=10ms/milli=1ms) landing within tolerance_ms (+ the
// resolution floor, currently a permanent no-op — TOUCH_RESOLUTION_FLOOR_MS
// = 0 in Utils.kt — kept here only so this stays a faithful mirror if that
// ever changes on the native side).
//
// This is DELIBERATELY different from the Lucky Index numerator (a strict
// error_ms === 0 check, done inline where it's used) — the project's own
// established rule (13-14 August "diverges by multiples" fix) is that Lucky
// Index counts only exact clean threes, never tolerance/subquest-eased wins.
function is_true_three_win($accuracyOrdinal, $errorMs, $toleranceMs) {
    $factor = pow(10, (int) $accuracyOrdinal);
    $aimMs = 3000; // LocalStorageService.getAimTime()'s default — a constant in practice
    $aimRounded = round($aimMs / 1000.0 * $factor);
    $durRounded = round(($aimMs + $errorMs) / 1000.0 * $factor);
    $roundedErrMs = ($durRounded - $aimRounded) * (1000.0 / $factor);
    $resolutionFloorMs = 0; // TOUCH_RESOLUTION_FLOOR_MS, Utils.kt — permanently disabled
    $winTol = max((int) $toleranceMs, $resolutionFloorMs);
    return abs($roundedErrMs) <= $winTol + 0.5;
}

// Lucky Index (Alex 2026-10-24) -- the same formula as native
// core/utils/LuckyIndex.kt and the web app's luckyIndexOf():
//   theoretical probability of a green attempt = green window / whole range,
//     green window = 2 * floor(tolerance / cell) + 1 cells of the level
//     (tolerance = the quest's easing at that moment, 0 without easing),
//     whole range  = floor(span / cell) + 1 cells, span = |P3| + |P97| of the
//     signed errors of THAT level's attempts in the set;
//   empirical probability = green attempts / all attempts;
//   Lucky = empirical / average theoretical. 0 when nothing was green.
// $items: list of [accuracyOrdinal, errorMs, toleranceMs, isGreen].
function lucky_index($items) {
    $n = count($items);
    if ($n === 0) return 0.0;
    $byAcc = array();
    foreach ($items as $it) $byAcc[(int) $it[0]][] = (int) $it[1];
    $span = array();
    foreach ($byAcc as $acc => $errs) {
        sort($errs);
        $m = count($errs);
        $lo = $errs[min($m - 1, max(0, (int) ($m * 0.03)))];
        $hi = $errs[min($m - 1, max(0, (int) ($m * 0.97)))];
        $span[$acc] = abs($lo) + abs($hi);
    }
    $greens = 0;
    $sumP = 0.0;
    $p = array();
    $w = array();
    foreach ($items as $i => $it) {
        $acc = (int) $it[0];
        $cell = pow(10, 3 - $acc);
        $k = 2 * (int) floor(max(0, (int) $it[2]) / $cell) + 1;
        $all = (int) floor($span[$acc] / $cell) + 1;
        $p[$i] = min(1.0, $k / $all);
        $w[$i] = $k;
        $sumP += $p[$i];
        if ($it[3]) $greens++;
    }
    if ($greens === 0 || $sumP <= 0) return 0.0;
    // Зелёные близнецы (Alex 11.11.2026, docs/LUCKY_INDEX_TWINS.md): зелёная, и
    // следующая в той же сессии — зелёная с тем же модулем промаха (одинаковая или
    // зеркальная). Lucky = (G + T) / (E_G + E_T), E_T = Σ pᵢpᵢ₊₁·(2w−1)/(wᵢwᵢ₊₁).
    $twins = 0;
    $expT = 0.0;
    $idx = array();
    foreach ($items as $i => $it) if (isset($it[4])) $idx[] = $i;
    usort($idx, function ($x, $y) use ($items) {
        return $items[$x][4] === $items[$y][4] ? ($items[$x][5] <=> $items[$y][5]) : ($items[$x][4] <=> $items[$y][4]);
    });
    $cells = function ($it) {
        $cell = (int) pow(10, 3 - (int) $it[0]);
        $d = (int) $it[1] + 3000;
        return (int) floor(($d + $cell / 2) / $cell) - (int) floor((3000 + $cell / 2) / $cell);
    };
    for ($j = 1; $j < count($idx); $j++) {
        $a = $items[$idx[$j - 1]];
        $b = $items[$idx[$j]];
        if ($a[4] !== $b[4]) continue;
        if ($a[3] && $b[3] && abs($cells($a)) === abs($cells($b))) $twins++;
        if ((int) $a[0] === (int) $b[0]) {
            $m = min($w[$idx[$j - 1]], $w[$idx[$j]]);
            $expT += $p[$idx[$j - 1]] * $p[$idx[$j]] * (2 * $m - 1) / ($w[$idx[$j - 1]] * $w[$idx[$j]]);
        }
    }
    return round(($greens + $twins) / ($sumP + $expT), 2);
}

// UUID-ish shape check on playerId — not a strict RFC4122 validator, just
// enough to reject junk before it reaches a query.
function looks_like_id($value) {
    return is_string($value) && preg_match('/^[a-f0-9-]{20,40}$/i', $value) === 1;
}

function looks_like_token($value) {
    return is_string($value) && preg_match('/^[a-f0-9]{16,40}$/i', $value) === 1;
}
