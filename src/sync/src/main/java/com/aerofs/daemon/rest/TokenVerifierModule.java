package com.aerofs.daemon.rest;

import com.aerofs.lib.NioChannelFactories;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.oauth.TokenVerifier;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.jboss.netty.util.Timer;

import java.net.URI;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class TokenVerifierModule extends AbstractModule
{
    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public TokenVerifier providesVerifier(CfgCACertificateProvider cacert, Timer timer)
    {
        return new TokenVerifier(
                getStringProperty("daemon.oauth.id", "oauth-havre"),
                getStringProperty("daemon.oauth.secret", "i-am-not-a-restful-secret"),
                URI.create(getStringProperty("daemon.oauth.url", "https://api.aerofs.com:4433/auth/tokeninfo")),
                timer,
                cacert,
                NioChannelFactories.getClientChannelFactory()
        );
    }
}
