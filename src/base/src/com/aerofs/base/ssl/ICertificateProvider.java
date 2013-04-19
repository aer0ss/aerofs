/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * An interface to provide a certificate.
 */
public interface ICertificateProvider
{
    public @Nonnull Certificate getCert() throws CertificateException, IOException;
}
