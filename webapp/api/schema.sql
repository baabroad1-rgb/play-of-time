-- Play of Time — leaderboard schema. Run this once in phpMyAdmin (or
-- whatever DB tool your hosting panel offers) against the empty database
-- you created for this. MySQL 5.7+ compatible (no CTEs, no window functions).

CREATE TABLE IF NOT EXISTS players (
  id CHAR(36) NOT NULL,               -- client-generated UUID, the leaderboard row key
  seq INT UNSIGNED NOT NULL AUTO_INCREMENT, -- join order, used in the default "playerNNN_join_..." name
  sync_token CHAR(32) NOT NULL,       -- paired secret; proves later requests are the same device
  display_name VARCHAR(40) NULL,
  level_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
  task_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
  sc_total DOUBLE NOT NULL DEFAULT 0,
  attempts_total INT UNSIGNED NOT NULL DEFAULT 0,
  wins_total INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_seq (seq),
  KEY idx_attempts_total (attempts_total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  player_id CHAR(36) NOT NULL,
  local_id INT UNSIGNED NOT NULL,     -- the attempt's id in the player's own local db (dedupe key)
  session_id INT UNSIGNED NOT NULL,
  level_id TINYINT UNSIGNED NOT NULL,
  task_id TINYINT UNSIGNED NOT NULL,
  accuracy TINYINT UNSIGNED NOT NULL,
  duration_ms INT NOT NULL,
  error_ms INT NOT NULL,
  is_win TINYINT UNSIGNED NOT NULL DEFAULT 0,
  tolerance_ms INT NOT NULL DEFAULT 0,
  sc DOUBLE NOT NULL DEFAULT 0,
  recorded_at DATETIME NOT NULL,
  recorded_ms BIGINT NULL,            -- exact start time, Unix ms (added by sync.php self-migration)
  PRIMARY KEY (id),
  UNIQUE KEY uniq_player_attempt (player_id, local_id),
  CONSTRAINT fk_attempts_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
