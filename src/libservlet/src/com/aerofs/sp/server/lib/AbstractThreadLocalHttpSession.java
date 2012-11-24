/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import javax.servlet.http.HttpSession;

// TODO (WW) FIXME The correlation of sessions and threads are accidental. It is incorrect to use
// thread-local storage to simulate session-local storage.
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
