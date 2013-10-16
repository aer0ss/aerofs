package com.aerofs.sp.server.session;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import org.slf4j.Logger;

import javax.servlet.http.HttpSession;
import java.util.Set;

public class SPSessionInvalidator
{
    private static final Logger l = Loggers.getLogger(SPSessionInvalidator.class);

    private final SPActiveUserSessionTracker _userTracker;
    private final SPActiveTomcatSessionTracker _sessionTracker;

    public SPSessionInvalidator(SPActiveUserSessionTracker userTracker,
            SPActiveTomcatSessionTracker tomcatTracker)
    {
        _userTracker = userTracker;
        _sessionTracker = tomcatTracker;
    }

    /**
     * Invalidate all sessions belonging to this user and update the user and tomcat session
     * trackers.
     */
    public void invalidate(UserID userID)
    {
        l.info("Invalidate: " + userID);
        Set<String> sessionSet = _userTracker.signOutAll(userID);

        if (sessionSet != null) {
            for (String sessionID : sessionSet) {
                HttpSession session = _sessionTracker.sessionDestroyed(sessionID);

                if (session != null) {
                    l.info("Invalidate: " + userID + " " + sessionID);
                    session.invalidate();
                }
            }
        }
    }
}