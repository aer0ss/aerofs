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

import java.net.URI;

import static com.aerofs.base.config.ConfigurationProperties.*;

public class RestModule extends AbstractModule
{
    private TokenVerifier verifier;

    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        GuiceUtil.multibind(binder(), ICoreEventHandlerRegistrar.class,
                RestCoreEventHandlerRegistar.class);

        bind(Configuration.class).to(RestConfiguration.class);
    }

    @Provides
    public TokenVerifier providesVerifier(CfgCACertificateProvider cacert)
    {
        if (verifier == null) {
            verifier = new TokenVerifier(
                    getStringProperty("daemon.oauth.id", ""),
                    getStringProperty("daemon.oauth.secret", ""),
                    URI.create(getStringProperty("daemon.oauth.url", "https://unified.syncfs.com/auth")),
                    cacert,
                    ChannelFactories.getClientChannelFactory()
            );
        }
        return verifier;
    }
}
