CREATE TABLE IF NOT EXISTS sp_settings_token (
    `st_uid` VARCHAR(320) NOT NULL PRIMARY KEY,
    `st_token` VARCHAR(255) NOT NULL /* must be same as bifrost.accesstoken.token */
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
