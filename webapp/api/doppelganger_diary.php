<?php
// doppelganger_diary.php — Doppelgänger's diary, written by a language model
// (Alex 2026-11-08: «давай попробуем OpenRouter… запросы генерятся, может,
// прямо только если человек зашёл и хочет почитать»).
//
// POST {playerId, day, facts, memory} → {entry, memory, model}
//
// The phone sends ONLY what its engine counted for one day of the rival
// (numbers, quest events, his habit changes) and his memory — a short
// "what I know about myself" the model rewrote last time. The model gets the
// character file (doppelganger_character.txt — the same file the simulator
// tools/micro_player/diary_llm.js reads) as the system prompt and answers
// with today's entry and the new memory. It has no numbers of its own to
// invent from: every fact comes from the engine.
//
// The OpenRouter key lives ONLY on the server, in api/openrouter_key.php
// (`<?php return 'sk-or-…';`), created by hand on the hosting and excluded
// from the deploy like config.php — never in git, never in the APK. Without
// it the endpoint answers 503 and the app keeps its rule-based notes.
//
// Limits (the key pays per request): per player and in total, per UTC day,
// counted in files under api/_dg_quota/ (denied to the web by .htaccess).

require __DIR__ . '/_lib.php';

const DG_MODEL = 'anthropic/claude-haiku-5.5';
const DG_PER_PLAYER_PER_DAY = 10;
const DG_TOTAL_PER_DAY = 2000;

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('POST only', 405);

$keyFile = __DIR__ . '/openrouter_key.php';
$key = is_file($keyFile) ? trim((string) (include $keyFile)) : '';
if ($key === '') json_error('diary writer not configured', 503);

function dg_clean($raw, $max) {
    $s = preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', trim((string) $raw));
    return function_exists('mb_substr') ? mb_substr($s, 0, $max, 'UTF-8') : substr($s, 0, $max);
}

$in = json_input();
$player = (string) ($in['playerId'] ?? '');
if (!preg_match('/^[0-9A-Za-z-]{8,64}$/', $player)) json_error('bad playerId');
$facts = $in['facts'] ?? null;
if (!is_array($facts)) json_error('no facts');
$factsJson = json_encode($facts, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
if ($factsJson === false || strlen($factsJson) > 8000) json_error('facts too long', 413);
$memory = dg_clean($in['memory'] ?? '', 900);
if ($memory === '') $memory = 'Nothing yet. I have never played.';

// ── quota ────────────────────────────────────────────────────────────────────
function dg_take_quota($who) {
    $dir = __DIR__ . '/_dg_quota';
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    if (!is_file($dir . '/.htaccess')) @file_put_contents($dir . '/.htaccess', "Require all denied\nDeny from all\n");
    $today = gmdate('Ymd');
    foreach (glob($dir . '/*.json') ?: array() as $old) {           // keep a week
        if (basename($old, '.json') < gmdate('Ymd', time() - 7 * 86400)) @unlink($old);
    }
    $fp = @fopen($dir . '/' . $today . '.json', 'c+');
    if (!$fp) return true;                                          // can't count — don't block the player
    flock($fp, LOCK_EX);
    $data = json_decode(stream_get_contents($fp), true);
    if (!is_array($data)) $data = array('total' => 0, 'who' => array());
    $mine = (int) ($data['who'][$who] ?? 0);
    $ok = $data['total'] < DG_TOTAL_PER_DAY && $mine < DG_PER_PLAYER_PER_DAY;
    if ($ok) {
        $data['total']++;
        $data['who'][$who] = $mine + 1;
        ftruncate($fp, 0);
        rewind($fp);
        fwrite($fp, json_encode($data));
        fflush($fp);
    }
    flock($fp, LOCK_UN);
    fclose($fp);
    return $ok;
}
if (!dg_take_quota(hash('sha256', $player))) json_error('daily limit reached', 429);

// ── ask the model ────────────────────────────────────────────────────────────
$character = @file_get_contents(__DIR__ . '/doppelganger_character.txt');
if ($character === false) json_error('character file missing', 500);
$body = json_encode(array(
    'model' => DG_MODEL,
    'messages' => array(
        array('role' => 'system', 'content' => $character),
        array('role' => 'user', 'content' => "MEMORY:\n" . $memory . "\n\nFACTS:\n" . $factsJson),
    ),
    'temperature' => 0.7,
    'max_tokens' => 2500,
    'response_format' => array('type' => 'json_object'),
), JSON_UNESCAPED_UNICODE);
$headers = array(
    'Authorization: Bearer ' . $key,
    'Content-Type: application/json',
    'HTTP-Referer: https://playoftime.online',
    'X-Title: Play of Time',
);
$url = 'https://openrouter.ai/api/v1/chat/completions';
if (function_exists('curl_init')) {
    $ch = curl_init($url);
    curl_setopt_array($ch, array(
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $body,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 50,
    ));
    $raw = curl_exec($ch);
    $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
} else {
    $ctx = stream_context_create(array('http' => array(
        'method' => 'POST', 'header' => implode("\r\n", $headers), 'content' => $body,
        'timeout' => 50, 'ignore_errors' => true,
    )));
    $raw = @file_get_contents($url, false, $ctx);
    $code = 0;
    foreach ($http_response_header ?? array() as $h) {
        if (preg_match('#^HTTP/\S+\s+(\d{3})#', $h, $m)) $code = (int) $m[1];
    }
}
if ($raw === false || $code !== 200) json_error('diary writer unavailable', 502);

$r = json_decode($raw, true);
$text = trim((string) ($r['choices'][0]['message']['content'] ?? ''));
$text = preg_replace('/^```(?:json)?\s*|\s*```$/', '', $text);
$out = json_decode($text, true);
if (!is_array($out) || trim((string) ($out['entry'] ?? '')) === '') json_error('diary writer returned nothing usable', 502);

json_out(array(
    'entry'  => dg_clean($out['entry'], 1600),
    'memory' => dg_clean($out['memory'] ?? $memory, 900),
    'model'  => DG_MODEL,
));
