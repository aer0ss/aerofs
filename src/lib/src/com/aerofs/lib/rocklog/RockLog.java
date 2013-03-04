/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.InjectableCfg;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.net.HttpURLConnection.HTTP_OK;

public class RockLog
{
    /**
     * Describes what high-level grouping (desktop-client, server, mobile) RockLog
     * is being instantiated in. This allows us to properly collate metrics on the server.
     */
    public static enum BaseComponent
    {
        CLIENT
        {
            @Override
            public String toString()
            {
                return "client";
            }
        },
        SERVER
        {
            @Override
            public String toString()
            {
                return "server";
            }
        }
    }

    //
    // constants
    //

    private static final int SOCKET_TIMEOUT = (int) (10 * C.SEC);
    private static final String ROCKLOG_URL = "http://rocklog.aerofs.com";
    private static final InjectableCfg _cfg = new InjectableCfg();

    //
    // singleton instance
    //

    private static RockLog _instance;

    //
    // per-instance values (technically l is shared, but, whatever)
    //

    private static final Logger l = Loggers.getLogger(RockLog.class);

    private final String _prefix;

    /*
    TODO (GS)
        - do not resend automatic defects
        - zip and send logs
        - send Cfg DB in the defect
     */

    public static Defect newDefect(String name)
    {
        return new Defect(getInstance(), _cfg, name);
    }

    public static Event newEvent(EventType event)
    {
        return new Event(getInstance(), _cfg, event);
    }

    public static Metrics newMetrics()
    {
        return new Metrics(getInstance(), _cfg);
    }

    /**
     * call in single-threaded mode only
     */
    public static void init_(BaseComponent component)
    {
        if (_instance == null) {
            _instance = new RockLog(component.toString());
        }
    }

    static RockLog getInstance()
    {
        return checkNotNull(_instance);
    }

    private RockLog(String prefix)  // prevent explicit initialization
    {
        _prefix = prefix;
    }

    void sendAsync(final RockLogMessage message)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                send(message);
            }
        },"rocklog-send").start();
    }

    void send(RockLogMessage message)
    {
        try {
            l.trace("send RockLog message...");
            rpc(message.getJSON().getBytes(), ROCKLOG_URL + message.getURLPath());
        } catch (Throwable e) {
            l.warn("fail send RockLog message: " + Util.e(e, IOException.class));
        }
    }

    private void rpc(byte[] data, String url) throws Exception
    {
        HttpURLConnection rocklogConnection = getRockLogConnection(url, data.length);

        try {
            send(rocklogConnection, data);
            recv(rocklogConnection);
        } finally {
            rocklogConnection.disconnect();
        }
    }

    private HttpURLConnection getRockLogConnection(String url, int contentLength)
            throws IOException
    {
        URL rocklogURL = new URL(url);

        HttpURLConnection rocklogConnection = (HttpURLConnection) rocklogURL.openConnection();
        rocklogConnection.setUseCaches(false);
        rocklogConnection.setConnectTimeout(SOCKET_TIMEOUT);
        rocklogConnection.setReadTimeout(SOCKET_TIMEOUT);
        rocklogConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        rocklogConnection.setFixedLengthStreamingMode(contentLength);
        rocklogConnection.setDoOutput(true);
        rocklogConnection.connect();

        return rocklogConnection;
    }

    private void send(HttpURLConnection conn, byte[] requestBody) throws IOException
    {
        OutputStream os = null;
        try {
            os = conn.getOutputStream();
            os.write(requestBody);
        } finally {
            if (os != null) os.close();
        }
    }

    private void recv(HttpURLConnection conn) throws IOException
    {
        int code = conn.getResponseCode();
        if (code != HTTP_OK) {
            throw new IOException("fail send RockLog message code: " + code);
        }

        DataInputStream is = null;
        byte[] responseBuffer = new byte[1024];
        try {
            is = new DataInputStream(new BufferedInputStream(conn.getInputStream()));
            // doing it this way (i.e. not looking at content-length) handles gzipped input properly
            // see: http://developer.android.com/reference/java/net/HttpURLConnection.html
            while (is.read() != -1) {
                // noinspection ResultOfMethodCallIgnored
                is.read(responseBuffer); // read the full response, but ignore it
            }
        } finally {
            if (is != null) is.close();
        }
    }

    String getPrefix()
    {
        return _prefix;
    }
}
