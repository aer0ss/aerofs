/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.net.ISslHandlerFactory;
import com.google.common.base.Preconditions;
import org.jboss.netty.handler.ssl.SslHandler;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class SSLEngineFactory implements ISslHandlerFactory
{
    public enum Mode { Client, Server }
    public enum Platform { Desktop, Android }

    private static final char[] KEYSTORE_PASSWORD = "".toCharArray();
    private static final String SECURITY_TYPE = "TLS";

    private final boolean _clientMode;
    private final String _algorithm;
    private final String _keystoreType;
    private final @Nullable IPrivateKeyProvider _keyProvider;
    private final @Nullable ICertificateProvider _trustedCA;
    private final @Nullable CRL _crl;
    private volatile SSLContext _context;
    private final Object _contextLock = new Object();

    /**
     * A factory to create either client-side or server-side SSL engines.
     *
     * IMPORTANT: Please read the documentation below carefully
     *
     * @param mode         Either Mode.Client or Mode.Server
     *
     * @param platform     Either Platform.Desktop or Platform.Android.
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
    public SSLEngineFactory(Mode mode, Platform platform, @Nullable IPrivateKeyProvider keyProvider, @Nullable ICertificateProvider trustedCA, @Nullable CRL crl)
    {
        checkArgument(trustedCA != null || crl == null, "crl must be null if trustedCA is null");

        switch (checkNotNull(mode)) {
        case Client: _clientMode = true;  break;
        case Server: _clientMode = false; break;
        default:throw new IllegalArgumentException("unknown mode: " + mode);
        }

        switch (checkNotNull(platform)) {
        case Desktop: _keystoreType = "JKS"; _algorithm = "SunX509"; break;
        case Android: _keystoreType = "BKS"; _algorithm = "X509";    break;
        default: throw new IllegalArgumentException("unknown platform: " + platform);
        }

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

    public SSLContext getSSLContext()
            throws IOException, GeneralSecurityException
    {
        synchronized (_contextLock) {
            if (_context == null) init();
            return _context;
        }
    }

    public SSLEngine getSSLEngine() throws IOException, GeneralSecurityException
    {
        return getSSLEngineImpl(null, 0);
    }

    public SSLEngine getSSLEngine(String host, int port) throws IOException, GeneralSecurityException
    {
        return getSSLEngineImpl(host, port);
    }

    /**
     * Resets the SSLContext. A new SSLContext will be created automatically next time getEngine()
     * or getSSLContext() is called. You must call this method if the private key or the certificate
     * returned by your IPrivateKeyProvider changed.
     */
    public void resetContext()
    {
        synchronized (_contextLock) {
            _context = null;
        }
    }

    private void init() throws IOException, GeneralSecurityException
    {
        synchronized (_contextLock) {
            checkState(_context == null, "cannot initialize client ssl engine twice");

            _context = SSLContext.getInstance(SECURITY_TYPE);
            _context.init(getKeyManagers(_keyProvider), getTrustManagers(_trustedCA, _crl), null);

            onSSLContextCreated(_context);
        }
    }

    private SSLEngine getSSLEngineImpl(String host, int port) throws IOException, GeneralSecurityException
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
    private KeyManager[] getKeyManagers(@Nullable IPrivateKeyProvider keyProvider)
            throws IOException, GeneralSecurityException
    {
        if (keyProvider == null) return null;

        KeyStore keyStore = KeyStore.getInstance(_keystoreType);
        keyStore.load(null, KEYSTORE_PASSWORD); // initialize the keystore

        // Load our key in the keystore, and initialize the KeyManagerFactory with it.
        checkNotNull(keyProvider.getCert());
        checkNotNull(keyProvider.getPrivateKey());

        keyStore.setKeyEntry("aerofs_private_key", keyProvider.getPrivateKey(), KEYSTORE_PASSWORD,
                new Certificate[]{keyProvider.getCert()});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(_algorithm);
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

        return keyManagerFactory.getKeyManagers();
    }

    /**
     * Creates the TrustManager, which holds the certs of root CAs you trust.
     */
    private TrustManager[] getTrustManagers(@Nullable ICertificateProvider trustedCA, CRL crl)
            throws IOException, GeneralSecurityException
    {
        if (trustedCA == null) return null;

        KeyStore keyStore = KeyStore.getInstance(_keystoreType);
        keyStore.load(null, KEYSTORE_PASSWORD);  // initialize the keystore

        // Load the CA cert in the keystore, and initialize the TrustManagerFactory with it.
        checkNotNull(trustedCA.getCert());

        keyStore.setCertificateEntry("aerofs_cacert", trustedCA.getCert());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(_algorithm);
        trustManagerFactory.init(keyStore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        if (crl == null) {
            return trustManagers;
        }

        // IMPORTANT: our cert checking comes in two phases:
        //
        // 1. Delegate to a real X509 trust manager to check if the cert is valid and has been
        //    issued by the trusted CA.
        // 2. Check if its serial number has been revoked.
        //
        // Below, we try and find a real X509 trust manager to be used in (1).
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

    @Override
    public SslHandler newSslHandler() throws IOException, GeneralSecurityException
    {
        SslHandler sslHandler = new SslHandler(getSSLEngine());
        sslHandler.setCloseOnSSLException(true);
        sslHandler.setEnableRenegotiation(false);
        return sslHandler;
    }

    public static SSLEngineFactory newServerFactory(@Nullable IPrivateKeyProvider key,
            @Nullable ICertificateProvider cacert)
    {
        return new SSLEngineFactory(Mode.Server, Platform.Desktop, key, cacert, null);
    }
}
