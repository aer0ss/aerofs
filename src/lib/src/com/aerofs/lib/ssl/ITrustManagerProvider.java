/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ssl;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManager;

/**
 * Interface that provides a nullable trust manager array.
 */
public interface ITrustManagerProvider
{
    @Nullable
    public TrustManager[] getTrustManagers();
}
