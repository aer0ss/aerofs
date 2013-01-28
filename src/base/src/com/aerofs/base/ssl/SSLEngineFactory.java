/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.Certificate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class SSLEngineFactory
{
    private static final char[] KEYSTORE_PASSWORD = "".toCharArray();
    private static final String ALGORITHM = "SunX509";
    private static final String KEYSTORE_TYPE = "JKS"; // jks = java key store
    private static final String SECURITY_TYPE = "TLS";

    private final boolean _clientMode;
    private final IPrivateKeyProvider _keyProvider;
    private final Certificate _trustedCA;
    private final CRL _crl;
    private volatile SSLContext _context;
    private final Object _contextLock = new Object();

    /**
     * A factory to create either client-side or server-side SSL engines.
     *
     * IMPORTANT: Please read the documentation below carefully
     *
     * @param clientMode   true if you want to create a client-side SSLEngine, false for server-side
     *
     * @param keyProvider  Who you are. Provides a certificate and a private key proving that
     *                     you are who you claim you are. If this parameter is null, this peer will
     *                     not be able to authenticate itself to the remote peer.
     *
     * @param trustedCA    Who you trust. If this parameter is non-null, then the remote peer's
     *                     certificate will be checked as having been issued by this CA.
     *
     *                     If this parameter is null, the behavior will be as follows:
     *                       - In server mode, any client will be accepted. (ie: no authentication
     *                       will be performed on the client)
     *                       - In client mode, any server with a valid SSL certificate issued by a
     *                       trusted CA will be accepted. (ie: the server will be authenticated,
     *                       and self-signed / bogus certificates won't be accepted.)
     *
     * @param crl          Who you distrust. If this parameter is null, no CRL check will be done.
     *                     If it is non-null, the remote peer's certificate serial number will be
     *                     checked against this certificate revokation list.
     *
     *                     Note that if trustedCA is null then crl must be null, since it makes
     *                     no sense to check if a certificate has been revoked if you're not
     *                     checking that it's a valid cert in the first place.
     */
    public SSLEngineFactory(boolean clientMode, @Nullable IPrivateKeyProvider keyProvider,
            @Nullable Certificate trustedCA, @Nullable CRL crl)
    {
        checkArgument(trustedCA != null || crl == null, "crl must be null if trustedCA is null");

        _clientMode = clientMode;
        _keyProvider = keyProvider;
        _trustedCA = trustedCA;
        _crl = crl;
    }

    /**
     * Derived classes can override this method to perform additional initialization on the SSL
     * context
     */
    protected void onSSLContextCreated(SSLContext context)
    {
    }

    public SSLEngine getSSLEngine() throws Exception
    {
        return getSSLEngineImpl(null, 0);
    }

    public SSLEngine getSSLEngine(String host, int port) throws Exception
    {
        return getSSLEngineImpl(host, port);
    }

    /**
     * Resets the SSLContext. A new SSLContext will be created automatically next time getEngine()
     * is called. You must call this method if the private key or the certificate returned by
     * your IPrivateKeyProvider changed.
     */
    public void resetContext()
    {
        synchronized (_contextLock) {
            _context = null;
        }
    }

    private void init() throws Exception
    {
        synchronized (_contextLock) {
            checkState(_context == null, "cannot initialize client ssl engine twice");

            _context = SSLContext.getInstance(SECURITY_TYPE);
            _context.init(getKeyManagers(_keyProvider), getTrustManagers(_trustedCA, _crl), null);

            onSSLContextCreated(_context);
        }
    }

    private SSLEngine getSSLEngineImpl(String host, int port) throws Exception
    {
        SSLEngine engine;

        synchronized (_contextLock) {
            if (_context == null) init();

            engine = (host != null) ? _context.createSSLEngine(host, port)
                                              : _context.createSSLEngine();
        }

        engine.setUseClientMode(_clientMode);

        // IMPORTANT
        // If we are in server mode and we have been provided with a CA cert to trust, make sure we
        // require the client to authenticate itself. Otherwise, any client will be able to connect.
        if (!_clientMode && _trustedCA != null) engine.setNeedClientAuth(true);

        return engine;
    }


    /**
     * Creates the KeyManager, which hold _this peer_'s credentials
     */
    private static KeyManager[] getKeyManagers(IPrivateKeyProvider keyProvider) throws Exception
    {
        if (keyProvider == null) return null;

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        keyStore.load(null, KEYSTORE_PASSWORD); // initialize the keystore

        // load our key in the keystore, and initialize the KeyManagerFactory with it

        keyStore.setKeyEntry("my_keys", keyProvider.getPrivateKey(), KEYSTORE_PASSWORD,
                new Certificate[]{keyProvider.getCert()});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ALGORITHM);
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

        return keyManagerFactory.getKeyManagers();
    }

    /**
     * Creates the TrustManager, which holds the certs of roots CAs you trust
     */
    private static TrustManager[] getTrustManagers(Certificate caCert, CRL crl) throws Exception
    {
        if (caCert == null) return null;

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        keyStore.load(null, KEYSTORE_PASSWORD);  // initialize the keystore

        // load the CA cert in the keystore, and initialize the TrustManagerFactory with it
        keyStore.setCertificateEntry("ca_cert", caCert);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(ALGORITHM);
        trustManagerFactory.init(keyStore);

        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        if (crl == null) return trustManagers;

        // IMPORTANT: our cert checking comes in two phases:
        // 1. Delegate to a real X509 trust manager to check if the cert is valid and has been
        //    issued by the trusted CA
        // 2. Check if its serial number has been revoked
        //
        // Below, we try and find a real X509 trust manager to be used in (1)
        X509TrustManager x509TrustManager = null;
        for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                x509TrustManager = (X509TrustManager) tm;
                break;
            }
        }

        Preconditions.checkNotNull(x509TrustManager);

        return new TrustManager[]{new CRLBasedTrustManager(x509TrustManager, crl)};
    }
}
