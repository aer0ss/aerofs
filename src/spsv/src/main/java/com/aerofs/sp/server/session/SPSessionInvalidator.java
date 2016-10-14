package com.aerofs.sp.server.session;

import com.aerofs.base.Loggers;
import com.aerofs.ids.UserID;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
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

    public void invalidateSecondFactor(UserID userid)
    {
        for (String sessId : _userTracker.sessionsForUser(userid)) {
            HttpSession tomcatSession = _sessionTracker.getSession(sessId);
            if (tomcatSession != null) {
                // we don't need the User.Factory for this access, so we null it out because
                // we can't actually cleanly inject this
                // we do need an IHttpSessionProvider that returns this particular tomcatSession
                SPSession session = new SPSession(null, () -> tomcatSession);
                session.dropSecondFactorAuthDate();
            }
        }
    }
}
