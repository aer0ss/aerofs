/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.session;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
import com.aerofs.sp.server.lib.user.ISession;
import com.aerofs.sp.server.lib.user.User;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;

/**
 * A class which holds all the interesting session information about a request.
 */
public class SPSession
    implements ISession
{
    private static final Logger l = Loggers.getLogger(SPSession.class);
    private static final String SESS_ATTR_USER_ID = "userid";
    private final User.Factory _factUser;

    private IHttpSessionProvider _sessionProvider;

    // TODO for 2FA:
    // public boolean isAnonymous()
    // public boolean isAuthenticated()
    // public UserID getPrincipal()
    // public List<Role> getAllowedRoles()
    // where Role is an enum of [BASIC, BASIC+2FA, CERT, OAUTH]

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

    private @Nullable User getUserNullable()
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
    public User getUser()
            throws ExNotAuthenticated
    {
        User user = getUserNullable();
        if (user == null) {
            l.info("not authenticated: session " + session().getId());
            throw new ExNotAuthenticated();
        } else {
            return user;
        }
    }

    @Override
    public boolean exists()
    {
        return getUserIDNullable() != null;
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
    public void remove()
    {
        session().removeAttribute(SESS_ATTR_USER_ID);
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
