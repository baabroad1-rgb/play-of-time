<?php
// Safe-to-deploy on purpose: this file NEVER contains the real DB password,
// so uploading/overwriting it (FTP, file manager, whatever) can never break
// or leak production credentials. Real values come from one of two places,
// checked in order:
//
//   1. Environment variables (POT_DB_HOST/PORT/NAME/USER/PASS) — if your
//      hosting panel has an "Environment Variables" section, use that.
//
//   2. An external secrets file that lives OUTSIDE this project folder on
//      the server — i.e. somewhere you never upload to from your local
//      copy, so it survives every deploy untouched. $externalSecretsPath
//      below points a few levels up from this file; adjust it ONCE on the
//      server to a real folder in your hosting account that sits outside
//      whatever you FTP/upload (e.g. a sibling of your public_html, not
//      inside it). Create that file ONLY on the server — never locally —
//      with this exact shape:
//
//        <?php
//        return array(
//            "host" => "localhost",
//            "port" => 3306,
//            "name" => "infousil_pot",
//            "user" => "infousil_pot",
//            "pass" => "the real password",
//        );
//
// If neither is set up yet, this returns a harmless placeholder — the API
// will fail its DB connection loudly (easy to notice) instead of silently
// using wrong credentials.
//
// See config.example.php and README.md in this folder for setup notes.

$externalSecretsPath = dirname(__DIR__, 3) . '/pot-secrets/config.php';
// ^ Alex: on the server, point this at a real folder outside the uploaded
// webapp folder (outside public_html or wherever this site's files live),
// then drop the file above at that path. dirname(__DIR__, 3) = three levels
// above this file (api/ -> webapp/ -> whatever contains it) — adjust the "3"
// if your actual folder depth on the server differs.

if (getenv("POT_DB_HOST") !== false || getenv("POT_DB_PASS") !== false) {
    return array(
        "host" => getenv("POT_DB_HOST") ?: "localhost",
        "port" => getenv("POT_DB_PORT") ?: 3306,
        "name" => getenv("POT_DB_NAME") ?: "infousil_pot",
        "user" => getenv("POT_DB_USER") ?: "infousil_pot",
        "pass" => getenv("POT_DB_PASS") ?: "",
    );
}

if (is_readable($externalSecretsPath)) {
    return require $externalSecretsPath;
}

return array(
    "host" => "localhost",
    "port" => 3306,
    "name" => "infousil_pot",
    "user" => "infousil_pot",
    "pass" => "REPLACE_ME__NO_REAL_SECRET_IS_DEPLOYED_FROM_HERE",
);
