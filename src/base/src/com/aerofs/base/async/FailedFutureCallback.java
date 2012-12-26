/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.async;

import com.google.common.util.concurrent.FutureCallback;

/**
 * Allows implementors to only handle the case when a {@link com.google.common.util.concurrent.ListenableFuture}
 * fails with an exception
 */
public abstract class FailedFutureCallback implements FutureCallback<Object>
{
    @Override
    public final void onSuccess(Object o)
    {
        // Not used in this callback
    }
}
