/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.NoopConnectionListener;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.lang.Short.parseShort;

/**
 * An utility class used to create and provision various SP resources. Depends on SPParam and
 * BaseParam.
 *
 * Currently shared between SPServlet and DryadServlet.
 */
public class SPVerkehrClientFactory
{
    private final Executor              _cachedThreadPool;
    private final ICertificateProvider  _cacertProvider;
    private final HashedWheelTimer      _timer;

    public SPVerkehrClientFactory(ServletContext context)
    {
        _cachedThreadPool = Executors.newCachedThreadPool();
        _cacertProvider = createCacertProvider(context);
        _timer = new HashedWheelTimer();
    }

    // only differs from createVerkehrPublisher() in port and factory class
    public VerkehrAdmin createVerkehrAdmin()
    {
        return new com.aerofs.verkehr.client.lib.admin.ClientFactory(
                Verkehr.HOST, parseShort(Verkehr.ADMIN_PORT),
                _cachedThreadPool, _cachedThreadPool, _cacertProvider,
                SPParam.VERKEHR_RECONNECT_DELAY, SPParam.VERKEHR_ACK_TIMEOUT, _timer,
                new NoopConnectionListener(), sameThreadExecutor()).create();
    }

    // only differs from createVerkehrAdmin() in port and factory class
    public VerkehrPublisher createVerkehrPublisher()
    {
        return new com.aerofs.verkehr.client.lib.publisher.ClientFactory(
                Verkehr.HOST, parseShort(Verkehr.PUBLISH_PORT),
                _cachedThreadPool, _cachedThreadPool, _cacertProvider,
                SPParam.VERKEHR_RECONNECT_DELAY, SPParam.VERKEHR_ACK_TIMEOUT, _timer,
                new NoopConnectionListener(), sameThreadExecutor()).create();
    }

    private ICertificateProvider createCacertProvider(ServletContext context)
    {
        String cacertPath = context.getInitParameter(SPParam.VERKEHR_CACERT_INIT_PARAMETER);
        if (!new File(cacertPath).isAbsolute()) {
            cacertPath = Util.join(context.getRealPath("/"), "WEB-INF", cacertPath);
        }
        return new FileBasedCertificateProvider(cacertPath);
    }
}
