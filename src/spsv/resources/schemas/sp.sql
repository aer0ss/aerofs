CREATE TABLE `sp_organization` (
  `o_id` VARCHAR(80) NOT NULL, -- we use the organization's domain name as an id
  `o_name` VARCHAR(80) CHARSET utf8 NOT NULL, -- organization friendly name, displayed to the user. May include spaces and all.
  `o_allowed_domain` VARCHAR(80) NOT NULL DEFAULT "", -- default domain is empty string, no accepted domain
  `o_open_sharing` BOOLEAN NOT NULL, -- whether sharing folders with external organizations is allowed
  INDEX org_name_idx (`o_name`),
  PRIMARY KEY (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Add the consumer AeroFS organization
INSERT INTO `sp_organization` (`o_id`, `o_name`, `o_allowed_domain`, `o_open_sharing`)
  VALUES ("consumer.aerofs.com", "Consumer AeroFS", "*", TRUE);

CREATE TABLE `sp_shared_folder` (
  `sf_id` BINARY(16) NOT NULL,
  `sf_name` VARCHAR(255) CHARSET utf8 DEFAULT NULL,
  -- todo: When sp.SetACL is moved into sp.ShareFolder, make sf_name NOT NULL
  INDEX sf_name_idx (`sf_name`),
  PRIMARY KEY (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_acl` (
  `a_sid` BINARY(16) NOT NULL,
  `a_id` VARCHAR(320) NOT NULL,
  `a_role` TINYINT NOT NULL,
  PRIMARY KEY (`a_sid`,`a_id`),
  KEY `a_sid` (`a_sid`),
  KEY `a_id` (`a_id`),
  CONSTRAINT `a_sid_foreign` FOREIGN KEY (`a_sid`) REFERENCES `sp_shared_folder` (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1; -- latin1 because a_id is email address

CREATE TABLE `sp_user` (
  -- TODO rename u_id to u_email and most other 'ids' to 'email'
  `u_id` VARCHAR(320) NOT NULL, -- 320 is the maximum email address length
  `u_hashed_passwd` CHAR(44) NOT NULL,
  `u_finalized` BOOLEAN NOT NULL DEFAULT FALSE, -- whether the user completed setup
  `u_verified` BOOLEAN NOT NULL DEFAULT FALSE, -- whether the email address is verified
  `u_id_created_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `u_storeless_invites_quota` INTEGER NOT NULL DEFAULT 2, -- this should be consistent with the value set in CfgDatabase. TODO let Java code set the column's value
  `u_first_name` VARCHAR(80) CHARSET utf8 NOT NULL, -- important UTF8
  `u_last_name` VARCHAR(80) CHARSET utf8 NOT NULL,  -- important UTF8
  `u_auth_level` INT UNSIGNED NOT NULL,
  `u_org_id` VARCHAR(80) NOT NULL,
  `u_acl_epoch` BIGINT NOT NULL,
  PRIMARY KEY (`u_id`),
  CONSTRAINT `u_org_foreign` FOREIGN KEY (`u_org_id`) REFERENCES `sp_organization` (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_device` (
  `d_id` CHAR(32) NOT NULL,
  -- todo (mj) why is d_id a CHAR(32) but sf_id (store id) is a BINARY(16)?
  `d_owner_id` VARCHAR(320) NOT NULL, -- this is the email address (u_id) in sp_user table
  `d_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `d_name` VARCHAR(320) NOT NULL,
  PRIMARY KEY (`d_id`),
  CONSTRAINT `d_owner_foreign` FOREIGN KEY (`d_owner_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_cert` (
  `c_serial` BIGINT UNSIGNED NOT NULL, -- the increasing serial number that identifies the certificate (generated by the CA).
  `c_device_id` VARCHAR(320) NOT NULL,
  `c_expire_ts` TIMESTAMP NOT NULL,
  `c_revoke_ts` TIMESTAMP, -- when the certificate has not been revoked, this is null.
  PRIMARY KEY (`c_serial`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_signup_code` (
  `t_code` CHAR(8) NOT NULL,
  `t_from` VARCHAR(320), -- used to be null if the invites was sent by aerofs
  `t_to` VARCHAR(320) NOT NULL,
  `t_org_id` VARCHAR(80) NOT NULL,
  `t_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`t_code`),
  CONSTRAINT `t_org_foreign` FOREIGN KEY (`t_org_id`) REFERENCES `sp_organization` (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_password_reset_token` (
  `r_token` CHAR(44),
  `r_user_id` VARCHAR(320) NOT NULL,
  `r_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`r_token`),
  CONSTRAINT `r_user_foreign` FOREIGN KEY (`r_user_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_batch_signup_code` (
  `b_idx` INT NOT NULL AUTO_INCREMENT,
  `b_code` CHAR(8) NOT NULL,
  `b_user` VARCHAR(320) DEFAULT NULL, -- should probably be unique
  -- todo: b_org_id field here
  `b_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- todo: CONSTRAINT `b_org_foreign` FOREIGN KEY (`b_org_id`) REFERENCES `sp_organization` (`o_id`),
  PRIMARY KEY (`b_idx`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_shared_folder_code` (
  `f_code` CHAR(8) NOT NULL,
  `f_from` VARCHAR(320) NOT NULL,
  `f_to` VARCHAR(320) NOT NULL,
  `f_share_id` BINARY(16) NOT NULL,
  `f_folder_name` VARCHAR(255) CHARSET utf8 NOT NULL, -- important UTF8
  `f_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX folder_to_idx (`f_to`),
  PRIMARY KEY (`f_code`),
  CONSTRAINT `f_from_foreign` FOREIGN KEY (`f_from`) REFERENCES `sp_user` (`u_id`),
  CONSTRAINT `f_sid_foreign` FOREIGN KEY (`f_share_id`) REFERENCES `sp_shared_folder` (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_email_subscriptions` (
    `es_id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
    `es_email` VARCHAR(254) NOT NULL,
    `es_subscription` INT NOT NULL,
    PRIMARY KEY(`es_id`),
    INDEX es_email_idx(`es_email`),
    INDEX es_subscription_idx(`es_subscription`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `sp_email_reminders` (
    `er_id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
    `er_email` VARCHAR(254) NOT NULL,
    `er_category` VARCHAR(64) NOT NULL,
    `er_first_email_sent` TIMESTAMP NOT NULL,
    `er_last_email_sent` TIMESTAMP NOT NULL,
    `er_remind` BOOLEAN NOT NULL,
    PRIMARY KEY(`er_id`),
    KEY(`er_email`,`er_category`),
    INDEX er_first_email_idx(`er_first_email_sent`),
    INDEX er_last_email_idx(`er_last_email_sent`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DELIMITER //
CREATE PROCEDURE accountreset(userid varchar(320))
DETERMINISTIC MODIFIES SQL DATA
 BEGIN
    DECLARE EXIT HANDLER FOR SQLSTATE '42000'
      SELECT 'Invalid account name.';

    IF ((SELECT COUNT(*) FROM SP_USER where u_id=userid) != 1) THEN CALL raise_error;
    end if;

    DELETE FROM sp_user WHERE u_id = userid;
 END //

DELIMITER ;
