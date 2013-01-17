/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManager;

public final class NullTrustManagerProvider implements ITrustManagerProvider
{
    public static final NullTrustManagerProvider NULL_TRUST_MANAGER_PROVIDER =
            new NullTrustManagerProvider();

    @Override
    @Nullable
    public TrustManager[] getTrustManagers()
    {
        return null;
    }
}