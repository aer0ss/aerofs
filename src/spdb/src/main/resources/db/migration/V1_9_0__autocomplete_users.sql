-- An additional user table for SP to fulfill autocomplete queries with
CREATE TABLE IF NOT EXISTS sp_autocomplete_users (
    `acu_email` VARCHAR(320) NOT NULL PRIMARY KEY,
    `acu_fullname` VARCHAR(160) CHARSET utf8 NOT NULL,
    `acu_lastname` VARCHAR(80) CHARSET utf8 NOT NULL,
    INDEX `acu_fullname` (`acu_fullname`),
    INDEX `acu_lastname` (`acu_lastname`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
