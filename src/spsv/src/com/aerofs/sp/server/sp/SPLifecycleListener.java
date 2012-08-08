package com.aerofs.sp.server.sp;

import com.aerofs.lib.Util;
import com.aerofs.verkehr.client.lib.commander.VerkehrCommander;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.lib.Util.join;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_ACK_TIMEOUT_TIMEUNIT;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_CACERT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_COMMANDER_ATTRIBUTE;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_COMMAND_PORT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_HOST_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_PUBLISH_PORT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_RECONNECT_DELAY;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_RECONNECT_DELAY_TIMEUNIT;
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

        String cacert = join(ctx.getRealPath("/"), "WEB-INF",
                ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER));

        Executor boss = Executors.newCachedThreadPool();
        Executor workers = Executors.newCachedThreadPool();
        HashedWheelTimer timer = new HashedWheelTimer();

        // HMMMMMMMM...notice how similar the commander is to a publisher?

        VerkehrPublisher publisher = getPublisher(host, publishPort, cacert, boss, workers, timer);
        ctx.setAttribute(VERKEHR_PUBLISHER_ATTRIBUTE, publisher);

        VerkehrCommander commander = getCommander(host, commandPort, cacert, boss, workers, timer);
        ctx.setAttribute(VERKEHR_COMMANDER_ATTRIBUTE, commander);

        publisher.start();
        commander.start();
    }

    private VerkehrCommander getCommander(String host, short commandPort, String cacert,
            Executor bossExecutor, Executor ioWorkerExecutor, HashedWheelTimer timer)
    {
        com.aerofs.verkehr.client.lib.commander.ClientFactory commanderFactory =
                new com.aerofs.verkehr.client.lib.commander.ClientFactory(host, commandPort,
            bossExecutor, ioWorkerExecutor, cacert,
            VERKEHR_RECONNECT_DELAY, VERKEHR_RECONNECT_DELAY_TIMEUNIT,
            VERKEHR_ACK_TIMEOUT, VERKEHR_ACK_TIMEOUT_TIMEUNIT, timer);

        return commanderFactory.create();
    }

    private VerkehrPublisher getPublisher(String host, short publishPort, String cacert,
            Executor bossExecutor, Executor ioWorkerExecutor, HashedWheelTimer timer)
    {
        com.aerofs.verkehr.client.lib.publisher.ClientFactory publisherFactory =
                new com.aerofs.verkehr.client.lib.publisher.ClientFactory(host, publishPort,
            bossExecutor, ioWorkerExecutor, cacert,
            VERKEHR_RECONNECT_DELAY, VERKEHR_RECONNECT_DELAY_TIMEUNIT, VERKEHR_ACK_TIMEOUT,
            VERKEHR_ACK_TIMEOUT_TIMEUNIT, timer);

        return publisherFactory.create();
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
