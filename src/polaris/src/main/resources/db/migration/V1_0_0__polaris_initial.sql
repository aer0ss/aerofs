/**
 * Polaris Database Initial Schema.
 *
 * FIXME (AG): change all sid, oid, did to BINARY(16)
 */

CREATE TABLE IF NOT EXISTS `objects`(
  `root_oid`       VARCHAR(16)  NOT NULL,
  `oid`            VARCHAR(16)  NOT NULL,
  `version`        BIGINT       NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `object_types`(
  `oid`            VARCHAR(16)  NOT NULL,
  `object_type`    INTEGER      NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `metadata`(
  `oid`             VARCHAR(16) NOT NULL,
  `version`         BIGINT      NOT NULL,
  `hash`            VARCHAR(16) NOT NULL,
  `size`            BIGINT      NOT NULL,
  `mtime`           BIGINT      NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `locations`(
  `oid`            VARCHAR(16)  NOT NULL,
  `version`        BIGINT       NOT NULL,
  `did`            VARCHAR(16)  NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `children`(
  `oid`            VARCHAR(16)  NOT NULL,
  `child_oid`      VARCHAR(16)  NOT NULL,
  `child_name`     VARCHAR(255) NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `transforms`(
  `change_id`      BIGINT       AUTO_INCREMENT NOT NULL,
  `root_oid`       VARCHAR(16)  NOT NULL,
  `oid`            VARCHAR(16)  NOT NULL,
  `transform_type` INTEGER      NOT NULL,
  `new_version`    BIGINT       NOT NULL,
  `child_oid`      VARCHAR(16)          ,
  `child_name`     VARCHAR(255)         ,
  PRIMARY KEY(change_id))
  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `converted`(
  `oid`                      VARCHAR(16)  NOT NULL,
  `distributed_version_tick` VARCHAR(255) NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8;
