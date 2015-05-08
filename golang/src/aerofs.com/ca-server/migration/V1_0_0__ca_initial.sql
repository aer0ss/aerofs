CREATE TABLE IF NOT EXISTS `server_configuration` (
    `ca_key` BLOB(1280) NOT NULL,
    `ca_cert` BLOB(1280) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `signed_certificates` (
    `serial_number` BIGINT NOT NULL,
    `certificate` BLOB(1280) NOT NULL,
    PRIMARY KEY (`serial_number`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

