/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.testlib;

import com.aerofs.base.Base64;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.google.common.base.Preconditions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * An SSL engine factory that uses a generated self-signed certificate.
 */
public class SimpleSslEngineFactory
{

    public SimpleSslEngineFactory() throws Exception
    {
        KeyPair caKeyPair = generateKeyPair(new SecureRandom());
        ICertificateProvider caCertificateProvider = new CertProvider(caKeyPair);
        _cert = "-----BEGIN CERTIFICATE-----\n"
                + Base64.encodeBytes(caCertificateProvider.getCert().getEncoded())
                + "\n-----END CERTIFICATE-----";

        _sslEngineFactory = new SSLEngineFactory(Mode.Server, Platform.Desktop,
                new KeyProvider(caKeyPair, caCertificateProvider.getCert()), null, null);
        _sslEngineFactory.getSSLEngine().setUseClientMode(false);
    }

    public String getCertificate() { return _cert; }
    public SSLContext getSSLContext() throws IOException, GeneralSecurityException
    {
        return _sslEngineFactory.getSSLContext();
    }

    private KeyPair generateKeyPair(SecureRandom secureRandom) throws NoSuchAlgorithmException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024, secureRandom);
        return generator.generateKeyPair();
    }

    private static X509Certificate generateCertificate(String issuerName, String subjectName,
            PublicKey subjectPublicKey, PrivateKey caPrivateKey, SecureRandom secureRandom,
            boolean isCA) throws IOException, CertificateException, OperatorCreationException
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
            certificateBuilder.addExtension(Extension.basicConstraints, true, caConstraint);
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA")
                .setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
                .build(caPrivateKey);

        X509CertificateHolder certificateHolder = certificateBuilder.build(signer);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate)certificateFactory
                .generateCertificate(new ByteArrayInputStream(certificateHolder.getEncoded()));
    }

    static class CertProvider implements ICertificateProvider
    {
        CertProvider(KeyPair caKeyPair) throws CertificateException, OperatorCreationException,
                IOException
        {
            this.caCertificate = generateCertificate("localhost", "localhost",
                    caKeyPair.getPublic(), caKeyPair.getPrivate(), new SecureRandom(), true);
        }
        @Nonnull
        @Override
        public X509Certificate getCert() throws CertificateException, IOException
        {
            return caCertificate;
        }

        private final X509Certificate caCertificate;
    }

    static class KeyProvider implements IPrivateKeyProvider
    {
        KeyProvider(KeyPair keyPair, X509Certificate cert) {
            _keyPair = keyPair;
            _cert = cert;
        }
        @Nonnull
        @Override
        public PrivateKey getPrivateKey() throws IOException
        {
            return _keyPair.getPrivate();
        }

        @Nonnull
        @Override
        public X509Certificate getCert() throws CertificateException, IOException
        {
            return _cert;
        }

        private KeyPair _keyPair;
        private X509Certificate _cert;
    }

    private String _cert;
    private final SSLEngineFactory _sslEngineFactory;
}
