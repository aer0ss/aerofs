/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.base.ParamFactory;
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

    @Inject
    public AuthenticatorFactory(ACLNotificationPublisher aclPublisher, AuditClient auditClient)
    {
        _aclPublisher = aclPublisher;
        _auditClient = auditClient;
    }

    public Authenticator create()
    {
        IAuthority[] authorities;
        if (Identity.AUTHENTICATOR == Identity.Authenticator.EXTERNAL_CREDENTIAL) {
            authorities = new IAuthority[] { new LdapAuthority(new LdapConfiguration(), _aclPublisher, _auditClient),
                                             new LocalAuthority() };
        } else if (Identity.AUTHENTICATOR == Identity.Authenticator.OPENID) {
            authorities = new IAuthority[] {new OpenIdAuthority(), new LocalAuthority() };
        } else {
            authorities = new IAuthority[] { new LocalAuthority() };
        }
        return new Authenticator(authorities);
    }
}
