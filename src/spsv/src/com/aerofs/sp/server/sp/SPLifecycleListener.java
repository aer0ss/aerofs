package com.aerofs.sp.server.sp;

import com.aerofs.lib.Util;
import com.aerofs.verkehr.client.publisher.VerkehrPublisher;
import com.aerofs.verkehr.client.commander.VerkehrCommander;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.lib.Util.join;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_CACERT_INIT_PARAMETER;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_ACK_TIMEOUT_TIMEUNIT;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_COMMANDER_ATTRIBUTE;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_COMMAND_PORT_INIT_PARAMETER;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_HOST_INIT_PARAMETER;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_PUBLISH_PORT_INIT_PARAMETER;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_RECONNECT_DELAY;
import static com.aerofs.sp.server.sp.SPParam.VERKEHR_RECONNECT_DELAY_TIMEUNIT;

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

        short publishPort = Short.parseShort(
                ctx.getInitParameter(VERKEHR_PUBLISH_PORT_INIT_PARAMETER));
        short commandPort = Short.parseShort(
                ctx.getInitParameter(VERKEHR_COMMAND_PORT_INIT_PARAMETER));

        String serverTrustedRootCACertFilename = join(ctx.getRealPath("/"), "WEB-INF",
                ctx.getInitParameter(VERKEHR_CACERT_INIT_PARAMETER));

        Executor bossExecutor = Executors.newCachedThreadPool();
        Executor ioWorkerExecutor = Executors.newCachedThreadPool();

        VerkehrPublisher publisher = VerkehrPublisher.getInstance(host, publishPort,
                bossExecutor, ioWorkerExecutor, serverTrustedRootCACertFilename,
                VERKEHR_RECONNECT_DELAY, VERKEHR_RECONNECT_DELAY_TIMEUNIT,
                VERKEHR_ACK_TIMEOUT, VERKEHR_ACK_TIMEOUT_TIMEUNIT, new HashedWheelTimer());
        publisher.start();

        VerkehrCommander commander = VerkehrCommander.getInstance(host, commandPort,
                bossExecutor, ioWorkerExecutor, serverTrustedRootCACertFilename,
                VERKEHR_RECONNECT_DELAY, VERKEHR_RECONNECT_DELAY_TIMEUNIT,
                VERKEHR_ACK_TIMEOUT, VERKEHR_ACK_TIMEOUT_TIMEUNIT, new HashedWheelTimer());
        commander.start();

        ctx.setAttribute(VERKEHR_PUBLISHER_ATTRIBUTE, publisher);
        ctx.setAttribute(VERKEHR_COMMANDER_ATTRIBUTE, commander);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        ServletContext ctx = servletContextEvent.getServletContext();

        // Stop the verkehr publisher.
        VerkehrPublisher publisher =
                (VerkehrPublisher) ctx.getAttribute(VERKEHR_PUBLISHER_ATTRIBUTE);
        assert publisher != null;
        publisher.stop();

        // Stop the verkehr commander.
        VerkehrCommander commander =
                (VerkehrCommander) ctx.getAttribute(VERKEHR_COMMANDER_ATTRIBUTE);
        assert commander != null;
        commander.stop();
    }
}
