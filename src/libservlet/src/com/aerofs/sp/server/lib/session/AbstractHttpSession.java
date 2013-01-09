package com.aerofs.sp.server.lib.session;

import javax.servlet.http.HttpSession;

/**
 * Abstract class for session management related objects to facilitate interaction with the session
 * provider.
 */
public abstract class AbstractHttpSession
{
    private final IHttpSessionProvider _sessionProvider;

    public AbstractHttpSession(IHttpSessionProvider sessionProvider)
    {
        _sessionProvider = sessionProvider;
    }

    protected HttpSession getSession()
    {
        return _sessionProvider.get();
    }

    public String getSessionID()
    {
        return _sessionProvider.get().getId();
    }
}