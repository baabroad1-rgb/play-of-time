<?php
// Сколько игроков на какой сборке приложения (Alex 2026-09-27: «а когда же
// переезд закончится то?»).
//
// Зачем понадобилось. При переезде на новый идентификатор пакета на телефонах
// живут ДВА приложения: мостовое старое (14.9x) и новое (15.xx). Мостовое
// нельзя убрать с сайта, пока хоть у кого-то из играющих история ещё лежит в
// старом — иначе он останется без источника. А узнать, кто где, было нечем:
// синхронизация присылала попытки и ни слова о том, из какого приложения.
// Теперь присылает версию, а этот адрес показывает раскладку.
//
// Отдаёт ТОЛЬКО сводку: версия, сколько игроков, когда последняя
// синхронизация в этой группе, сколько из них заходили за последнюю неделю.
// Ни имён, ни идентификаторов — по номеру версии игрока опознать нельзя, а
// адрес открытый.
//
// NULL в версии = сборка старее 15.02 (новое приложение) или 14.93 (мостовое):
// они версию не присылают. Эта группа будет таять по мере обновлений.

require __DIR__ . "/_lib.php";
require __DIR__ . "/db.php";

if ($_SERVER["REQUEST_METHOD"] !== "GET") json_error("GET only", 405);

$pdo = pot_db();

// Колонки может ещё не быть, если sync.php с самомиграцией не успел
// отработать ни разу. Это не ошибка — просто отвечаем, что данных нет.
try {
    $pdo->query("SELECT app_version FROM players LIMIT 0");
} catch (Exception $e) {
    json_out(array(
        "ready"   => false,
        "note"    => "app_version column not created yet — no sync has happened since it was added",
        "groups"  => array(),
    ));
}

// Только игроки, которые реально играли: с нулём попыток в лидерборд не
// попадают и переезд не задерживают.
//
// Запрос намеренно без MySQL-специфики: граница недели считается в PHP и
// уходит параметром, "за неделю" — через CASE, сортировка тоже в PHP. Так этот
// файл работает и на MySQL (сервер), и на SQLite — а значит его можно
// проверить локально перед выкладкой, чем первая версия похвастаться не могла:
// UTC_TIMESTAMP() - INTERVAL 7 DAY на SQLite просто падает.
$weekAgo = gmdate("Y-m-d H:i:s", time() - 7 * 86400);

$stmt = $pdo->prepare(
    "SELECT app_version, platform,
            COUNT(*)            AS players,
            SUM(attempts_total) AS attempts,
            MAX(updated_at)     AS last_sync,
            SUM(CASE WHEN updated_at >= ? THEN 1 ELSE 0 END) AS active_week
     FROM players
     WHERE attempts_total > 0
     GROUP BY app_version, platform"
);
$stmt->execute(array($weekAgo));

$groups = array();
foreach ($stmt->fetchAll() as $row) {
    $groups[] = array(
        "appVersion"  => $row["app_version"],          // null = сборка без отметки
        "platform"    => $row["platform"],
        "players"     => (int) $row["players"],
        "attempts"    => (int) $row["attempts"],
        "lastSyncAt"  => $row["last_sync"] ? str_replace(" ", "T", $row["last_sync"]) . "Z" : null,
        "activeWeek"  => (int) $row["active_week"],
    );
}

// Сначала те, кто версию не сообщил (самые старые сборки — именно они держат
// переезд), дальше по версии от новых к старым.
usort($groups, function ($a, $b) {
    if (($a["appVersion"] === null) !== ($b["appVersion"] === null)) {
        return $a["appVersion"] === null ? -1 : 1;
    }
    return strnatcmp((string) $b["appVersion"], (string) $a["appVersion"]);
});

json_out(array("ready" => true, "groups" => $groups));
