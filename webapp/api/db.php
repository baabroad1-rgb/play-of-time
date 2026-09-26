<?php
// Opens (and caches) one PDO connection per request.
//
// POT_TEST_DSN/_USER/_PASS let a local test harness point this at a throwaway
// SQLite file instead of MySQL, exercising the exact same code paths as
// production. These env vars are never set on the real server, so normal
// requests always use config.php + MySQL.
function pot_db() {
    static $pdo = null;
    if ($pdo !== null) return $pdo;

    $testDsn = getenv("POT_TEST_DSN");
    if ($testDsn) {
        $pdo = new PDO($testDsn, getenv("POT_TEST_USER") ?: null, getenv("POT_TEST_PASS") ?: null, array(
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ));
        return $pdo;
    }

    $cfg = require __DIR__ . "/config.php";
    $port = isset($cfg["port"]) ? $cfg["port"] : 3306;
    $dsn = "mysql:host=" . $cfg["host"] . ";port=" . $port . ";dbname=" . $cfg["name"] . ";charset=utf8mb4";
    $pdo = new PDO($dsn, $cfg["user"], $cfg["pass"], array(
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ));
    return $pdo;
}
