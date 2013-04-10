package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.properties.Configuration;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.servlets.lib.NoopConnectionListener;
import com.aerofs.sp.server.lib.session.HttpSessionUser;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.slf4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.lib.Util.join;
import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CACERT_INIT_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_PORT_INIT_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_HOST_INIT_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_PUBLISH_PORT_INIT_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_RECONNECT_DELAY;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.lang.Short.parseShort;

public class SPLifecycleListener implements ServletContextListener, HttpSessionListener
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
        Configuration.Server.initialize();

        ServletContext ctx = servletContextEvent.getServletContext();

        l.info("verkehr host:" + ctx.getInitParameter(VERKEHR_HOST_INIT_PARAMETER) +
                " pub port:" + ctx.getInitParameter(VERKEHR_PUBLISH_PORT_INIT_PARAMETER) +
                " adm port:" + ctx.getInitParameter(VERKEHR_ADMIN_PORT_INIT_PARAMETER) +
                " cacert:" + ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER)
        );

        String host = ctx.getInitParameter(VERKEHR_HOST_INIT_PARAMETER);

        short publishPort = parseShort(ctx.getInitParameter(VERKEHR_PUBLISH_PORT_INIT_PARAMETER));
        short adminPort = parseShort(ctx.getInitParameter(VERKEHR_ADMIN_PORT_INIT_PARAMETER));

        String cacert =  getCacertPath(ctx);

        Executor boss = Executors.newCachedThreadPool();
        Executor workers = Executors.newCachedThreadPool();
        HashedWheelTimer timer = new HashedWheelTimer();

        // FIXME (AG): HMMMMMMMM...notice how similar the admin is to a publisher?
        // FIXME (AG): really we should simply store the factories

        VerkehrPublisher publisher = getPublisher(host, publishPort, cacert, boss, workers, timer,
                new NoopConnectionListener(), sameThreadExecutor());
        publisher.start();
        ctx.setAttribute(VERKEHR_PUBLISHER_ATTRIBUTE, publisher);

        VerkehrAdmin admin = getAdmin(host, adminPort, cacert, boss, workers, timer,
                new NoopConnectionListener(), sameThreadExecutor());
        admin.start();
        ctx.setAttribute(VERKEHR_ADMIN_ATTRIBUTE, admin);

        // Set up the user session objects.
        ctx.setAttribute(SESSION_USER_TRACKER, _userSessionTracker);
        ctx.setAttribute(SESSION_INVALIDATOR, _sessionInvalidator);
        ctx.setAttribute(SESSION_EXTENDER, _sessionExtender);
    }

    private VerkehrAdmin getAdmin(String host, short adminPort,
            String cacert,
            Executor bossExecutor, Executor ioWorkerExecutor,
            HashedWheelTimer timer,
            IConnectionListener listener, Executor listenerExecutor)
    {
        com.aerofs.verkehr.client.lib.admin.ClientFactory adminFactory =
                new com.aerofs.verkehr.client.lib.admin.ClientFactory(host, adminPort,
                        bossExecutor, ioWorkerExecutor,
                        cacert,
                        VERKEHR_RECONNECT_DELAY,
                        VERKEHR_ACK_TIMEOUT,
                        timer,
                        listener, listenerExecutor);

        return adminFactory.create();
    }

    private VerkehrPublisher getPublisher(String host, short publishPort,
            String cacert,
            Executor bossExecutor, Executor ioWorkerExecutor,
            HashedWheelTimer timer,
            IConnectionListener listener, Executor listenerExecutor)
    {
        com.aerofs.verkehr.client.lib.publisher.ClientFactory publisherFactory =
                new com.aerofs.verkehr.client.lib.publisher.ClientFactory(host, publishPort,
                        bossExecutor, ioWorkerExecutor,
                        cacert,
                        VERKEHR_RECONNECT_DELAY,
                        VERKEHR_ACK_TIMEOUT,
                        timer,
                        listener, listenerExecutor);

        return publisherFactory.create();
    }

    private String getCacertPath(ServletContext ctx)
    {
        String cacertParameterValue = ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER);
        boolean isAbsolutePath = new File(cacertParameterValue).isAbsolute();
        if (isAbsolutePath) return cacertParameterValue;
        else return join(ctx.getRealPath("/"), "WEB-INF", cacertParameterValue);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        ServletContext ctx = servletContextEvent.getServletContext();

        VerkehrPublisher publisher =
                (VerkehrPublisher) ctx.getAttribute(VERKEHR_PUBLISHER_ATTRIBUTE);

        assert publisher != null;
        publisher.stop();

        VerkehrAdmin admin =  (VerkehrAdmin) ctx.getAttribute(VERKEHR_ADMIN_ATTRIBUTE);
        assert admin != null;
        admin.stop();
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
        HttpSessionUser sessionUser = new HttpSessionUser(new IHttpSessionProvider()
        {
            @Override
            public HttpSession get()
            {
                return event.getSession();
            }
        });

        User user = sessionUser.getNullable();

        if (user != null) {
            _userSessionTracker.signOut(user.id(), event.getSession().getId());
        }
    }
}
