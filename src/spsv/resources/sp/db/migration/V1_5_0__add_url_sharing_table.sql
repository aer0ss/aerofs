CREATE TABLE IF NOT EXISTS sp_url_sharing (
    `us_key` CHAR(32) NOT NULL PRIMARY KEY,
    `us_created_by` VARCHAR(320) NOT NULL,
    `us_sid` BINARY(16) NOT NULL,
    `us_oid` BINARY(16) NOT NULL,
    `us_token` VARCHAR(255) NOT NULL, /* must be same as bifrost.accesstoken.token */
    `us_expires` BIGINT(20) DEFAULT NULL,
    CONSTRAINT `us_sid_fk` FOREIGN KEY (`us_sid`) REFERENCES `sp_shared_folder` (`sf_id`) ON DELETE CASCADE
);
