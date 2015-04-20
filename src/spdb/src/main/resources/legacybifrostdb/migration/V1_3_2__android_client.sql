INSERT INTO client
    (id, contactEmail, contactName, description,
     clientName, thumbNailUrl, resourceserver_id,
     clientId, secret, expireDuration,
     includePrincipal, skipConsent, allowedClientCredentials, allowedImplicitGrant, useRefreshTokens)
VALUES
    (999992, 'support@aerofs.com', 'AeroFS Support', 'AeroFS Android Client',
     'AeroFS Android Client', '', 999999,
     'aerofs-android', 'AeKPDAqi+oaECwhLWS5/MPR3R0wIM4v7',
     0, 0, 0, 0, 0, 0);

INSERT INTO Client_redirectUris
    (CLIENT_ID, redirectUris)
VALUES
    (999992, 'aerofs://redirect');

INSERT INTO Client_scopes
    (CLIENT_ID, scopes)
VALUES
    (999992, 'files.read'),
    (999992, 'files.write');
