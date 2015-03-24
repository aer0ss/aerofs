package com.aerofs.sp.server.listeners;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.util.concurrent.MoreExecutors;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CLIENT_ATTRIBUTE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ProbeLifecycleListener
        extends ConfigurationLifecycleListener
        implements ServletContextListener
{
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        super.contextInitialized(servletContextEvent);

        ServletContext ctx = servletContextEvent.getServletContext();

        // Verkehr.
        VerkehrClient verkehrClient = createVerkehrClient();
        ctx.setAttribute(VERKEHR_CLIENT_ATTRIBUTE, verkehrClient);
    }

    private static VerkehrClient createVerkehrClient()
    {
        Executor nioExecutor = Executors.newCachedThreadPool();
        NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(nioExecutor, nioExecutor, 1, 2);
        String secret = AeroService.loadDeploymentSecret();
        return VerkehrClient.create(
                Verkehr.HOST,
                Verkehr.REST_PORT,
                MILLISECONDS.convert(30, SECONDS),
                MILLISECONDS.convert(60, SECONDS),
                () -> AeroService.getHeaderValue("probe-servlet", secret),
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
}
