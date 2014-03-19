/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.google.inject.Inject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;

public class ServerSSLEngineFactory extends SSLEngineFactory
{
    @Inject
    public ServerSSLEngineFactory(CfgKeyManagersProvider keyProvider, CfgCACertificateProvider trustedCA)
    {
        super(Mode.Server, Platform.Desktop, keyProvider, trustedCA, null);
    }

    @Override
    protected void onSSLContextCreated(SSLContext context)
    {
        // see comments in ClientSSLEngineFactory about constants
        SSLSessionContext sessionContext = context.getServerSessionContext();
        sessionContext.setSessionCacheSize(30); // only 30 sessions
        sessionContext.setSessionTimeout(1800); // 30 minutes
    }
}
