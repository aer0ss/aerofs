/**
 * Simple Database Initial Schema.
 */

CREATE TABLE IF NOT EXISTS `customers` (
  `customer_id`       INTEGER  AUTO_INCREMENT NOT NULL,
  `customer_name`     CHAR(32) NOT NULL,
  `organization_name` CHAR(32) NOT NULL,
  `seats`             INTEGER  NOT NULL,
  PRIMARY KEY (`customer_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8;

CREATE INDEX `customer_name_index` ON `customers` (`customer_name`);
