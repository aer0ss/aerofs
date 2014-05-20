-- Migration to add required fields for two-factor authentication

-- This field
ALTER TABLE `sp_user` ADD COLUMN `u_two_factor_enforced` BOOLEAN NOT NULL DEFAULT FALSE;

-- u_id is the user's email address
-- secret is the 10-byte, long-term second factor secret used with TOTP
-- setup_date is the date that this two-factor secret was created
CREATE TABLE `sp_two_factor_secret` (
    `tf_u_id` VARCHAR(320) NOT NULL,
    `tf_secret` BINARY(10) NOT NULL,
    `tf_setup_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`tf_u_id`),
    CONSTRAINT `tf_u_foreign` FOREIGN KEY (`tf_u_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


-- id is used to ensure consistent sort order
-- u_id is the user's email address
-- tfr_code is the actual recovery code the user needs to enter, ASCII
-- tfr_consumed is NULL if the code is still valid; if nonnull, the code was used at this time
CREATE TABLE `sp_two_factor_recovery` (
    `tfr_id` INTEGER NOT NULL AUTO_INCREMENT,
    `tfr_u_id` VARCHAR(320) NOT NULL,
    `tfr_code` VARCHAR(11) NOT NULL,
    `tfr_code_used_ts` DATETIME,
    PRIMARY KEY (`tfr_id`),
    INDEX `tfr_u_id` (`tfr_u_id`),
    CONSTRAINT `tfr_u_foreign` FOREIGN KEY (`tfr_u_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
