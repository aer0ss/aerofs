/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;

import java.sql.SQLException;

/**
 * Authenticator type that compares an scrypt'ed credential against the local user database.
 * If the credential matches our stored value for this user, continue; otherwise throw an
 * exception (ExBadCredential).
 */
public class LocalAuthenticator implements IAuthenticator
{
    @Override
    public void authenticateUser(
            User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans) throws SQLException, ExBadCredential
    {
        trans.begin();
        user.throwIfBadCredential(SPParam.getShaedSP(credential));
        trans.commit();
    }
}
