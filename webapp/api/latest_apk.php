<?php
// Serves whatever single .apk file currently sits in ../downloads/. Alex
// decided (2026-09-22) to keep exactly one APK there at a time, so this no
// longer needs to parse version numbers out of the filename at all -- it
// just serves the one .apk it finds. If more than one somehow ends up there
// (shouldn't happen under the new convention), it falls back to the most
// recently modified one rather than guessing from the name.
//
// Added 2026-08-14 after a deploy broke the site: index.html had a hardcoded
// apk filename that went stale the moment a newer build was uploaded under a
// different name. See TASKS.md P0 notes for that incident.
//
// 2026-09-22: moved from ../assets/ to ../downloads/ (shared with the
// native app's own self-update app_version.json), then simplified from
// "highest play-of-time-vMAJOR.MINOR.apk by version number" to "the one
// .apk file in downloads/" per Alex's decision the same day.

$downloadsDir = __DIR__ . '/../downloads';
$matches = glob($downloadsDir . '/*.apk');
if ($matches === false) {
    $matches = [];
}

$best = null;
$bestMtime = -1;
foreach ($matches as $path) {
    $mtime = filemtime($path);
    if ($mtime !== false && $mtime > $bestMtime) {
        $bestMtime = $mtime;
        $best = $path;
    }
}

if ($best === null) {
    http_response_code(404);
    header('Content-Type: text/plain');
    echo "No APK found in downloads/.";
    exit;
}

$filename = basename($best);
header('Content-Type: application/vnd.android.package-archive');
header('Content-Disposition: attachment; filename="' . $filename . '"');
header('Content-Length: ' . filesize($best));
header('Cache-Control: no-cache'); // always re-check for a newer build, don't let browsers cache a stale one
readfile($best);
