/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.lib.user.User;

import java.sql.SQLException;

/**
 * An authenticator type that will throw an exception if the given user/credential pair can not
 * be authenticated.
 */
public interface IAuthenticator
{
    // FIXME: I don't like the transaction interface being passed in here, it feels like poor
    // cohesion. However, the various authenticators will have different SQL transaction
    // requirements depending on whether they look into the User.exists() field before or after
    // an expensive operation (like external authentication)

    /**
     * Authenticate the user using the given credentials. Throw an exception (ExBadCredential or
     * other) if the user is not valid. A normal return indicates the user is valid.
     *
     * Important: this expects no currently-active transaction! The implementation will start
     * and commit as appropriate.
     *
     * @param user User object, must have id()
     * @param credential Credential formatted in the manner expected by the implementation. Could
     * be a raw or hashed credential.
     * @param trans A transaction class that can be begin'ed and commit'ed.
     * @throws Exception The user cannot be verified.
     */
    public void authenticateUser(
            User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans)
            throws Exception;

    /**
     * Check if the given userId is able to be automatically provisioned on first signin.
     *
     * @return whether the user can be automatically provisioned on first signin (i.e. without
     * explicitly requesting for signup or being invited).
     *
     * TODO (WW) support OpenID
     */
    public boolean isAutoProvisioned(UserID userID);
}
