/**
 * Polaris Database Initial Schema.
 *
 * FIXME (AG): change all sid, oid, did to BINARY(32)
 */

CREATE SCHEMA IF NOT EXISTS `polaris` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `objects` (
  `root_oid` CHAR(32) NOT NULL,
  `oid`      CHAR(32) NOT NULL,
  `version`  BIGINT   NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `object_types` (
  `oid`         CHAR(32) NOT NULL,
  `object_type` INTEGER  NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `file_properties` (
  `oid`     CHAR(32) NOT NULL,
  `version` BIGINT   NOT NULL,
  `hash`    CHAR(64) NOT NULL,
  `size`    BIGINT   NOT NULL,
  `mtime`   BIGINT   NOT NULL,
  PRIMARY KEY (`oid`, `version`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `locations` (
  `oid`     CHAR(32) NOT NULL,
  `version` BIGINT   NOT NULL,
  `did`     CHAR(32) NOT NULL
) ENGINE = InnoDB;

CREATE INDEX `locations_index` ON `locations` (`oid`, `version`);

CREATE TABLE IF NOT EXISTS `children` (
  `oid`        CHAR(32)            NOT NULL,
  `child_oid`  CHAR(32)            NOT NULL,
  `child_name` VARCHAR(255)        NOT NULL
) ENGINE = InnoDB;

CREATE INDEX `parent_index` ON `children` (`oid`);

CREATE TABLE IF NOT EXISTS `transforms` (
  `logical_timestamp`      BIGINT AUTO_INCREMENT NOT NULL,
  `originator`             CHAR(32)              NOT NULL,
  `root_oid`               CHAR(32)              NOT NULL,
  `oid`                    CHAR(32)              NOT NULL,
  `transform_type`         INTEGER               NOT NULL,
  `new_version`            BIGINT                NOT NULL,
  `child_oid`              CHAR(32)              DEFAULT NULL,
  `child_name`             VARCHAR(255)          DEFAULT NULL,
  `atomic_operation_id`    CHAR(32)              DEFAULT NULL,
  `atomic_operation_index` INT                   DEFAULT NULL,
  `atomic_operation_total` INT                   DEFAULT NULL,
  `timestamp`              BIGINT                NOT NULL,
  PRIMARY KEY (`logical_timestamp`)
) ENGINE = InnoDB;

CREATE INDEX `root_oid_index` ON `transforms` (`root_oid`);

CREATE TABLE IF NOT EXISTS `converted` (
  `oid`                      CHAR(32)     NOT NULL,
  `distributed_version_tick` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;
