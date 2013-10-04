/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.sp.authentication.LdapConfiguration.SecurityType;
import com.google.common.base.Preconditions;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import sun.misc.BASE64Encoder;

import javax.annotation.Nonnull;
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

public abstract class InMemoryServer
{
    public static KeyPair generateKeyPair(SecureRandom secureRandom)
            throws NoSuchAlgorithmException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024, secureRandom);
        return generator.generateKeyPair();
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

    /**
     * An in-memory test server that simulates a usual LDAP (unix-style) schema.
     * Add users to taste in individual test cases.
     */
    public static class LdapSchema extends InMemoryServer
    {
        public LdapSchema(boolean useTls, boolean useSsl) throws Exception
        {
            super(useTls, useSsl);
        }

        @Override
        protected void createSchema(InMemoryDirectoryServer server) throws Exception
        {
            add("dn: dc=org", "objectClass: top", "objectClass: domain", "dc: org");
            add("dn: dc=example,dc=org", "objectClass: top", "objectClass: domain",
                    "dc: example");

            add("dn: dc=users,dc=example,dc=org", "objectClass: top",
                    "objectClass: domain", "dc: users");

            add("dn: dc=roles,dc=example,dc=org", "objectClass: top",
                    "objectClass: domain", "dc: roles");
            add("dn: cn=testgroup,dc=roles,dc=example,dc=org",
                    "objectClass: groupOfUniqueNames", "cn: testgroup");
            add("dn: cn=admins,dc=roles,dc=example,dc=org",
                    "objectClass: groupOfUniqueNames", "cn: admins");
        }


    }

    abstract protected void createSchema(InMemoryDirectoryServer server) throws Exception;

    void resetConfig(LdapConfiguration cfg)
    {
        cfg.SERVER_HOST = "localhost";
        cfg.SERVER_PORT = _serverPort;
        cfg.SERVER_SECURITY = SecurityType.NONE;
        cfg.SERVER_PRINCIPAL = PRINCIPAL;
        cfg.SERVER_CREDENTIAL = CRED;

        cfg.USER_SCOPE = "sub";
        cfg.USER_BASE = "dc=users,dc=example,dc=org";
        cfg.USER_EMAIL = "mail";
        cfg.USER_FIRSTNAME = "givenName";
        cfg.USER_LASTNAME = "sn";
        cfg.USER_OBJECTCLASS = "inetOrgPerson";
        cfg.USER_RDN = "dn";
    }

    InMemoryServer(boolean useTls, boolean useSsl) throws Exception
    {
        _useSsl = useSsl;
        _useTls = useTls;
        startServer();
        createSchema(_server);
    }

    private void startServer() throws Exception
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=org");
        InMemoryListenerConfig listenerConfig;

        if (_useTls || _useSsl) {
            //
            // Generate a self-signed certificate for this server...
            //
            BASE64Encoder encoder = new BASE64Encoder();
            KeyPair caKeyPair = generateKeyPair(new SecureRandom());
            ICertificateProvider caCertificateProvider = new CertProvider(caKeyPair);
            SSLEngineFactory sslEngineFactory = new SSLEngineFactory(
                    Mode.Server, Platform.Desktop,
                    new KeyProvider(caKeyPair, caCertificateProvider.getCert()), null, null);

            _cert = "-----BEGIN CERTIFICATE-----\n"
                    + encoder.encodeBuffer(caCertificateProvider.getCert().getEncoded())
                    + "\n-----END CERTIFICATE-----";

            if (_useSsl) {
                listenerConfig = InMemoryListenerConfig.createLDAPSConfig(
                        "localhost", 0, sslEngineFactory.getSSLContext().getServerSocketFactory());
            } else {
                listenerConfig = InMemoryListenerConfig.createLDAPConfig(
                        "localhost", null, 0, sslEngineFactory.getSSLContext().getSocketFactory());
            }
        } else {
            listenerConfig = new InMemoryListenerConfig("test", null, 0, null, null, null);
        }

        config.addAdditionalBindCredentials(PRINCIPAL, CRED);
        config.setListenerConfigs(listenerConfig);
        config.setSchema(null); // do not check (attribute) schema
        config.setAuthenticationRequiredOperationTypes(config.getAllowedOperationTypes());

        _server = new InMemoryDirectoryServer(config);
        _server.startListening();
        _serverPort = _server.getListenPort();
    }

    public String getCertString()       { return _cert; }

    public void add(String... ldifLines) throws Exception { _server.add(ldifLines); }

    public void stop()                  { _server.shutDown(true); }

    private InMemoryDirectoryServer     _server;
    protected boolean                   _useSsl;
    protected boolean                   _useTls;
    protected int                       _serverPort;
    static final String                 PRINCIPAL = "cn=Admin,dc=example,dc=org";
    static final String                 CRED = "cred";

    private String                      _cert;
    static class CertProvider implements ICertificateProvider
    {
        CertProvider(KeyPair caKeyPair) throws CertificateException, OperatorCreationException, IOException
        {
            this.caCertificate = generateCertificate("localhost", "localhost",
                    caKeyPair.getPublic(), caKeyPair.getPrivate(), new SecureRandom(), true);
        }
        @Nonnull
        @Override
        public Certificate getCert() throws CertificateException, IOException
        {
            return caCertificate;
        }

        private final Certificate caCertificate;
    }

    static class KeyProvider implements IPrivateKeyProvider
    {
        KeyProvider(KeyPair keyPair, Certificate cert) {
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
        public Certificate getCert() throws CertificateException, IOException
        {
            return _cert;
        }

        private KeyPair _keyPair;
        private Certificate _cert;
    }
}
