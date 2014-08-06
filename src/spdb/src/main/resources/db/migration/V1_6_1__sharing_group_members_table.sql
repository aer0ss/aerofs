-- Migration to add required fields for group sharing. Step 3/4.

-- Sharing Group Members Table: used to track user membership in groups.
--
-- Fields:
--   gm_gid: unique group identifier.
--   gm_member_id: user identifier.
CREATE TABLE IF NOT EXISTS `sp_sharing_group_members` (
    `gm_gid` INTEGER NOT NULL,
    `gm_member_id` VARCHAR(320) NOT NULL,
    PRIMARY KEY (`gm_gid`, `gm_member_id`),
    CONSTRAINT `gm_gid_foreign` FOREIGN KEY (`gm_gid`) REFERENCES `sp_sharing_groups` (`sg_gid`),
    CONSTRAINT `gm_member_id_foreign` FOREIGN KEY (`gm_member_id`) REFERENCES `sp_user` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
