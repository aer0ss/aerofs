/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.BaseSecUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public class StringBasedCertificateProvider implements ICertificateProvider
{
    private Certificate _cert;
    private final String _certData;

    public StringBasedCertificateProvider(String certData)
    {
        _certData = certData;
    }

    @Nonnull
    @Override
    public Certificate getCert()
            throws CertificateException, IOException
    {
        if (_cert == null) {
            _cert = BaseSecUtil.newCertificateFromString(_certData);
        }

        return _cert;
    }
}
