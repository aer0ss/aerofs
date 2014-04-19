-- Migration to support the per-org teamserver quota
ALTER TABLE `sp_organization` ADD o_quota_per_user BIGINT;

