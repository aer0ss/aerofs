/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.BaseSecUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class StringBasedCertificateProvider implements ICertificateProvider
{
    private X509Certificate _cert;
    private final String _certData;

    public StringBasedCertificateProvider(String certData)
    {
        _certData = certData;
    }

    @Nonnull
    @Override
    public X509Certificate getCert()
            throws CertificateException, IOException
    {
        if (_cert == null) {
            _cert = BaseSecUtil.newCertificateFromString(_certData);
        }

        return _cert;
    }
}
