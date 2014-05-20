package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.lib.ChannelFactories;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.guice.GuiceUtil;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.restless.Configuration;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.internal.Scoping;
import org.jboss.netty.util.Timer;

import java.net.URI;

import static com.aerofs.base.config.ConfigurationProperties.*;

public class RestModule extends AbstractModule
{
    private TokenVerifier verifier;

    public RestModule() {}

    public RestModule(TokenVerifier _verifier)
    {
        verifier = _verifier;
    }

    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        GuiceUtil.multibind(binder(), ICoreEventHandlerRegistrar.class,
                RestCoreEventHandlerRegistar.class);

        bind(Configuration.class).to(RestConfiguration.class);
    }

    @Provides
    public TokenVerifier providesVerifier(CfgCACertificateProvider cacert, Timer timer)
    {
        if (verifier == null) {
            verifier = new TokenVerifier(
                    getStringProperty("daemon.oauth.id", "oauth-havre"),
                    getStringProperty("daemon.oauth.secret", "i-am-not-a-restful-secret"),
                    URI.create(getStringProperty("daemon.oauth.url", "https://api.aerofs.com:4433/auth/tokeninfo")),
                    timer,
                    cacert,
                    ChannelFactories.getClientChannelFactory()
            );
        }
        return verifier;
    }
}
