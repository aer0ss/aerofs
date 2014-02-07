/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.SQLException;

/**
 * A single class that delegates credential authentication to one or more identity authorities.
 *
 * The authorities asked for any given user depend on the configuration and the user in question.
 * Authorities will be asked, in order, if they can handle a particular user; the first authority
 * that can handle a user will then be considered authoritative.
 */
public class Authenticator
{
    /** Legacy support - No new users of this enum!
     * This is used to indicate backwards-compatible mode for authorities.
     * FIXME: Remove this enum when we can drop support for client-side SCrypt. Review January 2014
     */
    public static enum CredentialFormat
    {
        TEXT,
        LEGACY
    }

    /**
     * @param authorities Ordered array of authority types; they will be tried in order.
     */
    public Authenticator(@Nonnull IAuthority[] authorities)
    {
        Preconditions.checkArgument(authorities.length > 0);
        _authorities = authorities;
        l.info("Configured with {} authority types", _authorities.length);
    }

    /**
     * Authorities are asked in order if they can handle the userid given.
     * The first authority that accepts the id _must_ authenticate the user.
     *
     * If every authority refuses a userid, an exception is thrown.
     *
     * An authority may throw an exception, which will prevent subsequent authorities
     * from examining the credential.
     *
     * @param user User object, must have id()
     * @param credential Credential formatted in the manner expected by the implementation. Could
     * be a raw or hashed credential.
     * @param trans A transaction class that can be begin'ed and commit'ed.
     * @param format Indication to be passed through to IAuthority instances if the
     * credential is legacy-formatted.
     * @throws com.aerofs.base.ex.ExBadCredential if the given credential is incorrect
     * @throws ExExternalServiceUnavailable if the authority is misconfigured or all
     * authorities are unable to reach external authorities.
     */
    public IAuthority authenticateUser(User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans, CredentialFormat format)
            throws Exception
    {
        for (IAuthority auth : _authorities) {
            if (auth.canAuthenticate(user.id())) {
                l.debug("Auth using {} for {}", auth, user);
                auth.authenticateUser(user, credential, trans, format);
                l.info("auth ok {}", user);
                return auth;
            }
        }
        l.warn("No authority can handle {}", user.id());
        throw new ExBadCredential("Refusing to authenticate user " + user.id());
    }

    /**
     * True if the authority that is responsible for this account uses local credentials.
     */
    public boolean isLocallyManaged(@Nonnull UserID userID) throws ExExternalServiceUnavailable
    {
        for (IAuthority auth : _authorities) {
            if (auth.canAuthenticate(userID)) {
                return auth.managesLocalCredential();
            }
        }
        return true;
    }

    /**
     * Ask the primary (i.e. first) authority if this user is an internal record.
     * The authorities have different mechanisms to determine this fact. We deliberately only
     * ask the primary authority.
     */
    public boolean isInternalUser(UserID userID) throws ExExternalServiceUnavailable
    {
        return userID.isTeamServerID() ? true : _authorities[0].isInternalUser(userID);
    }

    public void setACLPublisher_(ACLNotificationPublisher aclPublisher)
    {
        for (IAuthority auth : _authorities) auth.setACLPublisher(aclPublisher);
    }

    private IAuthority[] _authorities;
    private static Logger l = LoggerFactory.getLogger(Authenticator.class);
}
