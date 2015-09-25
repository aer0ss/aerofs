--
-- Table structure for table `addresses`
--
CREATE TABLE `addresses` (
  `email` varchar(250) NOT NULL,
  `user_id` char(32) NOT NULL,
  PRIMARY KEY (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table `refresh_tokens`
--
CREATE TABLE `refresh_tokens` (
  `token` char(32) NOT NULL,
  `user_id` char(32) NOT NULL,
  `expiry` bigint(20) NOT NULL,
  PRIMARY KEY (`token`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table `auth_tokens`
--
CREATE TABLE `auth_tokens` (
  `token` char(32) NOT NULL,
  `refresh_token` char(32) NOT NULL,
  `expiry` bigint(20) NOT NULL,
  PRIMARY KEY (`token`),
  KEY `refresh_token` (`refresh_token`),
  CONSTRAINT `auth_tokens_ibfk_1` FOREIGN KEY (`refresh_token`) REFERENCES `refresh_tokens` (`token`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table `devices`
--
CREATE TABLE `devices` (
  `dev_id` char(16) NOT NULL,
  `user_id` char(32) NOT NULL,
  `dev_name` varchar(80) DEFAULT NULL,
  `dev_family` varchar(80) DEFAULT NULL,
  `push_type` enum('APNS','GCM','NONE') DEFAULT NULL,
  `push_token` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`dev_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table `verification_codes`
--
CREATE TABLE `verification_codes` (
  `email` varchar(254) NOT NULL,
  `code` char(32) NOT NULL,
  `created` bigint(20) DEFAULT NULL,
  UNIQUE KEY `email` (`email`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

