/**
 * Initial Polaris database schema.
 */

ALTER SCHEMA DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;

/**
 * object store
 */

CREATE TABLE IF NOT EXISTS `objects` (
  `store_oid` BINARY(16) NOT NULL,
  `oid`       BINARY(16) NOT NULL,
  `version`   BIGINT     NOT NULL,
  `locked`    TINYINT    DEFAULT 0,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;

CREATE INDEX `store_index` ON `objects` (`store_oid`);

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

/**
 * there's a one-to-many relationship here from child_oid -> parent_oid
 */
CREATE TABLE IF NOT EXISTS `children` (
  `parent_oid` BINARY(16)      NOT NULL,
  `child_oid`  BINARY(16)      NOT NULL,
  `child_name` VARBINARY(1020) NOT NULL,
  `deleted`    TINYINT         DEFAULT 0,
  PRIMARY KEY (`child_oid`, `parent_oid`)
) ENGINE = InnoDB;

CREATE INDEX `child_name_index` ON `children` (`parent_oid`, `child_name`(520));

CREATE TABLE IF NOT EXISTS `transforms` (
  `logical_timestamp`      BIGINT AUTO_INCREMENT NOT NULL,
  `originator`             BINARY(16)            NOT NULL,
  `store_oid`              BINARY(16)            NOT NULL,
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

CREATE INDEX `store_transforms_index` ON `transforms` (`store_oid`);

/**
 * notification
 */

CREATE TABLE IF NOT EXISTS `store_max_logical_timestamp` (
  `store_oid`         BINARY(16) NOT NULL,
  `logical_timestamp` BIGINT     NOT NULL,
  PRIMARY KEY (`store_oid`)
) ENGINE = InnoDB;

CREATE INDEX `store_max_logical_timestamp_index` ON `store_max_logical_timestamp` (`store_oid`, `logical_timestamp`);

CREATE TABLE IF NOT EXISTS `store_notified_logical_timestamp` (
  `store_oid`         BINARY(16) NOT NULL,
  `logical_timestamp` BIGINT     NOT NULL,
  PRIMARY KEY (`store_oid`)
) ENGINE = InnoDB;

CREATE INDEX `store_notified_logical_timestamp_index` ON `store_notified_logical_timestamp` (`store_oid`, `logical_timestamp`);

/**
 * migration
 */

CREATE TABLE IF NOT EXISTS `store_migrations` (
  `store_oid`         BINARY(16)        NOT NULL,
  `originator`        BINARY(16)        NOT NULL,
  `job_status`        INTEGER           NOT NULL,
  PRIMARY KEY (`store_oid`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `user_mount_points` (
  `user_root`     BINARY(16)        NOT NULL,
  `mount_point`   BINARY(16)        NOT NULL,
  `mount_parent`  BINARY(16)        NOT NULL,
  PRIMARY KEY (`user_root`, `mount_point`)
) ENGINE = InnoDB;

/**
 * conversion from distributed system -> polaris
 */

CREATE TABLE IF NOT EXISTS `converted` (
  `oid`                      BINARY(16)   NOT NULL,
  `distributed_version_tick` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`oid`)
) ENGINE = InnoDB;
