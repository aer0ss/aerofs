/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.lib.user.User;
import com.unboundid.ldap.sdk.LDAPSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * An authenticator that will use the given LDAP authenticator for
 * users with LDAP credential, and fall back to the AeroFS-managed password authenticator
 * for all others.
 */
public class SwitchingAuthenticator implements IAuthenticator
{
    /**
     * @param ldap Authenticator for LDAP users.
     * @param local Authenticator for _external_ users.
     */
    public SwitchingAuthenticator(LdapAuthenticator ldap, LocalAuthenticator local)
    {
        _ldap = ldap;
        _local = local;
    }

    @Override
    public void authenticateUser(User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans, CredentialFormat format)
            throws Exception
    {
        IAuthenticator authenticator = _ldap.canAuthenticate(user) ? _ldap : _local;
        authenticator.authenticateUser(user, credential, trans, format);
        l.debug("switching auth ok {} for user {} ...", authenticator.toString(), user.id());
    }

    /** True if the user can be authenticated by LDAP; false otherwise. */
    @Override
    public boolean isAutoProvisioned(User user)
            throws ExExternalServiceUnavailable, LDAPSearchException
    {
        return _ldap.canAuthenticate(user);
    }

    private final LdapAuthenticator _ldap;
    private final LocalAuthenticator _local;
    private static Logger l = LoggerFactory.getLogger(SwitchingAuthenticator.class);
}
