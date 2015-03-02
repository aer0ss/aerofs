/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.tc;

import com.google.inject.Inject;

import java.util.concurrent.Callable;

public class CoreLockReleasingExecutor
{
    private final TokenManager _tokenManager;

    @Inject
    public CoreLockReleasingExecutor(TokenManager tokenManager)
    {
        _tokenManager = tokenManager;
    }

    public <V> V execute_(Callable<V> c) throws Exception
    {
        return execute_(c, "");
    }

    public <V> V execute_(Callable<V> c, String reason) throws Exception
    {
        return _tokenManager.inPseudoPause_(Cat.UNLIMITED, reason, c::call);
    }
}
