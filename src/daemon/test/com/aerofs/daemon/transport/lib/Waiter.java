/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.google.common.util.concurrent.SettableFuture;

public final class Waiter implements IResultWaiter
{
    public final SettableFuture<Void> future = SettableFuture.create();

    @Override
    public void okay()
    {
        future.set(null);
    }

    @Override
    public void error(Exception e)
    {
        future.setException(e);
    }
}
