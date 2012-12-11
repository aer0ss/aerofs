--
-- Migration required to update existing SIDs to comply with the new OID->SID
-- generation algorithm and the structural constraints it imposes on SIDs
--

--
-- Rename old table containing SIDs
--   * due to foreign key constraints we can't just update SIDs in place
--   * to avoid irrecoverable data loss we want to keep the old data around in
--   case we somehow overlook a corner case during testing
--
rename table
    sp_shared_folder to bak_sp_shared_folder,
    sp_shared_folder_code to bak_sp_shared_folder_code,
    sp_acl to bak_sp_acl;

--
-- Recreate tables
-- NB: we need to change constraint names or MySQL will fail with error 121
--
CREATE TABLE `sp_shared_folder` (
  `sf_id` BINARY(16) NOT NULL,
  `sf_name` VARCHAR(255) CHARSET utf8 NOT NULL,
  INDEX sf_name_idx (`sf_name`),
  PRIMARY KEY (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_shared_folder_code` (
  `f_code` CHAR(8) NOT NULL,
  `f_from` VARCHAR(320) NOT NULL,
  `f_to` VARCHAR(320) NOT NULL,
  `f_share_id` BINARY(16) NOT NULL,
  `f_folder_name` VARCHAR(255) CHARSET utf8 NOT NULL, -- important UTF8
  `f_ts` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `f_role` TINYINT NOT NULL DEFAULT 1,  -- 1 is Role.EDITOR
  INDEX folder_to_idx (`f_to`),
  PRIMARY KEY (`f_code`),
  CONSTRAINT `sfc_from_foreign` FOREIGN KEY (`f_from`) REFERENCES `sp_user` (`u_id`),
  CONSTRAINT `sfc_sid_foreign` FOREIGN KEY (`f_share_id`) REFERENCES `sp_shared_folder` (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `sp_acl` (
  `a_sid` BINARY(16) NOT NULL,
  `a_id` VARCHAR(320) NOT NULL,
  `a_role` TINYINT NOT NULL,
  PRIMARY KEY (`a_sid`,`a_id`),
  KEY `a_sid` (`a_sid`),
  KEY `a_id` (`a_id`),
  CONSTRAINT `acl_sid_foreign` FOREIGN KEY (`a_sid`) REFERENCES `sp_shared_folder` (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Migrate data: the convoluted expression for SID simply resets 4 bits out of
-- 128 to comply with the structural constraints enforced by the new OID->SID
-- generation algorithm (see SID.java for details)
--
insert into sp_shared_folder(sf_id, sf_name)
    select
        concat(
            substr(sf_id, 1, 6),
            char((ascii(substr(sf_id, 7, 1)) & 0x0f)),
            substr(sf_id, 8, 9)
        ),
        sf_name
    from bak_sp_shared_folder;

insert into sp_shared_folder_code(f_code, f_from, f_to, f_share_id, f_folder_name, f_ts, f_role)
    select
        f_code,
        f_from,
        f_to,
        concat(
            substr(f_share_id, 1, 6),
            char((ascii(substr(f_share_id, 7, 1)) & 0x0f)),
            substr(f_share_id, 8, 9)
        ),
        f_folder_name,
        f_ts,
        f_role
    from bak_sp_shared_folder_code;

insert into sp_acl(a_sid, a_id, a_role)
    select
        concat(
            substr(a_sid, 1, 6),
            char((ascii(substr(a_sid, 7, 1)) & 0x0f)),
            substr(a_sid, 8, 9)
        ),
        a_id,
        a_role
    from bak_sp_acl;
