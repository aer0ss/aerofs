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

public class ClientSSLEngineFactory extends SSLEngineFactory
{
    @Inject
    public ClientSSLEngineFactory(CfgKeyManagersProvider keyProvider, CfgCACertificateProvider trustedCA)
    {
        super(Mode.Client, Platform.Desktop, keyProvider, trustedCA, null);
    }

    @Override
    protected void onSSLContextCreated(SSLContext context)
    {
        SSLSessionContext sessionContext = context.getClientSessionContext();
        // FIXME (AG): it's not clear what the appropriate cache size and session timeout is
        // it has to be long enough that a transient disconnect will allow for a fast handshake
        // and not too long for them to sit around for ever
        sessionContext.setSessionCacheSize(30); // 30 sessions
        sessionContext.setSessionTimeout(1800); // 30 minutes
    }
}
