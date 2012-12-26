/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.base.async.UncancellableFuture;

public final class StartPulseMessage
{
    private final UncancellableFuture<Void> _pulseFuture = UncancellableFuture.create();

    public UncancellableFuture<Void> getPulseFuture()
    {
        return _pulseFuture;
    }
}
