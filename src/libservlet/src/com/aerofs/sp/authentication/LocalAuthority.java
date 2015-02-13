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
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.sql.SQLException;

/**
 * Authentication authority that compares a credential value against the local user database.
 * If the hashed credential matches our stored hash for this user, continue;
 * otherwise throw ExBadCredential.
 */
class LocalAuthority implements IAuthority
{
    private static Logger l = LoggerFactory.getLogger(LocalAuthority.class);

    /**
     * Authenticate a user with an AeroFS-managed password.
     *
     * @param user User object, must have id()
     * @param credential Credential value - expects scrypt'ed if format==LEGACY
     * @param trans A transaction class that can be begin'ed and commit'ed.
     * @param format If LEGACY, then no server-side SCrypt will be performed.
     */
    @Override
    public void authenticateUser(User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans, CredentialFormat format)
            throws SQLException, ExBadCredential, GeneralSecurityException
    {
        byte[] credValue;
        if (format == Authenticator.CredentialFormat.TEXT) {
            credValue = LocalCredential.deriveKeyForUser(user.id(), credential);
        } else {
            Preconditions.checkState(format == Authenticator.CredentialFormat.LEGACY);
            l.info("checking legacy-formatted cred");
            credValue = credential;
        }

        trans.begin();
        user.throwIfBadCredential(LocalCredential.hashScrypted(credValue));
        trans.commit();
    }

    @Override
    public boolean isInternalUser(UserID userID)
    {
        return _internalAddressPattern.isInternalUser(userID);
    }

    @Override
    public void setACLPublisher(ACLNotificationPublisher aclPublisher) {}

    @Override
    public boolean managesLocalCredential() { return true; }

    @Override
    public boolean canAuthenticate(UserID userID) { return true; }

    @Override
    public String toString() { return "Credential"; }

    private AddressPattern _internalAddressPattern = new AddressPattern();
}
