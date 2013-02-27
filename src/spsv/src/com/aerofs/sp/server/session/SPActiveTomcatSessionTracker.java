/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.session;

import com.aerofs.base.Loggers;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * An object that keeps track of active SP tomcat sessions. Maintains a mapping from tomcat session
 * ID string to the HTTP session object.
 */
public class SPActiveTomcatSessionTracker
{
    private static final Logger l = Loggers.getLogger(SPActiveTomcatSessionTracker.class);
    private final Map<String, HttpSession> _sessionMap = Maps.newHashMap();

    public synchronized void sessionCreated(HttpSession session)
    {
        l.debug("Session created: " + session.getId());
        _sessionMap.put(session.getId(), session);
    }

    /**
     * Removes the session specified by the passed session ID and returns the HTTP session object.
     */
    public synchronized @Nullable HttpSession sessionDestroyed(String sessionID)
    {
        l.debug("Session destroyed: " + sessionID);

        HttpSession session = _sessionMap.get(sessionID);
        _sessionMap.remove(sessionID);
        return session;
    }

    public synchronized @Nullable HttpSession getSession(String sessionID)
    {
        return _sessionMap.get(sessionID);
    }
}