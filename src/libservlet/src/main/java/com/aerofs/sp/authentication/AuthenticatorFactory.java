/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.servlets.lib.analytics.IAnalyticsClient;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.sp.server.ACLNotificationPublisher;

import javax.inject.Inject;

/**
 * Create and configure an Authenticator instance for the current configured identity authorities.
 */
public class AuthenticatorFactory
{
    ACLNotificationPublisher _aclPublisher;
    AuditClient _auditClient;
    IAnalyticsClient _analyticsClient;

    @Inject
    public AuthenticatorFactory(ACLNotificationPublisher aclPublisher, AuditClient auditClient, IAnalyticsClient analyticsClient)
    {
        _aclPublisher = aclPublisher;
        _auditClient = auditClient;
        _analyticsClient = analyticsClient;
    }

    public Authenticator create()
    {
        IAuthority[] authorities;
        if (Identity.AUTHENTICATOR == Identity.Authenticator.EXTERNAL_CREDENTIAL) {
            authorities = new IAuthority[] {
                    new LdapAuthority(new LdapConfiguration(), _aclPublisher, _auditClient, _analyticsClient),
                    new LocalAuthority(),
            };
        } else if (Identity.AUTHENTICATOR == Identity.Authenticator.OPENID) {
            authorities = new IAuthority[] {
                    new OpenIdAuthority(),
                    new LocalAuthority(),
            };
        } else {
            authorities = new IAuthority[] {
                    new LocalAuthority()
            };
        }
        return new Authenticator(authorities);
    }
}
