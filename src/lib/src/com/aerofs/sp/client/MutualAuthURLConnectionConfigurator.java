/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.client;

import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.URLConnection;

public class MutualAuthURLConnectionConfigurator implements IURLConnectionConfigurator
{
    private @Nullable SSLSocketFactory _sslSocketFactory;

    // TODO (MP) make final when fallbackToOldImplementation() is removed.
    private SSLEngineFactory _factory = new SSLEngineFactory(Mode.Client, Platform.Desktop,
            new CfgKeyManagersProvider(), new CfgCACertificateProvider(), null);

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
        _factory = new SSLEngineFactory(Mode.Client, Platform.Desktop, new CfgKeyManagersProvider(),
                null, null);

        _sslSocketFactory = null;
    }
}