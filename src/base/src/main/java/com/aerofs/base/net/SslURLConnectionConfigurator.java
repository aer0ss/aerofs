/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.net;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.URLConnection;

public class SslURLConnectionConfigurator implements IURLConnectionConfigurator
{
    private @Nullable SSLSocketFactory _sslSocketFactory;

    private final SSLEngineFactory _factory;

    private SslURLConnectionConfigurator(Platform platform, @Nullable IPrivateKeyProvider key,
            ICertificateProvider cacert)
    {
        _factory = new SSLEngineFactory(Mode.Client, platform, key, cacert, null);
    }

    @Override
    public void configure(URLConnection connection) throws Throwable
    {
        if (!connection.getURL().getProtocol().equals("https")) return;

        if (_sslSocketFactory == null) {
            _sslSocketFactory = _factory.getSSLContext().getSocketFactory();
        }

        HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
        httpsConnection.setSSLSocketFactory(_sslSocketFactory);
    }

    public static IURLConnectionConfigurator oneWayAuth(Platform platform, ICertificateProvider cacert)
    {
        return new SslURLConnectionConfigurator(platform, null, cacert);
    }

    public static IURLConnectionConfigurator mutualAuth(Platform platform, IPrivateKeyProvider key,
            ICertificateProvider cacert)
    {
        return new SslURLConnectionConfigurator(platform, key, cacert);
    }
}
