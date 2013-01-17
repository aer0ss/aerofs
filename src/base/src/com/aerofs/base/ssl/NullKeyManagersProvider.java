/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import java.security.KeyStore;

public class NullKeyManagersProvider implements IKeyManagersProvider
{
    public static final NullKeyManagersProvider NULL_KEY_MANAGERS_PROVIDER =
            new NullKeyManagersProvider();

    @Override
    public void setKeyStore(KeyStore keyStore, final char[] keyStorePassword)
    {
        // noop
    }

    @Override
    @Nullable
    public KeyManager[] getKeyManagers(String algorithm)
    {
        return null;
    }
}