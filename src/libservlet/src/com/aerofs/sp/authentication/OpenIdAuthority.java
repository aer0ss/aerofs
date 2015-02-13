/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.authentication.Authenticator.CredentialFormat;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.lib.user.User;

import java.sql.SQLException;

/**
 * Authentication authority for OpenID.
 *
 * This class will never perform credential verification; however, it will determine
 * whether a given user is internal, or has a locally-managed credential.
 *
 * The OpenIdAuthority is responsible for "internal" users, where this fact is determined
 * by the internal_email_pattern property. Internal users are assumed to use OpenId, and cannot
 * have a local credential.
 *
 * If internal_email_pattern is empty, all users are treated as internal, and no external users
 * with local credentials can exist.
 */
class OpenIdAuthority implements IAuthority
{
    /**
     * This class cannot perform credential authentication. Use SP.openIdSignIn mechanics.
     */
    @Override
    public void authenticateUser(User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans, CredentialFormat format)
            throws ExBadCredential
    {
        throw new ExBadCredential("Attempt to sign in to an OpenID account");
    }

    @Override
    public void setACLPublisher(ACLNotificationPublisher aclPublisher) {}

    @Override
    public boolean isInternalUser(UserID userID)
    {
        return _internalAddressPattern.isInternalUser(userID);
    }

    @Override
    public boolean canAuthenticate(UserID userID)
    {
        return isInternalUser(userID);
    }

    @Override
    public boolean managesLocalCredential() { return false; }

    @Override
    public String toString() { return "OpenId"; }

    private AddressPattern _internalAddressPattern = new AddressPattern();
}
