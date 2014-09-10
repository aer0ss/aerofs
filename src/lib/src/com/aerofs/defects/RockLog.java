/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import org.slf4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;

public class RockLog
{
    private static final Logger l = Loggers.getLogger(RockLog.class);
    private static final int SOCKET_TIMEOUT = (int) (10 * C.SEC);

    private final String _rocklogUrl;
    private final Gson _gson;

    public RockLog(String rocklogUrl, Gson gson)
    {
        _rocklogUrl = rocklogUrl;
        _gson = gson;
    }

    public void send(String resourceURL, Object data)
    {
        rpc(resourceURL, data);
    }

    boolean rpc(String resourceURL, Object data)
    {
        try {
            HttpURLConnection rocklogConnection = getRockLogConnection(_rocklogUrl + resourceURL);
            BaseUtil.httpRequest(rocklogConnection, _gson.toJson(data));
            return true;
        } catch (Throwable e) {
            l.warn("fail to send RockLog message: {}", e.toString()); // we don't want the stack trace
            return false;
        }
    }

    private HttpURLConnection getRockLogConnection(String url)
            throws IOException, GeneralSecurityException
    {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        // FIXME (AT): we need to support http connection for TestRockLog
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection)conn).setSSLSocketFactory(createSSLContext().getSocketFactory());
        }

        conn.setUseCaches(false);
        conn.setConnectTimeout(SOCKET_TIMEOUT);
        conn.setReadTimeout(SOCKET_TIMEOUT);
        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        conn.setDoOutput(true);
        conn.connect();

        return conn;
    }

    private SSLContext createSSLContext()
            throws IOException, GeneralSecurityException
    {
        return new SSLEngineFactory(Mode.Client, Platform.Desktop, null,
                new CfgCACertificateProvider(), null)
                .getSSLContext();
    }

    public static class Noop extends RockLog
    {
        public Noop()
        {
            super("", null);
        }

        @Override
        public void send(String resourceURL, Object data)
        {
            // noop
        }
    }
}
