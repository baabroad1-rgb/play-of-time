<?php
// Three random quotes from aka mulator and three random questions from Question of the Day,
// for the main page of baa.playoftime.online.
//
// Posts are read from the channels' public web pages (t.me/s/<channel>) and kept in
// data/<channel>.json. At most every REFRESH seconds a request also checks the channels for
// new posts: it reads the newest page and walks back until it meets a post it already has.
// The data files in the repository are a starting snapshot, so the script never has to read
// the whole history on the server. Run "php samples.php --seed" to rebuild that snapshot.
//
// Hidden parts of a post (Telegram spoilers) are left out; book hashtags become the source.

const CHANNELS = ['quotes' => 'baaqod', 'questions' => 'q_of_t_d'];
const REFRESH = 6 * 3600;      // seconds between checks for new posts
const MAX_PAGES = 8;           // pages to walk back on one check
const PICK = 3;
// hashtags that mark jokes or other non-quotes
const NOT_QUOTES = ['смешно_в_обе_стороны', 'kinda_not_a_joke', 'ммк', 'стрелков', 'play_with_time'];

function data_file(string $ch): string { return __DIR__ . "/data/$ch.json"; }

function fetch_page(string $ch, ?int $before = null): ?string {
    $url = "https://t.me/s/$ch" . ($before ? "?before=$before" : '');
    $ctx = stream_context_create(['http' => ['timeout' => 8, 'header' => "User-Agent: Mozilla/5.0 (compatible; baa-samples)\r\n"]]);
    $html = @file_get_contents($url, false, $ctx);
    return $html === false ? null : $html;
}

function clean(string $html): string {
    $html = preg_replace('~<br\s*/?>~i', "\n", $html);
    $t = html_entity_decode(strip_tags($html), ENT_QUOTES | ENT_HTML5, 'UTF-8');
    $t = preg_replace("~[ \t\x{00A0}]+~u", ' ', $t);
    $t = preg_replace("~\n{3,}~", "\n\n", $t);
    return trim($t);
}

/** @return array<int, array{t:string, src:string, sp:bool, d:string}> */
function parse_page(string $ch, string $html): array {
    $out = [];
    $blocks = preg_split('~(?=<div class="tgme_widget_message_wrap)~', $html);
    foreach ($blocks as $b) {
        if (!preg_match('~data-post="' . preg_quote($ch, '~') . '/(\d+)"~', $b, $m)) continue;
        $id = (int)$m[1];
        if (!preg_match_all('~<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>~s', $b, $mm)) continue;
        $raw = end($mm[1]);                          // the post's own text, not a quoted reply
        $sp = (bool)preg_match('~<tg-spoiler~', $raw);
        $raw = preg_replace('~<tg-spoiler>.*?</tg-spoiler>~s', '', $raw);
        $tags = [];
        if (preg_match_all('~<a href="\?q=%23[^"]*">#([^<]+)</a>~u', $raw, $tm)) $tags = $tm[1];
        $raw = preg_replace('~<a href="\?q=%23[^"]*">#[^<]+</a>~u', '', $raw);
        $date = preg_match('~datetime="(\d{4}-\d\d-\d\d)~', $b, $dm) ? $dm[1] : '';
        $out[$id] = ['t' => clean($raw), 'src' => $tags ? $tags[0] : '', 'sp' => $sp, 'd' => $date];
    }
    return $out;
}

function load(string $ch): array {
    $j = @json_decode(@file_get_contents(data_file($ch)), true);
    return is_array($j) ? $j + ['updated' => 0, 'posts' => []] : ['updated' => 0, 'posts' => []];
}

function save(string $ch, array $data): void {
    $tmp = data_file($ch) . '.tmp';
    if (@file_put_contents($tmp, json_encode($data, JSON_UNESCAPED_UNICODE)) !== false) @rename($tmp, data_file($ch));
}

/** Add new posts: newest page, then back until a known post (or MAX_PAGES; all pages when $all). */
function refresh(string $ch, array $data, bool $all = false): array {
    $before = null;
    for ($i = 0; $all || $i < MAX_PAGES; $i++) {
        $html = fetch_page($ch, $before);
        if ($html === null) break;
        $posts = parse_page($ch, $html);
        if (!$posts) break;
        $known = false;
        foreach ($posts as $id => $p) {
            $old = $data['posts'][(string)$id] ?? null;
            if ($old) {
                $known = true;
                // keep an English translation made for the same text
                if (isset($old['en']) && ($old['en_of'] ?? null) === $p['t']) { $p['en'] = $old['en']; $p['en_of'] = $old['en_of']; }
            }
            $data['posts'][(string)$id] = $p;
        }
        $min = min(array_keys($posts));
        if (($known && !$all) || $min <= 1) break;
        $before = $min;
        if ($all) usleep(300000);
    }
    $data['updated'] = time();
    return $data;
}

function is_quote(array $p): bool {
    $len = mb_strlen($p['t']);
    if ($len < 60 || $len > 420) return false;
    if (in_array(mb_strtolower($p['src']), NOT_QUOTES, true)) return false;
    if (preg_match('~https?://~', $p['t'])) return false;
    return true;
}

function is_question(array $p): bool {
    $len = mb_strlen($p['t']);
    return $len >= 25 && $len <= 320 && mb_strpos($p['t'], '?') !== false && !preg_match('~https?://~', $p['t']);
}

// The page is in English: only posts written in English to begin with are shown
// (by Nov 2026: 171 quotes, 66 questions). Stored AI translations are kept but not used.
function readable(array $p): bool { return !preg_match('~\p{Cyrillic}~u', $p['t']); }

function pick(string $ch, array $data, callable $ok): array {
    $ids = [];
    foreach ($data['posts'] as $id => $p) if ($ok($p) && readable($p)) $ids[] = $id;
    shuffle($ids);
    $out = [];
    foreach (array_slice($ids, 0, PICK) as $id) {
        $p = $data['posts'][$id];
        $out[] = ['t' => $p['en'] ?? $p['t'], 'ai' => isset($p['en']), 'src' => str_replace('_', ' ', $p['src']), 'url' => "https://t.me/$ch/$id"];
    }
    return $out;
}

// --- command line: rebuild the starting snapshot from the whole history ---
if (PHP_SAPI === 'cli' && in_array('--seed', $argv ?? [], true)) {
    @mkdir(__DIR__ . '/data');
    foreach (CHANNELS as $ch) {
        $d = refresh($ch, ['updated' => 0, 'posts' => []], true);
        $d['updated'] = 0;                       // let the server check for new posts on first use
        save($ch, $d);
        echo "$ch: " . count($d['posts']) . " posts\n";
    }
    exit;
}

// --- web request ---
$result = [];
foreach (CHANNELS as $kind => $ch) {
    $data = load($ch);
    if (time() - (int)$data['updated'] > REFRESH) {
        $lock = @fopen(data_file($ch) . '.lock', 'c');
        if ($lock && flock($lock, LOCK_EX | LOCK_NB)) {   // one refresh at a time; others use what is there
            $data = refresh($ch, $data);
            save($ch, $data);
            flock($lock, LOCK_UN);
        }
        if ($lock) fclose($lock);
    }
    $result[$kind] = pick($ch, $data, $kind === 'quotes' ? 'is_quote' : 'is_question');
}
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
echo json_encode($result, JSON_UNESCAPED_UNICODE);
