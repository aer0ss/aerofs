DROP TABLE `converted`;

/**
 * conversion from distributed system -> polaris
 */
CREATE TABLE IF NOT EXISTS `converted_ticks` (
    `oid`       BINARY(16) NOT NULL,
    `component` TINYINT NOT NULL,
    `did`       BINARY(16) NOT NULL,
    `tick`      BIGINT NOT NULL,
    PRIMARY KEY (`oid`, `component`, `did`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `aliases` (
    `alias`     BINARY(16) NOT NULL,
    `store`     BINARY(16) NOT NULL,
    `target`    BINARY(16) NOT NULL,
    PRIMARY KEY (`store`, `alias`)
) ENGINE = InnoDB;
