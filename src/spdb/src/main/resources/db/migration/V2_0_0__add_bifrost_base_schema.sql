-- V1_0_0__bifrost_initial.sql
-- modified to flatten ALTER TABLEs from migrations up to v1.3.2 into this schema
CREATE TABLE IF NOT EXISTS `AbstractEntity` (
  `id` bigint(20) NOT NULL,
  `creationDate` datetime DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `DTYPE` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `I_BSTRTTY_DTYPE` (`DTYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `AccessToken_scopes` (
  `ACCESSTOKEN_ID` bigint(20) DEFAULT NULL,
  `scopes` varchar(255) DEFAULT NULL,
  KEY `I_CCSSCPS_ACCESSTOKEN_ID` (`ACCESSTOKEN_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `AuthorizationRequest_grantedScopes` (
  `AUTHORIZATIONREQUEST_ID` bigint(20) DEFAULT NULL,
  `grantedScopes` varchar(255) DEFAULT NULL,
  KEY `I_THRZCPS_AUTHORIZATIONREQUEST_ID` (`AUTHORIZATIONREQUEST_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `AuthorizationRequest_requestedScopes` (
  `AUTHORIZATIONREQUEST_ID` bigint(20) DEFAULT NULL,
  `requestedScopes` varchar(255) DEFAULT NULL,
  KEY `I_THRZCPS_AUTHORIZATIONREQUEST_ID1` (`AUTHORIZATIONREQUEST_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `Client_redirectUris` (
  `CLIENT_ID` bigint(20) DEFAULT NULL,
  `redirectUris` varchar(255) DEFAULT NULL,
  KEY `I_CLNTTRS_CLIENT_ID` (`CLIENT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `Client_scopes` (
  `CLIENT_ID` bigint(20) DEFAULT NULL,
  `scopes` varchar(255) DEFAULT NULL,
  KEY `I_CLNTCPS_CLIENT_ID` (`CLIENT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `ResourceServer_scopes` (
  `RESOURCESERVER_ID` bigint(20) DEFAULT NULL,
  `scopes` varchar(255) DEFAULT NULL,
  KEY `I_RSRCCPS_RESOURCESERVER_ID` (`RESOURCESERVER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `accesstoken` (
  `id` bigint(20) NOT NULL,
  `creationDate` datetime DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `encodedPrincipal` TEXT DEFAULT NULL,
  `expires` bigint(20) DEFAULT NULL,
  `refreshToken` varchar(255) DEFAULT NULL,
  `resourceOwnerId` varchar(255) DEFAULT NULL,
  `token` varchar(255) DEFAULT NULL,
  `client_id` bigint(20) NOT NULL,
  `owner` VARCHAR(320) NOT NULL DEFAULT '-',
  `mdid` CHAR(32) UNIQUE NOT NULL DEFAULT '88888888888888888888888888888888',
  PRIMARY KEY (`id`),
  UNIQUE KEY `U_CCSSTKN_REFRESHTOKEN` (`refreshToken`),
  UNIQUE KEY `U_CCSSTKN_TOKEN` (`token`),
  KEY `I_CCSSTKN_CLIENT` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `authorizationrequest` (
  `id` bigint(20) NOT NULL,
  `creationDate` datetime DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `authorizationCode` varchar(255) DEFAULT NULL,
  `encodedPrincipal` TEXT DEFAULT NULL,
  `redirectUri` varchar(255) DEFAULT NULL,
  `responseType` varchar(255) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `client_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `I_THRZQST_CLIENT` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `client` (
  `id` bigint(20) NOT NULL,
  `creationDate` datetime DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `clientId` varchar(255) DEFAULT NULL,
  `contactEmail` varchar(255) DEFAULT NULL,
  `contactName` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `expireDuration` bigint(20) DEFAULT NULL,
  `clientName` varchar(255) DEFAULT NULL,
  `allowedImplicitGrant` bit(1) DEFAULT NULL,
  `allowedClientCredentials` bit(1) DEFAULT NULL,
  `secret` varchar(255) DEFAULT NULL,
  `skipConsent` bit(1) DEFAULT NULL,
  `includePrincipal` bit(1) DEFAULT NULL,
  `thumbNailUrl` varchar(255) DEFAULT NULL,
  `useRefreshTokens` bit(1) DEFAULT NULL,
  `resourceserver_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `U_CLIENT_CLIENTID` (`clientId`),
  KEY `I_CLIENT_RESOURCESERVER` (`resourceserver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `client_attributes` (
  `client_id` bigint(20) DEFAULT NULL,
  `attribute_name` varchar(255) NOT NULL,
  `attribute_value` varchar(255) DEFAULT NULL,
  KEY `I_CLNTBTS_CLIENT_ID` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `resourceserver` (
  `id` bigint(20) NOT NULL,
  `creationDate` datetime DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `contactEmail` varchar(255) DEFAULT NULL,
  `contactName` varchar(255) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `resourceServerKey` varchar(255) DEFAULT NULL,
  `resourceServerName` varchar(255) DEFAULT NULL,
  `owner` varchar(255) DEFAULT NULL,
  `secret` varchar(255) NOT NULL,
  `thumbNailUrl` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `U_RSRCRVR_KEY` (`resourceServerKey`),
  UNIQUE KEY `U_RSRCRVR_OWNER` (`owner`,`resourceServerName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- from V1_0_1__hibernate_sequence.sql :
CREATE TABLE IF NOT EXISTS `hibernate_sequence` (
  `ID` tinyint(4) NOT NULL,
  `next_val` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
INSERT IGNORE into hibernate_sequence (id,next_val) values (0, 100000);
