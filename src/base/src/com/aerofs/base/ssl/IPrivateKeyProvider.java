/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * An interface to provide private keys and certificates.
 */
public interface IPrivateKeyProvider
{
    public PrivateKey getPrivateKey() throws IOException;
    public Certificate getCert() throws CertificateException, IOException;
}
