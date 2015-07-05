/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.ssl.IPrivateKeyProvider;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Simple wrapper for Cfg credentials
 */
public class CfgKeyManagersProvider implements IPrivateKeyProvider
{
    @Override
    public PrivateKey getPrivateKey()
    {
        return Cfg.privateKey();
    }

    @Override
    public X509Certificate getCert()
    {
        return BaseCfg.getInstance().cert();
    }
}
