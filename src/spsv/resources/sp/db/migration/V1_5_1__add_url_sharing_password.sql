ALTER TABLE sp_url_sharing
    ADD COLUMN `us_hashed_password` BINARY(64) DEFAULT NULL,
    ADD COLUMN `us_password_salt` BINARY(16) DEFAULT NULL;  /* must match UrlShare.PASSWORD_SALT_LENGTH */
