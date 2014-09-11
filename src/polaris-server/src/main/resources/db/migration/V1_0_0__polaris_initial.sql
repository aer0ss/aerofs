/**
 * Polaris Database Initial Schema.
 *
 * FIXME (AG): change all sid, oid, did to BINARY(16)
 * FIXME (AG): define indexes
 */

CREATE TABLE IF NOT EXISTS `objects`(
  `root_oid`                 VARCHAR(16)  NOT NULL,
  `oid`                      VARCHAR(16)  NOT NULL,
  `version`                  BIGINT       NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `object_types`(
  `oid`                      VARCHAR(16)  NOT NULL,
  `object_type`              INTEGER      NOT NULL,
  PRIMARY KEY (oid))
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `file_properties`(
  `oid`                      VARCHAR(16)  NOT NULL,
  `version`                  BIGINT       NOT NULL,
  `hash`                     VARCHAR(16)  NOT NULL,
  `size`                     BIGINT       NOT NULL,
  `mtime`                    BIGINT       NOT NULL,
  PRIMARY KEY (oid, version))
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `locations`(
  `oid`                      VARCHAR(16)  NOT NULL,
  `version`                  BIGINT       NOT NULL,
  `did`                      VARCHAR(16)  NOT NULL,
  PRIMARY KEY (oid, version))
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `children`(
  `oid`                      VARCHAR(16)  NOT NULL,
  `child_oid`                VARCHAR(16)  NOT NULL,
  `child_name`               VARCHAR(255) NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `transforms`(
  `logical_timestamp`        BIGINT       AUTO_INCREMENT NOT NULL,
  `root_oid`                 VARCHAR(16)  NOT NULL               ,
  `oid`                      VARCHAR(16)  NOT NULL               ,
  `transform_type`           INTEGER      NOT NULL               ,
  `new_version`              BIGINT       NOT NULL               ,
  `child_oid`                VARCHAR(16)  DEFAULT NULL           ,
  `child_name`               VARCHAR(255) DEFAULT NULL           ,
  PRIMARY KEY (logical_timestamp))
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `converted`(
  `oid`                      VARCHAR(16)  NOT NULL,
  `distributed_version_tick` VARCHAR(255) NOT NULL,
  PRIMARY KEY (oid))
  ENGINE=InnoDB DEFAULT CHARSET=utf8;
