/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * An interface to provide private keys and certificates.
 */
public interface IPrivateKeyProvider
{
    public @Nonnull PrivateKey getPrivateKey() throws IOException;
    public @Nonnull X509Certificate getCert() throws CertificateException, IOException;
}
