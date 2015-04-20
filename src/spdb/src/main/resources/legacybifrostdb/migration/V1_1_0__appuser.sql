INSERT INTO resourceserver
    (id, contactEmail, contactName, description,
     resourceServerKey, resourceServerName,
     owner, secret)
VALUES
    (999999, 'support@aerofs.com', 'AeroFS Support', 'AeroFS Resource Server',
     'oauth-havre', 'AeroFS Resource Server',
     'AeroFS', 'i-am-not-a-restful-secret');

INSERT INTO client
    (id, contactEmail, contactName, description,
     clientName, thumbNailUrl, resourceserver_id,
     clientId, secret, expireDuration,
     includePrincipal, skipConsent, allowedClientCredentials, allowedImplicitGrant, useRefreshTokens)
VALUES
    (999991, 'support@aerofs.com', 'AeroFS Support', 'AeroFS iOS Client',
     'AeroFS iOS Client', '', 999999,
     'aerofs-ios', 'this-is-not-an-ios-secret',
     0, 0, 0, 0, 0, 0);

INSERT INTO Client_redirectUris
    (CLIENT_ID, redirectUris)
VALUES
    (999991, 'aerofs://redirect');

INSERT INTO Client_scopes
    (CLIENT_ID, scopes)
VALUES
    (999991, 'readonly');