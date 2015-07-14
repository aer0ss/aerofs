/**
 * drop the old table only because this is a pre 1.0 migration in polaris, and data loss is not a concern for this migration
 */

DROP TABLE IF EXISTS `store_migrations`;

CREATE TABLE IF NOT EXISTS `migration_jobs` (
  `migrant`           BINARY(16)      NOT NULL,
  `destination`       BINARY(16)      NOT NULL,
  `job_id`            BINARY(16)      NOT NULL,
  `originator`        BINARY(16)      NOT NULL,
  `job_status`        INTEGER         NOT NULL,
  PRIMARY KEY (`job_id`)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `migrating_object_oids` (
  `old_oid`           BINARY(16)      NOT NULL,
  `new_oid`           BINARY(16)      NOT NULL,
  `job_id`            BINARY(16)      NOT NULL,
  PRIMARY KEY (`old_oid`)
) ENGINE = InnoDB;
