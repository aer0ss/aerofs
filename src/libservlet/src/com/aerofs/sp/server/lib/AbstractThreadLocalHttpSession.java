/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import javax.servlet.http.HttpSession;

/**
 * Use a thread local object here to hold session data because each individual tomcat request
 * is guaranteed by tomcat to be processed in its own thread.
 */
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
