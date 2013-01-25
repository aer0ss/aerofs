/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

import static com.aerofs.base.BaseSecUtil.newCertificateFromFile;
import static com.aerofs.base.BaseSecUtil.newPrivateKeyFromFile;

public class FileBasedKeyManagersProvider implements IPrivateKeyProvider
{
    private final String _keyFile;
    private final String _crtFile;

    public FileBasedKeyManagersProvider(String keyFile, String crtFile)
    {
        this._keyFile = keyFile;
        this._crtFile = crtFile;
    }

    @Override
    public PrivateKey getPrivateKey()
            throws IOException
    {
        PrivateKey privateKey;

        try {
            privateKey = newPrivateKeyFromFile(_keyFile);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException(e.toString());
        }
        catch (InvalidKeySpecException e) {
            throw new IOException(e.toString());
        }

        return privateKey;
    }

    @Override
    public X509Certificate getCert()
            throws CertificateException, IOException
    {
        return (X509Certificate) newCertificateFromFile(_crtFile);
    }
}
