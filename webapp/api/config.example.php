<?php
// Reference copy of what config.php looks for — see the big comment at the
// top of config.php for the full explanation. Two ways to provide the real
// credentials, either works:
//
// Option A — environment variables (if your hosting panel offers them):
//   POT_DB_HOST, POT_DB_PORT, POT_DB_NAME, POT_DB_USER, POT_DB_PASS
//
// Option B — an external file OUTSIDE the folder you upload/sync from your
// local machine, e.g. on the server:
//   /home/youraccount/pot-secrets/config.php   (a sibling of public_html,
//                                                NOT inside it)
// containing exactly:
//
//   <?php
//   return array(
//       "host" => "localhost",
//       "port" => 3306,
//       "name" => "infousil_pot",
//       "user" => "infousil_pot",
//       "pass" => "the real password",
//   );
//
// Create that file directly on the server (phpMyAdmin doesn't do this part —
// use your host's file manager or FTP, just don't upload it FROM this local
// project folder, or it stops being "outside the deploy" and defeats the
// point). config.php's `$externalSecretsPath` must point at wherever you
// actually put it.
//
// Caveat: a few very locked-down hosts restrict PHP from reading files
// outside the site's document root (an "open_basedir" restriction). If the
// external-file approach throws a permission error, check whether your
// panel has Environment Variables (Option A) instead, or ask your host
// whether pot-secrets/ can sit as a sibling of public_html within the same
// account (usually fine — the restriction is normally per-account, not
// per-folder).
