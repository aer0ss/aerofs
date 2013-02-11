CREATE TABLE `sp_organization` (
  -- We use random IDs instead of auto increment IDs only to prevent competitors from figuring out
  -- total number of orgs. It is NOT a security measure.
  `o_id` INTEGER NOT NULL, -- corresponding Java type: OrganizationID
  `o_name` VARCHAR(80) CHARSET utf8 NOT NULL, -- organization friendly name, displayed to the user.
                                              -- May include spaces and all.
  `o_contact_phone` VARCHAR(50), -- trying to leave enough room to handle international phone
                                 -- numbers we are not doing any very restrained validation
                                 -- on this input
  `o_stripe_customer_id` VARCHAR(255),  -- https://answers.stripe.com/questions/what-is-the-max-length-of-ids
                                        -- There's no well-defined maximum length on IDs, but VARCHAR(255) is certainly sufficient
  PRIMARY KEY (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Add the default organization
INSERT INTO `sp_organization` (`o_id`, `o_name`)
  VALUES (0, "Default Organization");

CREATE TABLE `sp_shared_folder` (
  `sf_id` BINARY(16) NOT NULL,
  `sf_name` VARCHAR(255) CHARSET utf8 NOT NULL,
  INDEX sf_name_idx (`sf_name`),
  PRIMARY KEY (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_acl` (
  `a_sid` BINARY(16) NOT NULL,
  `a_id` VARCHAR(320) NOT NULL,  -- TODO (WW) add a foreign key to user ids
  `a_role` TINYINT NOT NULL,
  `a_pending` BOOLEAN NOT NULL DEFAULT FALSE,
  `a_sharer` VARCHAR(320),
  PRIMARY KEY (`a_sid`,`a_id`),
  INDEX `a_sid` (`a_sid`),
  INDEX `a_id` (`a_id`),
  CONSTRAINT `a_sid_foreign` FOREIGN KEY (`a_sid`) REFERENCES `sp_shared_folder` (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1; -- latin1 because a_id is email address

CREATE TABLE `sp_user` (
  -- TODO rename u_id to u_email and most other 'ids' to 'email'
  `u_id` VARCHAR(320) NOT NULL, -- 320 is the maximum email address length
  `u_hashed_passwd` CHAR(44) NOT NULL,
  `u_id_created_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `u_first_name` VARCHAR(80) CHARSET utf8 NOT NULL, -- important UTF8
  `u_last_name` VARCHAR(80) CHARSET utf8 NOT NULL,  -- important UTF8
  `u_auth_level` INT UNSIGNED NOT NULL,
  `u_org_id` INTEGER NOT NULL,
  `u_acl_epoch` BIGINT NOT NULL,
  PRIMARY KEY (`u_id`),
  CONSTRAINT `u_org_foreign` FOREIGN KEY (`u_org_id`) REFERENCES `sp_organization` (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_device` (
  `d_id` CHAR(32) NOT NULL,
  -- TODO (MJ) why is d_id a CHAR(32) but sf_id (store id) is a BINARY(16)?
  `d_owner_id` VARCHAR(320) NOT NULL, -- this is the email address (u_id) in sp_user table
  `d_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `d_name` VARCHAR(320) NOT NULL,
  PRIMARY KEY (`d_id`),
  CONSTRAINT `d_name_owner` UNIQUE (`d_owner_id`, `d_name`),
  CONSTRAINT `d_owner_foreign` FOREIGN KEY (`d_owner_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_cert` (
  `c_serial` BIGINT UNSIGNED NOT NULL, -- the increasing serial number that identifies the
                                       -- certificate (generated by the CA).
  `c_device_id` VARCHAR(320) NOT NULL,
  `c_expire_ts` TIMESTAMP NOT NULL,
  `c_revoke_ts` TIMESTAMP, -- when the certificate has not been revoked, this is zero.
  PRIMARY KEY (`c_serial`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_signup_code` (
  `t_code` CHAR(8) NOT NULL,
  `t_to` VARCHAR(320) NOT NULL,
  `t_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`t_code`),
  INDEX `t_ts_idx` (`t_ts`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_password_reset_token` (
  `r_token` CHAR(44),
  `r_user_id` VARCHAR(320) NOT NULL,
  `r_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`r_token`),
  CONSTRAINT `r_user_foreign` FOREIGN KEY (`r_user_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_organization_invite` (
  `m_to` VARCHAR(320) NOT NULL, -- i.e. invitee
  `m_org_id` INTEGER NOT NULL,
  `m_from` VARCHAR(320), -- i.e. inviter
  `m_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`m_to`, `m_org_id`),
  INDEX `m_ts_idx` (`m_ts`),
  CONSTRAINT `m_org_foreign` FOREIGN KEY (`m_org_id`) REFERENCES `sp_organization` (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_email_subscriptions` (
  `es_token_id` CHAR(12) PRIMARY KEY NOT NULL,
  `es_email` VARCHAR(254) NOT NULL,   -- the actual max email length supported by RFC is 254 bytes
                                      -- we use 254 bytes here because the maximum key length size
                                      -- for mysql is 767 bytes, and 254*3 = 762 (for UTF8
                                      -- strings)
                                      -- TODO (WW) this length is not consistent with the length of
                                      -- email fields in other tables.
                                      -- TODO (WW) latin1 should be used instead as the charset.
  `es_subscription` INT NOT NULL,
  `es_last_emailed` TIMESTAMP NOT NULL,
  UNIQUE KEY(`es_email`,`es_subscription`),
  INDEX es_email_idx(`es_email`),
  INDEX es_subscription_idx(`es_subscription`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `sp_signup` (
  `s_idx` int(11) NOT NULL AUTO_INCREMENT,
  `s_email` varchar(320) NOT NULL,
  `s_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`s_idx`),
  UNIQUE KEY `s_email` (`s_email`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_enterprise_signup` (
  `e_email` varchar(320) CHARACTER SET latin1 NOT NULL,
  `e_full_name` varchar(320) NOT NULL,
  `e_phone` varchar(50) NOT NULL,
  `e_title` varchar(100) NOT NULL,
  `e_org` varchar(320) NOT NULL,
  `e_org_size` varchar(50) NOT NULL,
  `e_comment` text NOT NULL,
  `e_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
