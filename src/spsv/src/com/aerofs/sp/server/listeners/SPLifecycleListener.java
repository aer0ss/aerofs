package com.aerofs.sp.server.listeners;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.SPVerkehrClientFactory;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSession;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.slf4j.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import static com.aerofs.sp.server.lib.SPParam.AUDIT_CLIENT_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CACERT_INIT_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;

public class SPLifecycleListener extends ConfigurationLifecycleListener
        implements ServletContextListener, HttpSessionListener
{
    private static final Logger l = Loggers.getLogger(SPLifecycleListener.class);

    // Trackers.
    private final SPActiveUserSessionTracker _userSessionTracker = new SPActiveUserSessionTracker();
    private final SPActiveTomcatSessionTracker _tomcatSessionTracker =
            new SPActiveTomcatSessionTracker();

    // Session invalidator.
    private final SPSessionInvalidator _sessionInvalidator =
            new SPSessionInvalidator(_userSessionTracker, _tomcatSessionTracker);

    // Session extender.
    private final SPSessionExtender _sessionExtender = new SPSessionExtender(_tomcatSessionTracker);

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        super.contextInitialized(servletContextEvent);

        ServletContext ctx = servletContextEvent.getServletContext();

        l.info("verkehr host:" + Verkehr.HOST +
                " pub port:" + Verkehr.PUBLISH_PORT +
                " adm port:" + Verkehr.ADMIN_PORT +
                " cacert:" + ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER)
        );

        SPVerkehrClientFactory factory = new SPVerkehrClientFactory(ctx);

        VerkehrPublisher publisher = factory.createVerkehrPublisher();
        publisher.start();
        ctx.setAttribute(VERKEHR_PUBLISHER_ATTRIBUTE, publisher);

        VerkehrAdmin admin = factory.createVerkehrAdmin();
        admin.start();
        ctx.setAttribute(VERKEHR_ADMIN_ATTRIBUTE, admin);

        ctx.setAttribute(AUDIT_CLIENT_ATTRIBUTE, new AuditClient()
                .setAuditorClient(AuditorFactory.createUnauthenticated()));

        // Set up the user session objects.
        ctx.setAttribute(SESSION_USER_TRACKER, _userSessionTracker);
        ctx.setAttribute(SESSION_INVALIDATOR, _sessionInvalidator);
        ctx.setAttribute(SESSION_EXTENDER, _sessionExtender);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        ServletContext ctx = servletContextEvent.getServletContext();

        VerkehrPublisher publisher =
                (VerkehrPublisher) ctx.getAttribute(VERKEHR_PUBLISHER_ATTRIBUTE);
        if (publisher != null) {
            publisher.stop();
        }

        VerkehrAdmin admin =  (VerkehrAdmin) ctx.getAttribute(VERKEHR_ADMIN_ATTRIBUTE);
        if (admin != null) {
            admin.stop();
        }

        super.contextDestroyed(servletContextEvent);
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
        UserID userID = SPSession.getUserIDNullable(new IHttpSessionProvider()
        {
            @Override
            public HttpSession get()
            {
                return event.getSession();
            }
        });

        if (userID != null) {
            _userSessionTracker.signOut(userID, event.getSession().getId());
        }
    }
}
