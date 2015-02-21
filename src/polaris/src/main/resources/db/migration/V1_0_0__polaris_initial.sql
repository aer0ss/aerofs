/**
 * Initial Polaris database schema.
 */

ALTER SCHEMA `polaris` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `objects` (
  `root_oid` BINARY(16) NOT NULL,
  `oid`      BINARY(16) NOT NULL,
  `version`  BIGINT     NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;

CREATE INDEX `store_index` ON `objects` (`root_oid`);

CREATE TABLE IF NOT EXISTS `object_types` (
  `oid`         BINARY(16) NOT NULL,
  `object_type` INTEGER    NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `file_properties` (
  `oid`     BINARY(16) NOT NULL,
  `version` BIGINT     NOT NULL,
  `hash`    BINARY(32) NOT NULL,
  `size`    BIGINT     NOT NULL,
  `mtime`   BIGINT     NOT NULL,
  PRIMARY KEY (`oid`, `version`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `locations` (
  `oid`     BINARY(16) NOT NULL,
  `version` BIGINT     NOT NULL,
  `did`     BINARY(16) NOT NULL
) ENGINE = InnoDB;

CREATE INDEX `locations_index` ON `locations` (`oid`, `version`);

CREATE TABLE IF NOT EXISTS `children` (
  `parent_oid` BINARY(16)      NOT NULL,
  `child_oid`  BINARY(16)      NOT NULL,
  `child_name` VARBINARY(1020) NOT NULL
) ENGINE = InnoDB;

CREATE INDEX `parent_index`     ON `children` (`parent_oid`);
CREATE INDEX `child_name_index` ON `children` (`parent_oid`, `child_name`(520));

CREATE TABLE IF NOT EXISTS `transforms` (
  `logical_timestamp`      BIGINT AUTO_INCREMENT NOT NULL,
  `originator`             BINARY(16)            NOT NULL,
  `root_oid`               BINARY(16)            NOT NULL,
  `oid`                    BINARY(16)            NOT NULL,
  `transform_type`         INTEGER               NOT NULL,
  `new_version`            BIGINT                NOT NULL,
  `child_oid`              BINARY(16)            DEFAULT NULL,
  `child_name`             VARBINARY(1020)       DEFAULT NULL,
  `atomic_operation_id`    CHAR(32)              DEFAULT NULL,
  `atomic_operation_index` INT                   DEFAULT NULL,
  `atomic_operation_total` INT                   DEFAULT NULL,
  `timestamp`              BIGINT                NOT NULL,
  PRIMARY KEY (`logical_timestamp`)
) ENGINE = InnoDB;

CREATE INDEX `store_transforms_index` ON `transforms` (`root_oid`);

CREATE TABLE IF NOT EXISTS `converted` (
  `oid`                      BINARY(16)   NOT NULL,
  `distributed_version_tick` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;
