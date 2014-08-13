/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

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
            throws IOException
    {
        URL rocklogURL = new URL(url);

        HttpURLConnection rocklogConnection = (HttpURLConnection) rocklogURL.openConnection();
        rocklogConnection.setUseCaches(false);
        rocklogConnection.setConnectTimeout(SOCKET_TIMEOUT);
        rocklogConnection.setReadTimeout(SOCKET_TIMEOUT);
        rocklogConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        rocklogConnection.setDoOutput(true);
        rocklogConnection.connect();

        return rocklogConnection;
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
