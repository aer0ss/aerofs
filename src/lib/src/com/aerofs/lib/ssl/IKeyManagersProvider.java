/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ssl;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * This interface is used to provide arguments to the SSL context initializer. In this way,
 * we can access resources in Cfg or provide empty credentials to the SSL context.
 */
public interface IKeyManagersProvider
{
    /**
     * Set the key store for this key managers accessor. This should be called *before* get key
     * managers is called.
     *
     * Implementers of this interface should assert on this if it is indeed required for the key
     * managers to be returned correctly.
     */
    public void setKeyStore(KeyStore keyStore, final char[] keyStorePassword)
            throws CertificateException, IOException, KeyStoreException;

    /**
     * Get the key managers associated with this key managers accessor. These are used as inputs to
     * the SSL context.
     */
    @Nullable
    public KeyManager[] getKeyManagers(String algorithm)
            throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException;
}