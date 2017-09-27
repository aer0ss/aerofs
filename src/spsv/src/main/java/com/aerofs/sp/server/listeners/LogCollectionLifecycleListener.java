/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.listeners;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.ssmp.SSMPConnection;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import javax.servlet.ServletContextEvent;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.sp.server.lib.SPParam.SSMP_CLIENT_ATTRIBUTE;

public class LogCollectionLifecycleListener extends ConfigurationLifecycleListener
{
    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        super.contextInitialized(event);

        event.getServletContext().setAttribute(SSMP_CLIENT_ATTRIBUTE, createSSMPConnection());
    }

    private static SSMPConnection createSSMPConnection()
    {
        Executor executor = Executors.newCachedThreadPool();
        String secret = AeroService.loadDeploymentSecret();
        ICertificateProvider cacert = new StringBasedCertificateProvider(
                getStringProperty("config.loader.base_ca_certificate"));
        return new SSMPConnection(secret,
                InetSocketAddress.createUnresolved("lipwig.service", 8787),
                TimerUtil.getGlobalTimer(),
                new NioClientSocketChannelFactory(executor, executor, 1, 2),
                new SSLEngineFactory(Mode.Client, Platform.Desktop, null, cacert, null)
                        ::newSslHandler
        );
    }

    @Override
    public void contextDestroyed(ServletContextEvent event)
    {
        SSMPConnection ssmp = (SSMPConnection) event.getServletContext()
                .getAttribute(SSMP_CLIENT_ATTRIBUTE);

        if (ssmp != null) {
            ssmp.stop();
        }

        super.contextDestroyed(event);
    }
}
