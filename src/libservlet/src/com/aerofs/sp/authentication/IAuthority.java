/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.authentication.Authenticator.CredentialFormat;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.lib.user.User;

import java.sql.SQLException;

/**
 * An authority is an object that can authenticate a user/credential pair.
 */
public interface IAuthority
{
    // FIXME: Remove "format" arg when we drop support for client-side SCrypt. Review January 2014
    // Ok; I don't like the transaction interface being passed in here, it feels like poor
    // cohesion. However, the various authorities will have different SQL transaction
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
    void authenticateUser(
            User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans,
            CredentialFormat format)
            throws Exception;

    /**
     * Return true if an account authenticated by this authority has a local credential.
     *
     * Locally-managed accounts require a signup flow.
     *
     * Locally-managed accounts have a credential that is stored and owned by SP; which also
     * implies that credential is changeable.
     */
    boolean managesLocalCredential();

    /**
     * Return true if this authority can authenticate the given user.
     */
    boolean canAuthenticate(UserID userID) throws ExExternalServiceUnavailable;

    /**
     * Check if the given user is an internal user. NOTE that "internal" refers to whether the
     * accountholder belongs to the primary organization. This influences things like warnings
     * for shared-folder invitations, signup, etc. It does not necessarily tell you _where_
     * their account is managed.
     *
     * IMPORTANT: the internal/external axis is not always related to where the account is managed.
     *
     * @see IAuthority#managesLocalCredential()
     */
    boolean isInternalUser(UserID userID) throws ExExternalServiceUnavailable;
}
