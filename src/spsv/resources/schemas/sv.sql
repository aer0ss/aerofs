CREATE TABLE `sv_defect` (
  `def_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `def_desc` longtext NOT NULL,
  `def_auto` tinyint(1) NOT NULL,
  `def_cfg` text NOT NULL,
  `def_java_env` text NOT NULL,
  `hdr_client` text NOT NULL,
  `hdr_ts` bigint NOT NULL,
  `hdr_ver` varchar(16) NOT NULL,
  `hdr_user` text NOT NULL,
  `hdr_did` text NOT NULL,
  `hdr_approot` text NOT NULL,
  `hdr_rtroot` text NOT NULL,
  PRIMARY KEY (`def_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `sv_event` (
  `ev_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `ev_type` smallint NOT NULL,
  `ev_desc` text,
  `hdr_client` text NOT NULL,
  `hdr_ts` bigint NOT NULL,
  `hdr_ver` varchar(16) NOT NULL,
  `hdr_user` text NOT NULL,
  `hdr_did` text NOT NULL,
  `hdr_approot` text NOT NULL,
  `hdr_rtroot` text NOT NULL,
  PRIMARY KEY (`ev_id`),
  INDEX ev_type_idx (`ev_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
