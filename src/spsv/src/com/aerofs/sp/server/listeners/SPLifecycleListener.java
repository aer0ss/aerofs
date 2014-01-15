package com.aerofs.sp.server.listeners;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.lib.Util;
import com.aerofs.lib.properties.Configuration;
import com.aerofs.servlets.lib.NoopConnectionListener;
import com.aerofs.sp.server.lib.session.HttpSessionUserID;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;

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
import static com.aerofs.sp.server.lib.SPParam.AUDIT_CLIENT_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CACERT_INIT_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;
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
        // This Stackoverflow answer describes why it is most appropriate to do global/application
        // wide initialization within contextInitialized http://stackoverflow.com/a/2364451/3957
        //
        // Initialize Configuration Properties.
        try {
            Configuration.Server.initialize();
        } catch (Exception e) {
            throw new RuntimeException("Configuration server init error: " + Util.e(e));
        }

        ServletContext ctx = servletContextEvent.getServletContext();

        l.info("verkehr host:" + Verkehr.HOST +
                " pub port:" + Verkehr.PUBLISH_PORT +
                " adm port:" + Verkehr.ADMIN_PORT +
                " cacert:" + ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER)
        );

        short publishPort = parseShort(Verkehr.PUBLISH_PORT);
        short adminPort = parseShort(Verkehr.ADMIN_PORT);

        ICertificateProvider cacertProvider = new FileBasedCertificateProvider(getCacertPath(ctx));

        Executor boss = Executors.newCachedThreadPool();
        Executor workers = Executors.newCachedThreadPool();
        HashedWheelTimer timer = new HashedWheelTimer();

        // FIXME (AG): HMMMMMMMM...notice how similar the admin is to a publisher?
        // FIXME (AG): really we should simply store the factories

        VerkehrPublisher publisher = getPublisher(Verkehr.HOST, publishPort, cacertProvider,
                boss, workers, timer, new NoopConnectionListener(), sameThreadExecutor());
        publisher.start();
        ctx.setAttribute(VERKEHR_PUBLISHER_ATTRIBUTE, publisher);

        VerkehrAdmin admin = getAdmin(Verkehr.HOST, adminPort, cacertProvider, boss, workers,
                timer, new NoopConnectionListener(), sameThreadExecutor());
        admin.start();
        ctx.setAttribute(VERKEHR_ADMIN_ATTRIBUTE, admin);

        ctx.setAttribute(AUDIT_CLIENT_ATTRIBUTE, new AuditClient()
                .setAuditorClient(AuditorFactory.createUnauthenticated()));

        // Set up the user session objects.
        ctx.setAttribute(SESSION_USER_TRACKER, _userSessionTracker);
        ctx.setAttribute(SESSION_INVALIDATOR, _sessionInvalidator);
        ctx.setAttribute(SESSION_EXTENDER, _sessionExtender);
    }

    private VerkehrAdmin getAdmin(String host, short adminPort,
            ICertificateProvider cacertProvider,
            Executor bossExecutor, Executor ioWorkerExecutor,
            HashedWheelTimer timer,
            IConnectionListener listener, Executor listenerExecutor)
    {
        com.aerofs.verkehr.client.lib.admin.ClientFactory adminFactory =
                new com.aerofs.verkehr.client.lib.admin.ClientFactory(host, adminPort,
                        bossExecutor, ioWorkerExecutor,
                        cacertProvider,
                        VERKEHR_RECONNECT_DELAY,
                        VERKEHR_ACK_TIMEOUT,
                        timer,
                        listener, listenerExecutor);

        return adminFactory.create();
    }

    private VerkehrPublisher getPublisher(String host, short publishPort,
            ICertificateProvider cacertProvider,
            Executor bossExecutor, Executor ioWorkerExecutor,
            HashedWheelTimer timer,
            IConnectionListener listener, Executor listenerExecutor)
    {
        com.aerofs.verkehr.client.lib.publisher.ClientFactory publisherFactory =
                new com.aerofs.verkehr.client.lib.publisher.ClientFactory(host, publishPort,
                        bossExecutor, ioWorkerExecutor,
                        cacertProvider,
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
        if (publisher != null) {
            publisher.stop();
        }

        VerkehrAdmin admin =  (VerkehrAdmin) ctx.getAttribute(VERKEHR_ADMIN_ATTRIBUTE);
        if (admin != null) {
            admin.stop();
        }
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
        HttpSessionUserID sessionUserID = new HttpSessionUserID(new IHttpSessionProvider()
        {
            @Override
            public HttpSession get()
            {
                return event.getSession();
            }
        });

        UserID userID = sessionUserID.getUserIDNullable();

        if (userID != null) {
            _userSessionTracker.signOut(userID, event.getSession().getId());
        }
    }
}
