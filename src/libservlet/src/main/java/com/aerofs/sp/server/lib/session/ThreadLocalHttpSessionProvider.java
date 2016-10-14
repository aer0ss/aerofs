/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import javax.servlet.http.HttpSession;

/**
 * Thread local objects are used to hold session data because each individual tomcat request is
 * guaranteed by tomcat to be processed in its own thread.
 */
public class ThreadLocalHttpSessionProvider implements IHttpSessionProvider
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

    @Override
    public HttpSession get()
    {
        return _session.get();
    }
}
