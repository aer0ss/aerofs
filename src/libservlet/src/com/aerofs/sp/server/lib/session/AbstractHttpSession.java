package com.aerofs.sp.server.lib.session;

import javax.servlet.http.HttpSession;

/**
 * Abstract class for session management related objects to facilitate interaction with the session
 * provider.
 *
 * TODO (MP) refactor classes that inherit from this class.
 * Session specific objects are not sessions, so inheritance is not the right abstraction.
 * Composition should be used instead. See HttpSessionUser and HttpSessionRemoteAddress.
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

    // Suppress unused method warning because this is actually used in SPService. IDEs are bad at
    // detecting this because we're calling a method on an interface defined by a subclass, so it
    // doesn't have an override.
    @SuppressWarnings("unused")
    public String getSessionID()
    {
        return _sessionProvider.get().getId();
    }
}