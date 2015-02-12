-- Migration to add required fields for group sharing. Step 4/4.

-- Sharing Group Shares Table: used to track group membership in shares.
--
-- Fields:
--   gs_gid: unique group identifier.
--   gs_sid: share identifier.
--   gs_role: group's role in the share (see ACL table for details on roles).
CREATE TABLE IF NOT EXISTS `sp_sharing_group_shares` (
    `gs_gid` INTEGER NOT NULL,
    `gs_sid` BINARY(16) NOT NULL,
    `gs_role` TINYINT NOT NULL,
    PRIMARY KEY (`gs_gid`, `gs_sid`),
    CONSTRAINT `gs_gid_foreign` FOREIGN KEY (`gs_gid`) REFERENCES `sp_sharing_groups` (`sg_gid`),
    CONSTRAINT `gs_sid_foreign` FOREIGN KEY (`gs_sid`) REFERENCES `sp_shared_folder` (`sf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
