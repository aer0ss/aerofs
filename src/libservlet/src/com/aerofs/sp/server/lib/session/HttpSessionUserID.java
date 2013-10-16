/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.base.id.UserID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HttpSessionUserID
        extends AbstractHttpSession
{
    private static final String SESS_ATTR_USER_ID = "userid";

    public HttpSessionUserID(IHttpSessionProvider sessionProvider)
    {
        super(sessionProvider);
    }

    public @Nullable UserID getUserIDNullable()
    {
        String s = (String) getSession().getAttribute(SESS_ATTR_USER_ID);

        if (s == null) {
            return null;
        }

        return UserID.fromInternal(s);
    }

    public void setUserID(@Nonnull UserID userID)
    {
        getSession().setAttribute(SESS_ATTR_USER_ID, userID.getString());
    }

    public void remove()
    {
        getSession().removeAttribute(SESS_ATTR_USER_ID);
    }
}
