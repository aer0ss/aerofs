-- Migration to add a required enum for mandatory two-factor authentication at
-- the organization level

-- Note the default value of 1. 1 is OPT_IN.  0 is DISALLOWED.  2 is MANDATORY.
ALTER TABLE `sp_organization` ADD COLUMN `o_two_factor_enforcement_level` INTEGER NOT NULL DEFAULT 1;
