/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import javax.servlet.http.HttpSession;

public class AbstractThreadLocalHttpSession
{
    protected static final ThreadLocal<HttpSession> _session = new ThreadLocal<HttpSession>();

    /**
     * Set the thread-local HttpSession.
     */
    public void setSession(HttpSession httpSession)
    {
        assert httpSession != null;
        _session.set(httpSession);
    }
}
