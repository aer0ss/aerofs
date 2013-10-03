/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.common.UserFilter;
import com.aerofs.sp.server.lib.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * An authenticator that will use the given LDAP authenticator for internal
 * users, and the given Local authenticator for external users.
 */
public class SwitchingAuthenticator implements IAuthenticator
{
    /**
     * @param ldap Authenticator for _internal_ users.
     * @param local Authenticator for _external_ users.
     */
    public SwitchingAuthenticator(LdapAuthenticator ldap, LocalAuthenticator local)
    {
        _ldap = ldap;
        _local = local;
        _filter = new UserFilter();
    }

    @Override
    public void authenticateUser(User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans)
            throws Exception
    {
        if (_filter.isInternalUser(user.id())) {
            _l.debug("Auth internal user {} ...", user.id());
            _ldap.authenticateUser(user, credential, trans);
        } else {
            _l.debug("Auth external user {} ...", user.id());
            _local.authenticateUser(user, credential, trans);
        }
    }

    private final LdapAuthenticator _ldap;
    private final LocalAuthenticator _local;
    private final UserFilter _filter;
    private static Logger _l = LoggerFactory.getLogger(SwitchingAuthenticator.class);
}
