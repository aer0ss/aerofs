/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * An interface to provide private keys and certificates. Used as a tool to de-dup code between
 * verkehr tests and the daemon.
 */
public interface IPrivateKeyProvider
{
    public PrivateKey getPrivateKey()
            throws IOException;

    public X509Certificate getCert()
            throws CertificateException, IOException;
}
