/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wraps a HttpSession and provides a getter, setter, and remover of the "user" attribute.
 */
public class HttpSessionUser
        extends AbstractHttpSession
        implements ISessionUser
{
    private static final Logger l = Loggers.getLogger(HttpSessionUser.class);
    private static final String SESS_ATTR_USER  = "user";

    public HttpSessionUser(IHttpSessionProvider sessionProvider)
    {
        super(sessionProvider);
    }

    @Override
    public boolean exists()
    {
        return getNullable() != null;
    }

    public @Nullable User getNullable()
    {
        return (User) getSession().getAttribute(SESS_ATTR_USER);
    }

    @Override
    public @Nonnull User get() throws ExNotAuthenticated
    {
        User user = getNullable();
        if (user == null) {
            l.info("not authenticated: session " + getSession().getId());
            throw new ExNotAuthenticated();
        } else {
            return user;
        }
    }

    @Override
    public void set(@Nonnull User user)
    {
        getSession().setAttribute(SESS_ATTR_USER, user);
    }

    @Override
    public void remove()
    {
        getSession().removeAttribute(SESS_ATTR_USER);
    }
}
