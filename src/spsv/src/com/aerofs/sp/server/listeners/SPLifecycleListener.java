package com.aerofs.sp.server.listeners;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSession;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.util.concurrent.MoreExecutors;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.sp.server.lib.SPParam.AUDIT_CLIENT_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CLIENT_ATTRIBUTE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

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

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        super.contextInitialized(servletContextEvent);

        ServletContext ctx = servletContextEvent.getServletContext();

        // verkehr
        VerkehrClient verkehrClient = createVerkehrClient();
        ctx.setAttribute(VERKEHR_CLIENT_ATTRIBUTE, verkehrClient);

        // auditor
        ctx.setAttribute(AUDIT_CLIENT_ATTRIBUTE, new AuditClient().setAuditorClient(AuditorFactory.createUnauthenticated()));

        // user-session objects
        ctx.setAttribute(SESSION_USER_TRACKER, _userSessionTracker);
        ctx.setAttribute(SESSION_INVALIDATOR, _sessionInvalidator);
        ctx.setAttribute(SESSION_EXTENDER, _sessionExtender);
    }

    private static VerkehrClient createVerkehrClient()
    {
        Executor nioExecutor = Executors.newCachedThreadPool();
        NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(nioExecutor, nioExecutor, 1, 2);
        return VerkehrClient.create(
                Verkehr.HOST,
                Verkehr.REST_PORT,
                MILLISECONDS.convert(30, SECONDS),
                MILLISECONDS.convert(60, SECONDS),
                10,
                new HashedWheelTimer(),
                MoreExecutors.sameThreadExecutor(),
                channelFactory);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        ServletContext ctx = servletContextEvent.getServletContext();
        VerkehrClient verkehrClient = (VerkehrClient) ctx.getAttribute(VERKEHR_CLIENT_ATTRIBUTE);
        if (verkehrClient != null) {
            verkehrClient.disconnectAll();
            verkehrClient.shutdown();
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
