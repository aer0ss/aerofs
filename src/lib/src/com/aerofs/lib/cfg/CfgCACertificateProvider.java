/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import com.aerofs.base.ssl.ICertificateProvider;

public class CfgCACertificateProvider implements ICertificateProvider
{
    @Override
    public Certificate getCert()
            throws IOException, CertificateException
    {
        return Cfg.cacert();
    }
}
