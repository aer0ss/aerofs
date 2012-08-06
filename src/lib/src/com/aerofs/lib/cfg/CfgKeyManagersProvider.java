/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.cfg;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ssl.AbstractKeyManagersProvider;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Simple wrapper for Cfg credentials required to instantiate the verkehr publisher.
 */
public class CfgKeyManagersProvider extends AbstractKeyManagersProvider
{
    @Override
    public PrivateKey getPrivateKey()
    {
        return Cfg.privateKey();
    }

    @Override
    public X509Certificate getCert()
            throws CertificateException, IOException
    {
        return Cfg.cert();
    }
}
