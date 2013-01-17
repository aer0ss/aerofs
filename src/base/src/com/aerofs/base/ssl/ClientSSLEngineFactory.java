package com.aerofs.base.ssl;

import com.aerofs.base.ssl.IKeyManagersProvider;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;

import static com.aerofs.base.BaseSecUtil.newCertificateFromFile;

public final class ClientSSLEngineFactory
{
    // Statics
    private static final char[] KEYSTORE_PASSWORD = "".toCharArray();
    private static final String ALGORITHM = "SunX509";
    private static final String KEYSTORE_TYPE = "JKS"; // jks = java key store
    private static final String SECURITY_TYPE = "TLS";

    // Private members.
    private final IKeyManagersProvider _keyManagersProvider;
    private final String _serverTrustedRootCACertFilename;
    @Nullable private volatile SSLContext _context;

    public ClientSSLEngineFactory(String serverTrustedRootCACertFilename,
            IKeyManagersProvider keyManagersProvider)
    {
        this._serverTrustedRootCACertFilename = serverTrustedRootCACertFilename;
        this._keyManagersProvider = keyManagersProvider;
    }

    void init_()
            throws Exception
    {
        assert _context == null : ("cannot initialize client ssl engine twice");

        // create the keystore and load it with the server certificate chain
        Certificate serverCertificate = newCertificateFromFile(_serverTrustedRootCACertFilename);

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        // have to do a load with null to init the keystore
        keyStore.load(null, KEYSTORE_PASSWORD);
        // call setCertificateEntry with the same alias multiple times to establish a certificate
        // chain (I'm not sure about the order)
        keyStore.setCertificateEntry("aerofs", serverCertificate);

        // load our server certificate chain into the trust manager factory
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(ALGORITHM);
        trustManagerFactory.init(keyStore);

        // Set the key store in the key managers provider before we call get key managers when we
        // initialize the SSL context. This provides the client keys (or dummy equivalents) to the
        // SSL context.
        _keyManagersProvider.setKeyStore(keyStore, KEYSTORE_PASSWORD);

        // Setup the client context
        SSLContext context = SSLContext.getInstance(SECURITY_TYPE);

        // When we use an empty key manager, no client keys are set up in this case.
        context.init(_keyManagersProvider.getKeyManagers(ALGORITHM),
                trustManagerFactory.getTrustManagers(), null);

        SSLSessionContext sessionContext = context.getClientSessionContext();
        sessionContext.setSessionCacheSize(5000);
        sessionContext.setSessionTimeout(3600);

        this._context = context;
    }

    public synchronized SSLEngine getSSLEngine()
            throws Exception
    {
        if (_context == null) {
            init_();
        }

        assert _context != null : ("ssl context is null");

        SSLEngine engine = _context.createSSLEngine();
        engine.setUseClientMode(true);

        return engine;
    }

    public synchronized SSLEngine getSSLEngine(String host, short port)
            throws Exception
    {
        if (_context == null) {
            init_();
        }

        assert _context != null : ("ssl context is null");

        SSLEngine engine = _context.createSSLEngine(host, port);
        engine.setUseClientMode(true);

        return engine;
    }
}
