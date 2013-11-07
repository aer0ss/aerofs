/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.sql.SQLException;

/**
 * Authenticator type that compares an scrypt'ed credential against the local user database.
 * If the credential matches our stored value for this user, continue; otherwise throw an
 * exception (ExBadCredential).
 */
public class LocalAuthenticator implements IAuthenticator
{
    private static Logger l = LoggerFactory.getLogger(LocalAuthenticator.class);

    /**
     * Authenticate a user with an AeroFS-managed password.
     * @param user User object, must have id()
     * @param credential Credential value - expects scrypt'ed if format==LEGACY
     * @param trans A transaction class that can be begin'ed and commit'ed.
     * @param format If LEGACY, then no server-side SCrypt will be performed.
     */
    @Override
    public void authenticateUser(
            User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans,
            CredentialFormat format)
            throws SQLException, ExBadCredential, GeneralSecurityException
    {
        byte[] credValue;
        if (format == CredentialFormat.TEXT)
        {
            credValue = LocalCredential.deriveKeyForUser(user.id(), credential);
        }
        else
        {
            Preconditions.checkState(format == CredentialFormat.LEGACY);
            l.info("checking legacy-formatted cred");
            credValue = credential;
        }

        trans.begin();
        user.throwIfBadCredential(LocalCredential.hashScrypted(credValue));
        trans.commit();
    }

    @Override
    public boolean isAutoProvisioned(User user)
    {
        // All users must go through standard signup workflow -- they are manually provisioned.
        return false;
    }
}
