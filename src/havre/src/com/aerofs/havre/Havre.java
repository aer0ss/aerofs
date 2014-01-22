package com.aerofs.havre;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.FileBasedKeyManagersProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.havre.auth.OAuthAuthenticator;
import com.aerofs.havre.proxy.HttpProxyServer;
import com.aerofs.havre.tunnel.EndpointVersionDetector;
import com.aerofs.havre.tunnel.TunnelEndpointConnector;
import com.aerofs.tunnel.TunnelServer;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Properties;

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

    public Havre(final UserID user, DID did, @Nullable IPrivateKeyProvider proxyKey,
            IPrivateKeyProvider tunnelKey, ICertificateProvider cacert)
    {
        Timer timer = new HashedWheelTimer();
        TunnelEndpointConnector c = new TunnelEndpointConnector(new EndpointVersionDetector());
        _tunnel = new TunnelServer(new InetSocketAddress(TUNNEL_HOST, TUNNEL_PORT),
                tunnelKey, cacert, user, did, timer, c);
        _proxy = new HttpProxyServer(new InetSocketAddress(PROXY_HOST, PROXY_PORT),
                proxyKey, timer, new OAuthAuthenticator(timer, cacert), c);
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

    public int getProxyPort()
    {
        return _proxy.getListeningPort();
    }

    public static void main(String[] args) throws Exception
    {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                System.err.println("uncaught exception thd:" + t.getName()
                        + " err:" + e + " - kill system");
                e.printStackTrace(System.err);
                System.exit(1);
            }
        });

        Properties extra = new Properties();
        if (args.length > 0) extra.load(new FileInputStream(args[0]));

        ConfigurationProperties.setProperties(extra);

        // dummy daemon-like identity for CName verification when esablishing tunnel
        UserID tunnelUser = UserID.DUMMY;
        DID tunnelDevice = new DID(UniqueID.ZERO);

        IPrivateKeyProvider tunnelKey = new FileBasedKeyManagersProvider("havre.key", "havre.crt");
        ICertificateProvider cacert = new FileBasedCertificateProvider(
                getStringProperty("havre.tunnel.cacert", "cacert.pem"));

        // fail-fast if key/certs missing or invalid
        checkNotNull(tunnelKey.getCert());
        checkNotNull(tunnelKey.getPrivateKey());
        checkNotNull(cacert.getCert());

        final Havre havre = new Havre(tunnelUser, tunnelDevice, null, tunnelKey, cacert);
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
