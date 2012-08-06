/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

public interface IUnicastConnection
{
    ListenableFuture<Void> connect_();

    ListenableFuture<Void> send_(byte[][] bss, Prio pri);

    ListenableFuture<ImmutableList<WireData>> receive_();

    ListenableFuture<Void> disconnect_(Exception ex);

    ListenableFuture<Void> getCloseFuture_();
}
