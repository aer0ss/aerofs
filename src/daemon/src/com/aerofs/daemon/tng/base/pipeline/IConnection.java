/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

import com.aerofs.daemon.lib.Prio;
import com.google.common.util.concurrent.ListenableFuture;

public interface IConnection
{
    ListenableFuture<Void> getCloseFuture_();

    ListenableFuture<Void> send_(Object input, Prio pri);

    ListenableFuture<Void> disconnect_(Exception ex);
}