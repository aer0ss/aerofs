-- Migration to support the per-user shared folder name feature.
-- In the past, there was a single name for each shared folder, meaning that if users renamed their
-- shared folders locally, the new name would no longer match what they saw on the web.
-- With this migration, we add a new table to store per-user names.

-- Drop the index on sf_name - it's useless
ALTER TABLE `sp_shared_folder` DROP INDEX `sf_name_idx`;

-- Rename `sf_name` to `sf_original_name`
ALTER TABLE `sp_shared_folder` CHANGE `sf_name` `sf_original_name` VARCHAR(255) CHARSET utf8 NOT NULL;

-- Create the `sp_shared_folder_names` table
CREATE TABLE IF NOT EXISTS `sp_shared_folder_names` (
  `sn_sid` BINARY(16) NOT NULL,
  `sn_user_id` VARCHAR(320) NOT NULL,
  `sn_name` VARCHAR(255) CHARSET utf8 NOT NULL,
  PRIMARY KEY (`sn_sid`, `sn_user_id`),
  CONSTRAINT `sn_sid_foreign` FOREIGN KEY (`sn_sid`) REFERENCES `sp_shared_folder` (`sf_id`) ON DELETE CASCADE,
  CONSTRAINT `sn_user_foreign` FOREIGN KEY (`sn_user_id`) REFERENCES `sp_user` (`u_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
