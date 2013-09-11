/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.google.common.base.Preconditions;
import org.bouncycastle.operator.OperatorCreationException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public final class PrivateKeyProvider implements IPrivateKeyProvider
{
    private final KeyPair keyPair;
    private final Certificate certificate;

    public PrivateKeyProvider(SecureRandom secureRandom, String subjectName, String caName, Certificate caCertificate, PrivateKey caPrivateKey)
            throws CertificateException, OperatorCreationException, IOException, NoSuchAlgorithmException, InvalidAlgorithmParameterException // [sigh] definitely bad practice
    {
        this.keyPair = SecTestUtil.generateKeyPair(secureRandom);
        this.certificate = SecTestUtil.generateCertificate(caName, subjectName, keyPair.getPublic(), caPrivateKey, secureRandom, false);

        Preconditions.checkState(BaseSecUtil.signingPathExists((X509Certificate)certificate, (X509Certificate)caCertificate));
    }

    @Nonnull
    @Override
    public PrivateKey getPrivateKey()
    {
        return keyPair.getPrivate();
    }

    @Nonnull
    @Override
    public Certificate getCert()
    {
        return certificate;
    }
}
