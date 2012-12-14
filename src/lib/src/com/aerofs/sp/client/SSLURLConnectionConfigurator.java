package com.aerofs.sp.client;

import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.lib.cfg.Cfg;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;

public class SSLURLConnectionConfigurator implements IURLConnectionConfigurator
{
    private static final char[] KEYSTORE_PASSWORD = "".toCharArray(); // no password
    private static final String ALGORITHM = "SunX509";
    private static final String KEYSTORE_TYPE = "JKS"; // jks = java key store
    private static final String SECURITY_TYPE = "TLS";

    public static final IURLConnectionConfigurator SSL_URL_CONNECTION_CONFIGURATOR =
            new SSLURLConnectionConfigurator();

    private @Nullable SSLSocketFactory _sslSocketFactory;

    private SSLURLConnectionConfigurator()
    {
        // Nothing to do.
    }

    @Override
    public void configure(URLConnection connection)
            throws Throwable
    {
        if (_sslSocketFactory == null) {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, KEYSTORE_PASSWORD);

            keyStore.setKeyEntry("client", Cfg.privateKey(), KEYSTORE_PASSWORD,
                    new Certificate[]{Cfg.cert()});

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ALGORITHM);
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

            SSLContext context = SSLContext.getInstance(SECURITY_TYPE);
            context.init(keyManagerFactory.getKeyManagers(), null, null);

            _sslSocketFactory = context.getSocketFactory();
        }

        HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
        httpsConnection.setSSLSocketFactory(_sslSocketFactory);
    }
}