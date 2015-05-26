package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.tunnel.TunnelClient;
import com.google.inject.Inject;
import org.jboss.netty.util.Timer;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.lib.NioChannelFactories.getClientChannelFactory;

public class RestTunnelClient extends TunnelClient
{
    @Inject
    public RestTunnelClient(CfgLocalUser user, CfgLocalDID did, Timer timer,
            ClientSSLEngineFactory sslEngineFactory, final RestService service)
    {
        super(getStringProperty("api.tunnel.host", "api.aerofs.com"),
                getIntegerProperty("api.tunnel.port", 8084), user.get(), did.get(),
                getClientChannelFactory(), sslEngineFactory, service::getSpecializedPipeline, timer);
    }

    @Override
    public String toString()
    {
        return "REST tunnel";
    }
}