/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.core;

import com.aerofs.base.BaseParam.Cacert;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.URLConnection;

public class URLConnectionConfigurator implements IURLConnectionConfigurator
{
    public static final IURLConnectionConfigurator CONNECTION_CONFIGURATOR =
            new URLConnectionConfigurator();

    private @Nullable SSLSocketFactory _sslSocketFactory;

    // FIXME: jP : Should we just use Cacert.CACERT? this implementation uses /etc/ssl/Aerofs_CA.pem
    // TODO (MP) make final when fallbackToOldImplementation() is removed.
    private SSLEngineFactory _factory = new SSLEngineFactory(Mode.Client, Platform.Desktop,
            null, new FileBasedCertificateProvider(Cacert.FILE), null);

    @Override
    public void configure(URLConnection connection)
            throws Throwable
    {
        if (_sslSocketFactory == null) {
            _sslSocketFactory = _factory.getSSLContext().getSocketFactory();
        }

        HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
        httpsConnection.setSSLSocketFactory(_sslSocketFactory);
    }

    @Override
    public void fallbackToOldImplementation()
    {
        _factory = new SSLEngineFactory(Mode.Client, Platform.Desktop, null, null, null);
        _sslSocketFactory = null;
    }
}