package com.aerofs.sp.server.listeners;

import com.aerofs.ids.UserID;
import com.aerofs.sp.server.session.*;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import static com.aerofs.sp.server.lib.SPParam.*;

public class SPLifecycleListener extends ConfigurationLifecycleListener
        implements ServletContextListener, HttpSessionListener
{
    // Trackers.
    private final SPActiveUserSessionTracker _userSessionTracker = new SPActiveUserSessionTracker();
    private final SPActiveTomcatSessionTracker _tomcatSessionTracker =
            new SPActiveTomcatSessionTracker();

    // Session invalidator.
    private final SPSessionInvalidator _sessionInvalidator =
            new SPSessionInvalidator(_userSessionTracker, _tomcatSessionTracker);

    // Session extender.
    private final SPSessionExtender _sessionExtender = new SPSessionExtender(_tomcatSessionTracker);

    public SPLifecycleListener() {}

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        super.contextInitialized(servletContextEvent);

        ServletContext ctx = servletContextEvent.getServletContext();

        // user-session objects
        ctx.setAttribute(SESSION_USER_TRACKER, _userSessionTracker);
        ctx.setAttribute(SESSION_INVALIDATOR, _sessionInvalidator);
        ctx.setAttribute(SESSION_EXTENDER, _sessionExtender);
    }

    @Override
    public void sessionCreated(final HttpSessionEvent event)
    {
        _tomcatSessionTracker.sessionCreated(event.getSession());
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event)
    {
        _tomcatSessionTracker.sessionDestroyed(event.getSession().getId());

        // If a sign in has occurred for this specific session, update the user session tracker
        // as well.
        UserID userID = SPSession.getUserIDNullable(event::getSession);

        if (userID != null) {
            _userSessionTracker.signOut(userID, event.getSession().getId());
        }
    }
}
