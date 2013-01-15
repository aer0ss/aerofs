package com.aerofs.sp.server.session;

import com.aerofs.base.C;

import javax.servlet.http.HttpSession;

public class SPSessionExtender
{
    private SPActiveTomcatSessionTracker _sessionTracker;

    public SPSessionExtender(SPActiveTomcatSessionTracker sessionTracker)
    {
        _sessionTracker = sessionTracker;
    }

    public void extendSession(String sessionID)
    {
        // One year, in seconds (not milliseconds - that's why we divide by 1000).
        final int MAX_INACTIVE_INTERVAL = (int) ((C.YEAR / C.SEC) / 1000);

        HttpSession session = _sessionTracker.getSession(sessionID);

        if (session != null) {
            session.setMaxInactiveInterval(MAX_INACTIVE_INTERVAL);
        }
    }
}