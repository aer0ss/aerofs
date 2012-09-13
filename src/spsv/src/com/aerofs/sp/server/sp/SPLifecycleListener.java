package com.aerofs.sp.server.sp;

import com.aerofs.lib.Util;
import com.aerofs.servletlib.NoopConnectionListener;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.commander.VerkehrCommander;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.lib.Util.join;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_CACERT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_COMMANDER_ATTRIBUTE;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_COMMAND_PORT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_HOST_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_PUBLISH_PORT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_RECONNECT_DELAY;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.lang.Short.parseShort;

public class SPLifecycleListener implements ServletContextListener
{
    private static final Logger l = Util.l(SPLifecycleListener.class);

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        ServletContext ctx = servletContextEvent.getServletContext();

        l.info("verkehr host:" + ctx.getInitParameter(VERKEHR_HOST_INIT_PARAMETER) +
                " pub port:" + ctx.getInitParameter(VERKEHR_PUBLISH_PORT_INIT_PARAMETER) +
                " cmd port:" + ctx.getInitParameter(VERKEHR_COMMAND_PORT_INIT_PARAMETER) +
                " cacert:" + ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER)
        );

        String host = ctx.getInitParameter(VERKEHR_HOST_INIT_PARAMETER);

        short publishPort = parseShort(ctx.getInitParameter(VERKEHR_PUBLISH_PORT_INIT_PARAMETER));
        short commandPort = parseShort(ctx.getInitParameter(VERKEHR_COMMAND_PORT_INIT_PARAMETER));

        String cacert =  getCacertPath(ctx);

        Executor boss = Executors.newCachedThreadPool();
        Executor workers = Executors.newCachedThreadPool();
        HashedWheelTimer timer = new HashedWheelTimer();

        // FIXME (AG): HMMMMMMMM...notice how similar the commander is to a publisher?
        // FIXME (AG): really we should simply store the factories

        VerkehrPublisher publisher = getPublisher(host, publishPort, cacert, boss, workers, timer,
                new NoopConnectionListener(), sameThreadExecutor());
        publisher.start();
        ctx.setAttribute(VERKEHR_PUBLISHER_ATTRIBUTE, publisher);

        VerkehrCommander commander = getCommander(host, commandPort, cacert, boss, workers, timer,
                new NoopConnectionListener(), sameThreadExecutor());
        commander.start();
        ctx.setAttribute(VERKEHR_COMMANDER_ATTRIBUTE, commander);
    }

    private VerkehrCommander getCommander(String host, short commandPort,
            String cacert,
            Executor bossExecutor, Executor ioWorkerExecutor,
            HashedWheelTimer timer,
            IConnectionListener listener, Executor listenerExecutor)
    {
        com.aerofs.verkehr.client.lib.commander.ClientFactory commanderFactory =
                new com.aerofs.verkehr.client.lib.commander.ClientFactory(host, commandPort,
                        bossExecutor, ioWorkerExecutor,
                        cacert,
                        VERKEHR_RECONNECT_DELAY,
                        VERKEHR_ACK_TIMEOUT,
                        timer,
                        listener, listenerExecutor);

        return commanderFactory.create();
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

        VerkehrPublisher publisher =  (VerkehrPublisher) ctx.getAttribute(VERKEHR_PUBLISHER_ATTRIBUTE);
        assert publisher != null;
        publisher.stop();

        VerkehrCommander commander =  (VerkehrCommander) ctx.getAttribute(VERKEHR_COMMANDER_ATTRIBUTE);
        assert commander != null;
        commander.stop();
    }
}
