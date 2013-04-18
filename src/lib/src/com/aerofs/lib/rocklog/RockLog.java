/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.InjectableCfg;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;


public class RockLog
{
    private static final Logger l = Loggers.getLogger(RockLog.class);
    private static final int SOCKET_TIMEOUT = (int) (10 * C.SEC);
    private static final String DEFAULT_ROCKLOG_URL = "http://rocklog.aerofs.com";

    private final String _rocklogUrl;
    private final InjectableCfg _cfg;

    @Inject
    public RockLog()
    {
        this(DEFAULT_ROCKLOG_URL, new InjectableCfg());
    }

    RockLog(String rocklogUrl, InjectableCfg cfg)
    {
        _rocklogUrl = rocklogUrl;
        _cfg = cfg;
    }

    /**
     * The defect name allows us to easily search and aggregate defects in RockLog.
     *
     * How to pick a good defect name:
     *
     * - NO SPACES
     * - Short string that describes what component failed
     * - Use dots to create hierarchies
     *
     * Good names:
     * "daemon.linker.someMethod", "system.nolaunch"
     *
     * Bad names:
     * "Name With Spaces", "daemon.linker.someMethod_failed" <-- "failed" is redundant
     */
    public Defect newDefect(String name)
    {
        return new Defect(this, _cfg, name);
    }

    public Metrics newMetrics()
    {
        return new Metrics(this, _cfg);
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

    boolean send(RockLogMessage message)
    {
        try {
            rpc(message.getJSON(), _rocklogUrl + message.getURLPath());
            return true;
        } catch (Throwable e) {
            l.warn("fail send RockLog message: {}", e.toString()); // we don't want the stack trace
            return false;
        }
    }

    private void rpc(String data, String url) throws Exception
    {
        HttpURLConnection rocklogConnection = getRockLogConnection(url);
        BaseUtil.httpRequest(rocklogConnection, data);
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
}
