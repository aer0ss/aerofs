package com.aerofs.base.ssl;

import com.aerofs.base.ssl.ITrustManagerProvider;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import static com.aerofs.base.BaseSecUtil.newCertificateFromFile;
import static com.aerofs.base.BaseSecUtil.newPrivateKeyFromFile;

public final class ServerSSLEngineFactory
{
    // Statics.
    private static final char[] KEYSTORE_PASSWORD = "".toCharArray();
    private static final String ALGORITHM = "SunX509";
    private static final String KEYSTORE_TYPE = "JKS"; // jks = java key store
    private static final String SECURITY_TYPE = "TLS";

    // Privates (hehe).
    private final String _serverKeyFilename;
    private final String _serverCertFilename;
    @Nullable private volatile SSLContext _context;
    private final ITrustManagerProvider _trustManagerProvider;

    /**
     * SSL engine factory for server side code.
     *
     * @param serverKeyFilename The name of the file which contains the server's private key.
     * @param serverCertFilename The name of the file which contains the server's certificate.
     * @param trustManagerProvider The trust manager provider. If the TMS does indeed provide a
     * trust manager, then we will force mutual authentication, else we will not.
     */
    public ServerSSLEngineFactory(String serverKeyFilename, String serverCertFilename,
            ITrustManagerProvider trustManagerProvider)
    {
        this._serverKeyFilename = serverKeyFilename;
        this._serverCertFilename = serverCertFilename;
        this._trustManagerProvider = trustManagerProvider;
    }

    void init_()
            throws Exception
    {
        assert _context == null : ("cannot initialize server ssl engine twice");

        // load the server's private key and its public key signed by the trusted CA from PEM files
        PrivateKey serverPrivateKey = newPrivateKeyFromFile(_serverKeyFilename);
        Certificate serverCertificate = newCertificateFromFile(_serverCertFilename);

        // create the keystore and load it with the public and private keys
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        // have to do a load with null to init the keystore
        keyStore.load(null, KEYSTORE_PASSWORD);
        keyStore.setKeyEntry("server", serverPrivateKey, KEYSTORE_PASSWORD,
                new Certificate[] {serverCertificate});

        // create a keymanager that uses this keystore
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ALGORITHM);
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

        // Setup the server context and use a trust manager the hooks into the command channel.
        // If the trust manager returned by the trust manager provider is null, then the context
        // will not perform mutual auth.

        SSLContext context = SSLContext.getInstance(SECURITY_TYPE);
        context.init(keyManagerFactory.getKeyManagers(), _trustManagerProvider.getTrustManagers(), null);

        SSLSessionContext sessionContext = context.getServerSessionContext(); // only care about the server side
        sessionContext.setSessionCacheSize(100); // only 100 sessions
        sessionContext.setSessionTimeout(300); // 5 minutes

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
        engine.setUseClientMode(false);

        // Mutual authentication, bitch. Only force mutual auth when we have been provided with a
        // valid trust manager.
        // FIXME (AG): use alternate "EMPTY_TRUST_MANAGER" mechanism that obviates this check
        if (_trustManagerProvider.getTrustManagers() != null) {
            engine.setNeedClientAuth(true);
        }

        return engine;
    }
}
