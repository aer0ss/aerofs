/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.session;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.base.ex.ExSecondFactorSetupRequired;
import com.aerofs.ids.UserID;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
import com.aerofs.sp.server.lib.session.ISession;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;
import java.sql.SQLException;

/**
 * A class which holds all the interesting session information about a request.
 */
public class SPSession
    implements ISession
{
    private static final Logger l = Loggers.getLogger(SPSession.class);

    private static final String SESS_ATTR_USER_ID = "userid";
    private static final String SESS_ATTR_BASIC_AUTH_DATE = "basic_auth_date";
    private static final String SESS_ATTR_SECOND_FACTOR_AUTH_DATE = "second_factor_auth_date";
    private static final String SESS_ATTR_CERT_AUTH_DATE = "cert_auth_date";

    private static final long SESSION_LIFETIME = 30 * C.DAY;

    private final User.Factory _factUser;
    private IHttpSessionProvider _sessionProvider;

    // TODO for 2FA:
    // public UserID getPrincipal()
    // public boolean isAuthenticated()  // true if any roles are currently authenticated

    public SPSession(User.Factory factUser, IHttpSessionProvider sessionProvider)
    {
        _factUser = factUser;
        _sessionProvider = sessionProvider;
    }

    private HttpSession session()
    {
        return _sessionProvider.get();
    }

    public boolean isAnonymous()
    {
        return getUserIDNullable() == null;
    }

    public @Nullable UserID getPrincipal()
    {
        return getUserIDNullable();
    }

    @Override
    public ImmutableList<Provenance> getAuthenticatedProvenances()
    {
        long now = System.currentTimeMillis();
        ImmutableList.Builder<Provenance> builder = ImmutableList.builder();
        Long basicAuthDate = (Long)session().getAttribute(SESS_ATTR_BASIC_AUTH_DATE);
        // Basic and two-factor auth
        if (basicAuthDate != null && (now - basicAuthDate) < SESSION_LIFETIME) {
            builder.add(Provenance.BASIC);
            Long secondFactorAuthDate = (Long)session().getAttribute(SESS_ATTR_SECOND_FACTOR_AUTH_DATE);
            if (secondFactorAuthDate != null && (now - secondFactorAuthDate) < SESSION_LIFETIME) {
                builder.add(Provenance.BASIC_PLUS_SECOND_FACTOR);
            }
        }
        // Cert auth
        Long certAuthDate = (Long)session().getAttribute(SESS_ATTR_CERT_AUTH_DATE);
        if (certAuthDate != null && (now - certAuthDate) < SESSION_LIFETIME) {
            builder.add(Provenance.CERTIFICATE);
        }
        return builder.build();
    }

    @Override
    public void setBasicAuthDate(long timestamp)
    {
        session().setAttribute(SESS_ATTR_BASIC_AUTH_DATE, timestamp);
    }

    @Override
    public void setSecondFactorAuthDate(long timestamp)
    {
        session().setAttribute(SESS_ATTR_SECOND_FACTOR_AUTH_DATE, timestamp);
    }

    @Override
    public void setCertificateAuthDate(long timestamp)
    {
        session().setAttribute(SESS_ATTR_CERT_AUTH_DATE, timestamp);
    }

    @Override
    public void dropSecondFactorAuthDate()
    {
        session().removeAttribute(SESS_ATTR_SECOND_FACTOR_AUTH_DATE);
    }

    @Override
    public boolean isAuthenticated()
    {
        return getAuthenticatedProvenances().size() > 0;
    }

    @Override
    public @Nullable User getUserNullable()
    {
        UserID userID = getUserIDNullable();

        if (userID == null) {
            return null;
        }

        return _factUser.create(userID);
    }

    public @Nullable UserID getUserIDNullable()
    {
        return getUserIDNullable(_sessionProvider);
    }

    @Nonnull
    @Override
    public User getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup group)
            throws ExNotAuthenticated, SQLException, ExSecondFactorRequired,
            ExSecondFactorSetupRequired, ExNotFound
    {
        User user = getUserNullable();
        if (user == null) {
            l.info("not authenticated: session " + session().getId());
            throw new ExNotAuthenticated();
        } else {
            User.checkProvenance(user, getAuthenticatedProvenances(), group);
            return user;
        }
    }

    @Override
    public void setUser(User user)
    {
        setUserID(user.id());
    }

    private void setUserID(@Nonnull UserID userID)
    {
        session().setAttribute(SESS_ATTR_USER_ID, userID.getString());
    }

    @Override
    public void deauthorize()
    {
        // Remove all session data.
        // TODO: don't drop the UserID, so we can use it to prepopulate fields and such
        session().removeAttribute(SESS_ATTR_USER_ID);
        session().removeAttribute(SESS_ATTR_BASIC_AUTH_DATE);
        session().removeAttribute(SESS_ATTR_SECOND_FACTOR_AUTH_DATE);
        session().removeAttribute(SESS_ATTR_CERT_AUTH_DATE);
    }

    @Override
    public String id()
    {
        return session().getId();
    }

    /**
     * This function exists so that SPLifecycleListener can map tomcat sessions to UserIDs outside
     * of the session's request context.
     * @param provider A wrapper to
     * @return the UserID associated with the session, or null if none exists
     */
    public static @Nullable UserID getUserIDNullable(IHttpSessionProvider provider)
    {
        String s = (String) provider.get().getAttribute(SESS_ATTR_USER_ID);

        if (s == null) {
            return null;
        }

        return UserID.fromInternal(s);
    }

}
