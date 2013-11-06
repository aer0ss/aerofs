package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.tunnel.TunnelClient;
import com.google.inject.Inject;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.lib.ChannelFactories.getClientChannelFactory;

public class RestTunnelClient extends TunnelClient
{
    @Inject
    public RestTunnelClient(CfgLocalUser user, CfgLocalDID did, Timer timer,
            ClientSSLEngineFactory sslEngineFactory, final RestService service)
    {
        super(tunnelAddress(), user.get(), did.get(), getClientChannelFactory(), sslEngineFactory,
                new ChannelPipelineFactory()
                {
                    @Override
                    public ChannelPipeline getPipeline()
                            throws Exception
                    {
                        return service.getSpecializedPipeline();
                    }
                }, timer);
    }

    private static InetSocketAddress tunnelAddress()
    {
        return new InetSocketAddress(
                getStringProperty("api.tunnel.host", "aerofs.com"),
                getIntegerProperty("api.tunnel.port", 8084));
    }

    @Override
    public String toString()
    {
        return "REST tunnel";
    }
}
