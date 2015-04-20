CREATE TABLE IF NOT EXISTS `hibernate_sequence` (
  `ID` tinyint(4) NOT NULL,
  `next_val` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
INSERT IGNORE into hibernate_sequence (id,next_val) values (0, 100000);