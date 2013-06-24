/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.BaseSecUtil;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public class FileBasedCertificateProvider implements ICertificateProvider
{
    // (AT) why is this static? wouldn't we accidentally use the wrong cert
    //   if we have 2 separate instances of the provider on the same process?
    private static Certificate _cacert;
    private final String _certFilename;

    public FileBasedCertificateProvider(String certFilename)
    {
        _certFilename = certFilename;
    }

    @Override
    public Certificate getCert() throws CertificateException, IOException
    {
        if (_cacert == null) {
            _cacert = BaseSecUtil.newCertificateFromFile(_certFilename);
        }

        return _cacert;
    }
}
