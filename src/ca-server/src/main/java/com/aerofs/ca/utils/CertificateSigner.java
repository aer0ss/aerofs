package com.aerofs.ca.utils;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

public class CertificateSigner {

    private X509CertificateHolder _caCert;
    private PrivateKey _caKey;
    // X509ExtensionUtils class is not threadsafe, but since it's only used for a short period in making or creating a
    // certificate, we choose to only make using _extensionUtils mutually exclusive instead of making the entire class
    // synchronized, or making the server explicitly single-threaded
    private static final X509ExtensionUtils _extensionUtils;

    static {
        try {
            _extensionUtils = new JcaX509ExtensionUtils();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("could not initialize Certificate Authority server utils", e);
        }
    }

    protected CertificateSigner(X509CertificateHolder cert, PrivateKey privateKey)
            throws NoSuchAlgorithmException
    {
        _caCert = cert;
        _caKey = privateKey;
    }

    public static CertificateSigner certificateSignerWithKeys(KeyPair keys)
            throws NoSuchAlgorithmException, IOException, OperatorCreationException
    {
        return new CertificateSigner(createNewCACertificate(keys), keys.getPrivate());
    }

    public static CertificateSigner certificateSignerWithKeyAndCert(KeyPair keys, X509CertificateHolder cert)
            throws NoSuchAlgorithmException
    {
        return new CertificateSigner(cert, keys.getPrivate());
    }

    private static X509CertificateHolder createNewCACertificate(KeyPair keys)
            throws NoSuchAlgorithmException, IOException, OperatorCreationException
    {
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(ASN1Sequence.getInstance(keys.getPublic().getEncoded()));

        Calendar dates = Calendar.getInstance();
        // set the start time to be 24 hours before current time to avoid clock synchronization issues between server and clients
        dates.add(Calendar.DATE, -1);
        Date startDate = dates.getTime();
        dates.add(Calendar.YEAR, 10);
        Date endDate = dates.getTime();
        BigInteger serialNumber = BigInteger.valueOf(new Random().nextLong());
        X500Name caName = new X500Name("C=US, ST=California, L=San Francisco, O=aerofs.com, CN=" + randomDN());

        X509v3CertificateBuilder certGen =
                new X509v3CertificateBuilder(caName, serialNumber, startDate, endDate, caName, publicKeyInfo);
        // mark the certificate as a CA Certificate
        certGen.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        // _extensionUtils operations are not thread-safe
        synchronized (_extensionUtils) {
            certGen.addExtension(Extension.subjectKeyIdentifier, false,
                    _extensionUtils.createSubjectKeyIdentifier(publicKeyInfo));
            certGen.addExtension(Extension.authorityKeyIdentifier, false,
                    _extensionUtils.createAuthorityKeyIdentifier(publicKeyInfo));
        }

        certGen.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.cRLSign | KeyUsage.keyCertSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate());

        return certGen.build(signer);
    }

    // This ensures a (usually) unique DN for our CA to work around a chrome and firefox
    // bug. These browsers will fail weirdly when you try to create an exception for a new
    // cert that has a DN that it already knows about.
    private static String randomDN()
    {
        return "AeroFS-" + UUID.randomUUID().toString().replace("-", "");
    }

    public X509CertificateHolder signCSR(PKCS10CertificationRequest csr, long serialNo)
            throws IOException, OperatorCreationException
    {
        SubjectPublicKeyInfo publicKeyInfo = csr.getSubjectPublicKeyInfo();
        Calendar dates = Calendar.getInstance();
        // set the start time to be 24 hours before current time to avoid clock synchronization issues between server and clients
        dates.add(Calendar.DATE, -1);
        Date startDate = dates.getTime();
        dates.add(Calendar.YEAR, 1);
        Date endDate = dates.getTime();

        X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
                _caCert.getSubject(), BigInteger.valueOf(serialNo), startDate, endDate, csr.getSubject(), publicKeyInfo);
        // make the certificate a non-CA cert
        certGen.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        // _extensionUtils operations are not thread-safe
        synchronized (_extensionUtils) {
            certGen.addExtension(Extension.subjectKeyIdentifier, false,
                    _extensionUtils.createSubjectKeyIdentifier(publicKeyInfo));
            certGen.addExtension(Extension.authorityKeyIdentifier, false,
                    _extensionUtils.createAuthorityKeyIdentifier(_caCert.getSubjectPublicKeyInfo()));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(_caKey);

        return certGen.build(signer);
    }

    public X509CertificateHolder caCert()
    {
        return _caCert;
    }
}
