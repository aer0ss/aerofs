/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * An interface to provide a certificate.
 */
public interface ICertificateProvider
{
    public @Nonnull X509Certificate getCert() throws CertificateException, IOException;
}
