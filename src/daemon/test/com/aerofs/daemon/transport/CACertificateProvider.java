/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.testlib.SecTestUtil;
import org.bouncycastle.operator.OperatorCreationException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public final class CACertificateProvider implements ICertificateProvider
{
    private final Certificate caCertificate;

    public CACertificateProvider(SecureRandom secureRandom, String caName, KeyPair caKeyPair)
            throws CertificateException, OperatorCreationException, IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException, SignatureException // [sigh] definitely bad practice
    {
        this.caCertificate = SecTestUtil.generateCertificate(caName, caName, caKeyPair.getPublic(),
                caKeyPair.getPrivate(), secureRandom, true);
    }

    @Nonnull
    @Override
    public Certificate getCert()
    {
        return this.caCertificate;
    }
}
