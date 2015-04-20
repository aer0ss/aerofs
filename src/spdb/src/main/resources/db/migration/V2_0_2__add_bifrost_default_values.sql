-- adapted from:
-- bifrost V1_1_0__appuser.sql
-- bifrost V1_1_1__resource_server_scopes.sql
-- bifrost V1_2_1__write_permission.sql
-- bifrost V1_3_0__mdid.sql had only ALTER TABLEs which were flattened into V2_0_0
-- bifrost V1_3_1__scopes.sql
-- bifrost V1_3_2__android_client.sql

-- We use REPLACE INTO because this migration is meant to run *after* the data
-- import from the former bifrost DB, which may include additional rows in the
-- client, Client_redirectUris, and Client_scopes tables.  However, this may
-- also be running on a clean install, in which case we need to add these rows
-- if they don't exist.

-- We could also have used INSERT IGNORE here, but that ignores *all* insertion
-- failures, whereas REPLACE INTO merely ensures that the rows are deleted
-- before they are added again.

-- Set up resource server, and scopes on it.
REPLACE INTO resourceserver
    (id, contactEmail, contactName, description,
     resourceServerKey, resourceServerName,
     owner, secret)
VALUES
    (999999, 'support@aerofs.com', 'AeroFS Support', 'AeroFS Resource Server',
     'oauth-havre', 'AeroFS Resource Server',
     'AeroFS', 'i-am-not-a-restful-secret');

REPLACE INTO ResourceServer_scopes
    (RESOURCESERVER_ID, scopes)
VALUES
    (999999, 'files.read'),
    (999999, 'files.write');



-- Set up iOS client, redirect, and client scopes.
REPLACE INTO client
    (id, contactEmail, contactName, description,
     clientName, thumbNailUrl, resourceserver_id,
     clientId, secret, expireDuration,
     includePrincipal, skipConsent, allowedClientCredentials, allowedImplicitGrant, useRefreshTokens)
VALUES
    (999991, 'support@aerofs.com', 'AeroFS Support', 'AeroFS iOS Client',
     'AeroFS iOS Client', '', 999999,
     'aerofs-ios', 'this-is-not-an-ios-secret',
     0, 0, 0, 0, 0, 0);

REPLACE INTO Client_redirectUris
    (CLIENT_ID, redirectUris)
VALUES
    (999991, 'aerofs://redirect');

REPLACE INTO Client_scopes
    (CLIENT_ID, scopes)
VALUES
    (999991, 'files.read'),
    (999991, 'files.write');



-- Set up Android app client, redirect, and client scopes.
REPLACE INTO client
    (id, contactEmail, contactName, description,
     clientName, thumbNailUrl, resourceserver_id,
     clientId, secret, expireDuration,
     includePrincipal, skipConsent, allowedClientCredentials, allowedImplicitGrant, useRefreshTokens)
VALUES
    (999992, 'support@aerofs.com', 'AeroFS Support', 'AeroFS Android Client',
     'AeroFS Android Client', '', 999999,
     'aerofs-android', 'AeKPDAqi+oaECwhLWS5/MPR3R0wIM4v7',
     0, 0, 0, 0, 0, 0);

REPLACE INTO Client_redirectUris
    (CLIENT_ID, redirectUris)
VALUES
    (999992, 'aerofs://redirect');

REPLACE INTO Client_scopes
    (CLIENT_ID, scopes)
VALUES
    (999992, 'files.read'),
    (999992, 'files.write');
