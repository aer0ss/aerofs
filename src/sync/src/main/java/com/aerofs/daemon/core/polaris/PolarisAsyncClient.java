package com.aerofs.daemon.core.polaris;

import com.aerofs.daemon.core.AsyncHttpClient;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.inject.Inject;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.Timer;

import java.net.URI;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class PolarisAsyncClient extends AsyncHttpClient {
    public static class Factory {
        @Inject CoreExecutor executor;
        @Inject CfgLocalDID did;
        @Inject CfgLocalUser user;
        @Inject Timer timer;
        @Inject ClientSocketChannelFactory channelFactory;
        @Inject ClientSSLEngineFactory sslEngineFactory;

        public PolarisAsyncClient create()
        {
            return new PolarisAsyncClient(executor, did, user, timer, channelFactory, sslEngineFactory);
        }
    }

    @Inject
    public PolarisAsyncClient(CoreExecutor executor, CfgLocalDID localDID, CfgLocalUser localUser,
                           Timer timer, ClientSocketChannelFactory channelFactory, ClientSSLEngineFactory sslEngineFactory)
    {
        super(URI.create(getStringProperty("daemon.polaris.url")),
                executor,
                new Auth(localUser.get(), localDID.get()), timer, channelFactory, sslEngineFactory);
    }
}
