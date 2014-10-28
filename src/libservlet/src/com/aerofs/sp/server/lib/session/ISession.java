/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.base.ex.ExSecondFactorSetupRequired;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.sql.SQLException;

/**
 * This interface is only necessary to enable mocking of the ThreadLocalHttpSessionUser in SPService
 */
public interface ISession
{
    public enum Provenance
    {
        BASIC,                    // The standard authenticator for this user
        BASIC_PLUS_SECOND_FACTOR, // Same, but also provided second factor
        CERTIFICATE,              // Logged in with device certificate
        // possible future additions:
        // RECENT_BASIC, which requires the basic provenance proven within the last ~15 minutes
        // RECENT_BASIC_PLUS_SECOND_FACTOR, analogous
    }

    // Provenance usages
    public enum ProvenanceGroup
    {
        LEGACY, // A catch-all group that is permitted if you have either CERTIFICATE provenance
                // or BASIC (if your user doesn't use two-factor) or BASIC_PLUS_SECOND_FACTOR (if
                // your user has enabled two-factor enforcement)
        TWO_FACTOR_SETUP, // Allows users to set up two-factor auth if:
                          // 1) their organization mandates 2FA usage
                          // 2) they do not currently have 2FA enabled
                          // 3) they carry at least BASIC provenance
                          // If we required LEGACY, then users in an org with MANDATORY 2FA would
                          // be unable to set up their second factor if they hadn't already set up
                          // a second factor.
        INTERACTIVE, // Like LEGACY, but doesn't allow CERTIFICATE provenance.  Useful if you want
                     // to allow actions only to user sessions, rather than to automated device
                     // certificate-originated sessions.
    }

    /**
     * @return the user of this session
     * @throws ExNotAuthenticated if no user has been setUser for the session (i.e. does not exist).
     *         ExSecondFactorRequired if user requires a second factor but this session bears none
     */
    @Nonnull
    User getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup group)
            throws ExNotAuthenticated, SQLException, ExSecondFactorRequired,
            ExSecondFactorSetupRequired, ExNotFound;

    User getUserNullable();

    void setBasicAuthDate(long timestamp);

    void setSecondFactorAuthDate(long timestamp);

    void setCertificateAuthDate(long timestamp);

    /**
     * @return True if the current session is authenticated, False otherwise.
     */
    boolean isAuthenticated();

    /**
     * @return a list of the provenances for which this session is currently authenticated.
     */
    ImmutableList<Provenance> getAuthenticatedProvenances();

    /**
     * Set the session user.
     */
    void setUser(User user);

    /**
     * Remove the user ID and all authentication tokens from this session.  isAuthenticated() will
     * return false after this.
     */
    void deauthorize();

    /**
     * Get unique identifier associated with this session.
     */
    String id();
}
