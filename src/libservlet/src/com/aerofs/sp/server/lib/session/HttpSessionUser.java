/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.user.ISessionUser;
import org.slf4j.Logger;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wraps a HttpSession and provides a getter, setter, and remover of the "user" attribute.
 */
public class HttpSessionUser
        extends HttpSessionUserID
        implements ISessionUser
{
    private static final Logger l = Loggers.getLogger(HttpSessionUser.class);

    private final User.Factory _factUser;

    public HttpSessionUser(User.Factory factUser, IHttpSessionProvider sessionProvider)
    {
        super(sessionProvider);
        _factUser = factUser;
    }

    @Override
    public boolean exists()
    {
        return getUserIDNullable() != null;
    }

    public @Nullable User getUserNullable()
    {
        UserID userID = getUserIDNullable();

        if (userID == null) {
            return null;
        }

        return _factUser.create(userID);
    }

    @Override
    public @Nonnull User getUser() throws ExNotAuthenticated
    {
        User user = getUserNullable();
        if (user == null) {
            l.info("not authenticated: session " + getSession().getId());
            throw new ExNotAuthenticated();
        } else {
            return user;
        }
    }

    @Override
    public void setUser(@Nonnull User user)
    {
        setUserID(user.id());
    }
}
