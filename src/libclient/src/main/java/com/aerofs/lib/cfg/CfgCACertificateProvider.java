/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.aerofs.base.ssl.ICertificateProvider;
import com.google.inject.Inject;

public class CfgCACertificateProvider implements ICertificateProvider
{
    @Override
    public X509Certificate getCert()
            throws IOException, CertificateException
    {
        return BaseCfg.getInstance().cacert();
    }
}
