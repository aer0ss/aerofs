package com.aerofs.havre;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ssl.*;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.havre.auth.OAuthAuthenticator;
import com.aerofs.havre.proxy.HttpProxyServer;
import com.aerofs.havre.tunnel.EndpointVersionDetector;
import com.aerofs.havre.tunnel.TunnelEndpointConnector;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.tunnel.ITunnelConnectionListener;
import com.aerofs.tunnel.TunnelServer;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Properties;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Havre (french for haven) is a simple public gateway to the REST API
 *
 * It keeps track of available REST-enabled daemon by accepting heartbeats from them and
 * forwards incoming REST calls to the appropriate client.
 */
public class Havre
{
    static {
        Loggers.init();
    }

    private static final Logger l = Loggers.getLogger(Havre.class);

    public final int PROXY_PORT = getIntegerProperty("havre.proxy.port", 8083);
    public final String PROXY_HOST = getStringProperty("havre.proxy.host", "localhost");
    public final int TUNNEL_PORT = getIntegerProperty("havre.tunnel.port", 8084);
    public final String TUNNEL_HOST = getStringProperty("havre.tunnel.host", "localhost");

    private final HttpProxyServer _proxy;
    private final TunnelServer _tunnel;
    private final TunnelEndpointConnector _endpointConnector;

    public Havre(final UserID user, DID did, @Nullable IPrivateKeyProvider proxyKey,
            IPrivateKeyProvider tunnelKey, ICertificateProvider cacert, Timer timer,
            TokenVerifier verifier, @Nullable RequestRouter router)
    {
        _endpointConnector = new TunnelEndpointConnector(new EndpointVersionDetector());
        _tunnel = new TunnelServer(new InetSocketAddress(TUNNEL_HOST, TUNNEL_PORT),
                tunnelKey, cacert, user, did, timer, _endpointConnector);
        _proxy = new HttpProxyServer(new InetSocketAddress(PROXY_HOST, PROXY_PORT),
                proxyKey, timer, new OAuthAuthenticator(verifier), _endpointConnector, router);
    }

    public void start()
    {
        _proxy.start();
        _tunnel.start();

        l.info("Havre at {} / {}", _proxy.getListeningPort(), _tunnel.getListeningPort());
    }

    public void stop()
    {
        _proxy.stop();
        _tunnel.stop();
    }

    public void setTunnelConnectionListener(ITunnelConnectionListener listener)
    {
        _endpointConnector.setListener(listener);
    }

    public int getProxyPort()
    {
        return _proxy.getListeningPort();
    }

    public int getTunnelPort()
    {
        return _tunnel.getListeningPort();
    }

    public static void main(String[] args) throws Exception
    {
        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        Properties extra = new Properties();
        if (args.length > 0) extra.load(new FileInputStream(args[0]));

        ConfigurationProperties.setProperties(extra);

        // Set the log verbosity to the level as defined in config service
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
                .setLevel(Level.toLevel(getStringProperty("havre.log.level"), Level.INFO));

        // dummy daemon-like identity for CName verification when establishing tunnel
        UserID tunnelUser = UserID.DUMMY;
        DID tunnelDevice = new DID(UniqueID.ZERO);

        IPrivateKeyProvider tunnelKey = new FileBasedKeyManagersProvider("havre.key", "havre.crt");
        ICertificateProvider cacert = new FileBasedCertificateProvider(
                getStringProperty("havre.tunnel.cacert", "cacert.pem"));

        // fail-fast if key/certs missing or invalid
        checkNotNull(tunnelKey.getCert());
        checkNotNull(tunnelKey.getPrivateKey());
        checkNotNull(cacert.getCert());

        Timer timer = new HashedWheelTimer();
        ClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory();
        TokenVerifier verifier = new TokenVerifier(getStringProperty("havre.oauth.id"),
                getStringProperty("havre.oauth.secret"),
                URI.create("http://sparta.service:8700/tokeninfo"),
                timer, cacert, channelFactory);

        String secret = AeroService.loadDeploymentSecret();
        URI locations = URI.create(getStringProperty("havre.locations.url") + "/locations/filter");

        RequestRouter router = new RequestRouter(locations, channelFactory, timer, secret);

        SSMPConnection ssmp = new SSMPConnection(SSMPIdentifier.fromInternal("havre"), secret,
                new InetSocketAddress("lipwig.service", 8787), timer, channelFactory,
                new SSLEngineFactory(Mode.Client, Platform.Desktop, null, cacert, null)::newSslHandler);
        ssmp.start();
        ssmp.addEventHandler(router);

        final Havre havre = new Havre(tunnelUser, tunnelDevice, null, tunnelKey, cacert, timer,
                verifier, router);
        havre.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run()
            {
                havre.stop();
            }
        });
    }
}
