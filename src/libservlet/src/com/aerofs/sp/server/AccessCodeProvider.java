package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.base.Loggers;
import com.aerofs.sp.server.lib.user.User;
import com.google.inject.Inject;

import org.slf4j.Logger;

public class AccessCodeProvider
{
    private static final Logger l = Loggers.getLogger(AccessCodeProvider.class);

    // Important: recall that IdentitySessionManager speaks seconds, not milliseconds,
    // due to the underlying key-expiration technology.
    private static final int TIMEOUT_SEC = 30;

    private final AuditClient               _auditClient;
    private final IdentitySessionManager    _identitySessionManager;

    @Inject
    public AccessCodeProvider(AuditClient auditClient,
                              IdentitySessionManager identitySessionManager)
    {
        _auditClient = auditClient;
        _identitySessionManager = identitySessionManager;
    }

    public String createAccessCodeForUser(User user)
    {
        l.info("Gen access code for {}", user.id());
        _auditClient.event(AuditClient.AuditTopic.DEVICE, "device.access.code")
                .add("user", user.id())
                .add("timeout", TIMEOUT_SEC)
                .publish();

        return  _identitySessionManager.createDeviceAuthorizationNonce(user, TIMEOUT_SEC);
    }
}
