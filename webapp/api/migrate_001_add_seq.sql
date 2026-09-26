-- One-time migration for a database that already had schema.sql run against
-- it before the "seq" column existed. Safe to run once in phpMyAdmin. If you
-- created your database fresh with the current schema.sql, you don't need
-- this — seq is already there.
ALTER TABLE players ADD COLUMN seq INT UNSIGNED NOT NULL AUTO_INCREMENT UNIQUE AFTER id;
