/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.rocklog;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.InjectableCfg;
import com.google.common.collect.Queues;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;


public class RockLog
{
    private static final Logger l = Loggers.getLogger(RockLog.class);
    private static final int SOCKET_TIMEOUT = (int) (10 * C.SEC);

    private final String _rocklogUrl;
    private final InjectableCfg _cfg;

    private final Executor _e = new ThreadPoolExecutor(
            0, 1,                                           // at most 1 thread
            1, TimeUnit.SECONDS,                            // idle thread TTL
            Queues.<Runnable>newLinkedBlockingQueue(100));  // bounded event queue

    @Inject
    public RockLog()
    {
        this(getStringProperty("lib.rocklog.url", "http://rocklog.aerofs.com"), new InjectableCfg());
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
        _e.execute(new Runnable()
        {
            @Override
            public void run()
            {
                rpc(defect);
            }
        });
    }

    boolean rpc(Defect defect)
    {
        if (_rocklogUrl == null) return false;
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
