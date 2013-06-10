/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.InjectableCfg;
import com.google.common.net.HttpHeaders;
import com.netflix.config.DynamicStringProperty;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;


public class RockLog
{
    private static final Logger l = Loggers.getLogger(RockLog.class);
    private static final int SOCKET_TIMEOUT = (int) (10 * C.SEC);
    private static final DynamicStringProperty ROCKLOG_URL =
            new DynamicStringProperty("lib.rocklog.url", "http://rocklog.aerofs.com");

    private final String _rocklogUrl;
    private final InjectableCfg _cfg;

    @Inject
    public RockLog()
    {
        this(ROCKLOG_URL.get(), new InjectableCfg());
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

    void send(final Defect defect)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                rpc(defect);
            }
        },"rocklog-send").start();
    }

    boolean rpc(Defect defect)
    {
        try {
            String url = _rocklogUrl + defect.getURLPath();
            HttpURLConnection rocklogConnection = getRockLogConnection(url);
            BaseUtil.httpRequest(rocklogConnection, defect.getJSON());
            return true;
        } catch (Throwable e) {
            l.warn("fail send RockLog message: {}", e.toString()); // we don't want the stack trace
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
}
