/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public abstract class AbstractKeyManagersProvider
        implements IPrivateKeyProvider, IKeyManagersProvider
{
    @Nullable
    private char[] _keyStorePassword = null;
    @Nullable
    private KeyStore _keyStore = null;

    @Override
    public void setKeyStore(KeyStore keyStore, final char[] keyStorePassword)
            throws CertificateException, IOException, KeyStoreException
    {
        this._keyStore = keyStore;
        this._keyStorePassword = keyStorePassword;

        keyStore.setKeyEntry("client", getPrivateKey(), keyStorePassword,
                new Certificate[] {getCert()});
    }

    @Override
    public KeyManager[] getKeyManagers(String algorithm)
            throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException
    {
        assert _keyStorePassword != null : "must set key store pass before getting key managers";
        assert _keyStore != null :         "must set key store before getting key managers";

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(_keyStore, _keyStorePassword);

        return keyManagerFactory.getKeyManagers();
    }
}
