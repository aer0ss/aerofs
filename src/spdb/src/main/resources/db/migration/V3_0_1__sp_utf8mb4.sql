/*
 * Allow shared folder names to have 4-byte UTF-8 characters
 */
ALTER TABLE sp_shared_folder_names
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY sn_name varchar(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

/*
 * Allow first/last names to have 4-byte UTF-8 characters
 */
ALTER TABLE sp_user
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY u_first_name varchar(80)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY u_last_name varchar(80)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

/*
 * Allow device name to have 4-byte UTF-8 characters
 */
ALTER TABLE sp_device
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY d_name varchar(320)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

/*
 * Allow organization names to have 4-byte UTF-8 characters
 */
ALTER TABLE sp_organization
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY o_name varchar(80)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
