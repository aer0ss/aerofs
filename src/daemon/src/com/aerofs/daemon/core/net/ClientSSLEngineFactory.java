/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.google.inject.Inject;

public class ClientSSLEngineFactory extends SSLEngineFactory
{
    @Inject
    public ClientSSLEngineFactory(CfgKeyManagersProvider keyProvider, CfgCACertificateProvider trustedCA)
    {
        super(Mode.Client, Platform.Desktop, keyProvider, trustedCA, null);
    }
}
