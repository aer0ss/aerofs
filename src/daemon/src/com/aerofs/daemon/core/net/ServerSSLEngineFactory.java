/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.google.inject.Inject;

public class ServerSSLEngineFactory extends SSLEngineFactory
{
    @Inject
    public ServerSSLEngineFactory(CfgKeyManagersProvider keyProvider, CfgCACertificateProvider trustedCA)
    {
        super(Mode.Server, Platform.Desktop, keyProvider, trustedCA, null);
    }
}
