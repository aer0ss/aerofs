/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.google.common.base.Preconditions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Date;

public abstract class SecTestUtil
{
    private SecTestUtil()
    {
        // private to enforce uninstantiability
    }

    public static KeyPair generateKeyPair(SecureRandom secureRandom)
            throws NoSuchAlgorithmException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024, secureRandom);
        return generator.generateKeyPair();
    }

    public static KeyPair generateKeyPairNoCheckedThrow(SecureRandom secureRandom)
    {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024, secureRandom);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException();
        }
    }

    public static Certificate generateCertificate(String issuerName, String subjectName, PublicKey subjectPublicKey, PrivateKey caPrivateKey, SecureRandom secureRandom, boolean isCA)
            throws IOException, OperatorCreationException, CertificateException
    {
        Preconditions.checkArgument(!issuerName.isEmpty());
        Preconditions.checkArgument(!subjectName.isEmpty());

        if (isCA) {
            Preconditions.checkArgument(issuerName.equalsIgnoreCase(subjectName));
        }

        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                new X500Name(String.format("CN=%s", issuerName)),
                BigInteger.valueOf(secureRandom.nextLong()),
                new Date(System.currentTimeMillis() - 120000), // 2 mins in the past
                new Date(System.currentTimeMillis() + 300000), // 5 mins after creation
                new X500Name(String.format("CN=%s", subjectName)),
                subjectPublicKey
        );

        if (isCA) {
            BasicConstraints caConstraint = new BasicConstraints(true);
            certificateBuilder.addExtension(X509Extension.basicConstraints, true, caConstraint);
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA")
                .setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
                .build(caPrivateKey);

        X509CertificateHolder certificateHolder = certificateBuilder.build(signer);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return certificateFactory.generateCertificate(new ByteArrayInputStream(certificateHolder.getEncoded()));
    }
}
