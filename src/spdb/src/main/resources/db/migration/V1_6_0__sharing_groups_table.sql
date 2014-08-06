-- Migration to add required fields for group sharing. Step 1/4.

-- Sharing Groups Table: used to track group name & existence.
--
-- Fields:
--   sg_sig is a unique group identifier.
--   sg_common_name is the user-facing name of the group.
--   sg_org_id is the organization id. each group is associated with an organization.
--   sg_external_id is used to uniquely identify the group in 3rd party systems, specifically LDAP
--     and AD systems. 20 bytes because we use the SHA1 of the DN.
CREATE TABLE IF NOT EXISTS `sp_sharing_groups` (
    `sg_gid` INTEGER NOT NULL,
    `sg_common_name` VARCHAR(80) CHARSET utf8mb4 NOT NULL,
    `sg_org_id` INTEGER NOT NULL,
    `sg_external_id` BINARY(20) DEFAULT NULL,
    PRIMARY KEY (`sg_gid`),
    CONSTRAINT `sg_org_id_foreign` FOREIGN KEY (`sg_org_id`) REFERENCES `sp_organization` (`o_id`),
    INDEX `sg_org_id` (`sg_org_id`),
    INDEX `sg_common_name` (`sg_common_name`),
    INDEX `sg_external_id` (`sg_external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
