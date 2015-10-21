-- Migration to rename field team_only to require_login for url sharing.
-- This field is a boolean value that dictates whether or not
-- the url requires a signed in user for access.

ALTER TABLE `sp_url_sharing` CHANGE COLUMN `us_team_only` `us_require_login` BOOLEAN NOT NULL DEFAULT FALSE;