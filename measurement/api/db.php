<?php
// One PDO connection per request + the measurement tables.
//
// Credentials: the game's server-side config (/api/config.php on the host,
// never in the repository and never read from here by anyone but PHP on the
// server). Same database, SEPARATE tables with the m_ prefix — the game's own
// players/attempts tables are never read or written by this API.
//
// POT_TEST_DSN lets a local harness point this at a throwaway SQLite file,
// the same trick as the game's api/db.php.
function m_db() {
    static $pdo = null;
    if ($pdo !== null) return $pdo;

    $opts = array(
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    );
    $testDsn = getenv("POT_TEST_DSN");
    if ($testDsn) {
        $pdo = new PDO($testDsn, null, null, $opts);
    } else {
        $cfg = require __DIR__ . "/../../api/config.php";
        $port = isset($cfg["port"]) ? $cfg["port"] : 3306;
        $dsn = "mysql:host=" . $cfg["host"] . ";port=" . $port . ";dbname=" . $cfg["name"] . ";charset=utf8mb4";
        $pdo = new PDO($dsn, $cfg["user"], $cfg["pass"], $opts);
    }
    m_ensure_tables($pdo);
    return $pdo;
}

// Nobody has DB-console access, so the tables create themselves on first use
// (same approach as the game's sync.php self-migrations). IF NOT EXISTS makes
// it a no-op on every later request.
function m_ensure_tables(PDO $pdo) {
    $sqlite = $pdo->getAttribute(PDO::ATTR_DRIVER_NAME) === "sqlite";
    if ($sqlite) {
        $pdo->exec("CREATE TABLE IF NOT EXISTS m_players (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            id TEXT NOT NULL UNIQUE,
            sync_token TEXT NOT NULL,
            display_name TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL)");
        $pdo->exec("CREATE TABLE IF NOT EXISTS m_measurements (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_id TEXT NOT NULL,
            local_id INTEGER NOT NULL,
            level_id INTEGER NOT NULL,
            attempts INTEGER NOT NULL,
            wins INTEGER NOT NULL,
            late_avg_ms REAL NULL,
            early_avg_ms REAL NULL,
            per_minute REAL NULL,
            per_win REAL NULL,
            started_ms INTEGER NOT NULL,
            ended_ms INTEGER NOT NULL,
            UNIQUE (player_id, local_id))");
        return;
    }
    $pdo->exec("CREATE TABLE IF NOT EXISTS m_players (
        id CHAR(36) NOT NULL,
        seq INT UNSIGNED NOT NULL AUTO_INCREMENT,
        sync_token CHAR(32) NOT NULL,
        display_name VARCHAR(40) NOT NULL DEFAULT '',
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_m_seq (seq)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $pdo->exec("CREATE TABLE IF NOT EXISTS m_measurements (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        player_id CHAR(36) NOT NULL,
        local_id INT UNSIGNED NOT NULL,
        level_id TINYINT UNSIGNED NOT NULL,
        attempts INT UNSIGNED NOT NULL,
        wins INT UNSIGNED NOT NULL,
        late_avg_ms DOUBLE NULL,
        early_avg_ms DOUBLE NULL,
        per_minute DOUBLE NULL,
        per_win DOUBLE NULL,
        started_ms BIGINT NOT NULL,
        ended_ms BIGINT NOT NULL,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_m_player_local (player_id, local_id),
        KEY idx_m_level (level_id, player_id, ended_ms)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
