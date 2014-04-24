-- Migration to add columns for per-user teamserver quota enforcement

ALTER TABLE `sp_user` ADD COLUMN `u_bytes_used` BIGINT;
ALTER TABLE `sp_user` ADD COLUMN `u_usage_warning_sent` BOOLEAN NOT NULL DEFAULT FALSE;

