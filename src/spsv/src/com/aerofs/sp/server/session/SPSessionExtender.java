package com.aerofs.sp.server.session;

import com.aerofs.base.C;
import com.aerofs.base.config.ConfigurationProperties;

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
        // One year, in seconds
        final int MAX_INACTIVE_INTERVAL = getInactiveInterval();

        HttpSession session = _sessionTracker.getSession(sessionID);

        if (session != null) {
            session.setMaxInactiveInterval(MAX_INACTIVE_INTERVAL);
        }
    }

    private int getInactiveInterval()
    {
        return ConfigurationProperties.getBooleanProperty("web.session_daily_expiration", false) ?
                (int) (C.DAY / C.SEC) : (int) (C.YEAR / C.SEC);
    }
}