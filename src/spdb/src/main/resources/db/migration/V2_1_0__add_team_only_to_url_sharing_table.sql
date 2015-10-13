-- Migration to add required field team_only for url sharing.
-- This field is a boolean value that dictates whether or not
-- the url is scoped to the team for authentication.

ALTER TABLE `sp_url_sharing` ADD COLUMN `us_team_only` BOOLEAN NOT NULL DEFAULT FALSE;