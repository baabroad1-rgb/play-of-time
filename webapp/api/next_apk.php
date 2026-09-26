<?php
// Отдаёт APK ПЕРЕИМЕНОВАННОГО приложения (online.playoftime) из downloads/next/.
//
// Зачем отдельный адрес. По основному (latest_apk.php) лежит последняя версия
// СТАРОГО приложения — она умеет отдать свою историю новому. Если положить
// туда новое, игрок, нажавший обновление первым делом, поставит его раньше,
// чем у него окажется старое с отдачей данных, и переедет без прогресса.
// Поэтому порядок зашит в софт: по основному адресу всегда старое, а ссылку
// на новое показывает оно само, уже умея отдать историю.
//
// Логика та же, что в latest_apk.php: отдаём единственный .apk из папки,
// если их вдруг несколько — самый свежий по дате.

$dir = __DIR__ . '/../downloads/next';
$matches = glob($dir . '/*.apk');
if ($matches === false) $matches = [];

$best = null;
$bestMtime = -1;
foreach ($matches as $path) {
    $mtime = filemtime($path);
    if ($mtime !== false && $mtime > $bestMtime) { $bestMtime = $mtime; $best = $path; }
}

if ($best === null) {
    http_response_code(404);
    header('Content-Type: text/plain');
    echo "No APK found in downloads/next/.";
    exit;
}

header('Content-Type: application/vnd.android.package-archive');
header('Content-Disposition: attachment; filename="' . basename($best) . '"');
header('Content-Length: ' . filesize($best));
header('Cache-Control: no-cache');
readfile($best);
