/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.charlie;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseParam.Charlie;
import com.aerofs.base.C;
import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.net.SslURLConnectionConfigurator;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;
import java.io.DataInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.base.BaseParam.Charlie.CHARLIE_AUTH_KEY;
import static com.aerofs.base.BaseParam.Charlie.CHARLIE_AUTH_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class CharlieClient
{
    private static final Logger l = LoggerFactory.getLogger(CharlieClient.class);

    private static final long INITIAL_CHECK_IN_DELAY = 30 * C.SEC;
    private static final long CHECK_IN_INTERVAL = 60 * C.SEC;
    private static final long CONNECT_TIMEOUT = 30 * C.SEC;
    private static final long READ_TIMEOUT = 30 * C.SEC;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final UserID localUser;
    private final ScheduledExecutorService checkpointExecutor;
    private final IURLConnectionConfigurator sslConnectionConfigurator;
    private final DID localDID;

    @Inject
    public CharlieClient(
            CfgLocalUser localUser,
            CfgLocalDID localDID,
            CfgKeyManagersProvider keyProvider,
            CfgCACertificateProvider certificateProvider)
    {
        this.localUser = localUser.get();
        this.localDID = localDID.get();
        this.sslConnectionConfigurator = SslURLConnectionConfigurator.mutualAuth(
                Platform.Desktop,
                keyProvider,
                certificateProvider);

        // create our custom thread factory so that we can name threads properly
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder();
        threadFactoryBuilder.setDaemon(true);
        threadFactoryBuilder.setNameFormat("cc%d");
        threadFactoryBuilder.setPriority(Thread.NORM_PRIORITY);
        threadFactoryBuilder.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        this.checkpointExecutor = Executors.newScheduledThreadPool(3, threadFactoryBuilder.build());
    }

    public void start()
    {
        boolean set = started.compareAndSet(false, true);
        if (!set) {
            return;
        }

        checkpointExecutor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    checkInWithCharlie();
                } catch (Error e) {
                    SystemUtil.fatal(e);
                } catch (Throwable t) {
                    l.warn("fail check in with charlie err:{}", t.getMessage());
                }
            }
        }, INITIAL_CHECK_IN_DELAY, CHECK_IN_INTERVAL, MILLISECONDS);
    }

    private void checkInWithCharlie()
            throws Throwable
    {
        l.debug("beg check in with charlie");

        // create and set up the connection
        HttpsURLConnection connection = (HttpsURLConnection) Charlie.CHARLIE_URL.openConnection();
        sslConnectionConfigurator.configure(connection);
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.addRequestProperty(CHARLIE_AUTH_KEY, String.format(CHARLIE_AUTH_VALUE,
                    Base64.encodeBytes(localUser.getString().getBytes("UTF-8")),
                    localDID.toStringFormal()));
        connection.setRequestMethod("POST");
        connection.setConnectTimeout((int) CONNECT_TIMEOUT);
        connection.setReadTimeout((int) READ_TIMEOUT);

        // make an empty POST
        connection.connect();

        // read the response (but don't do anything with it)
        DataInputStream in = null;
        try {
            l.debug("fin check in with charlie code:{}", connection.getResponseCode());
            in = new DataInputStream(connection.getInputStream());
            in.readFully(new byte[connection.getContentLength()]);
        } finally {
            Closeables.close(in, true);
        }
    }
}
