/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.tc;

import com.aerofs.daemon.core.tc.TC.TCB;
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
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, reason);
        try {
            TCB tcb = tk.pseudoPause_(reason);
            try {
                return c.call();
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
