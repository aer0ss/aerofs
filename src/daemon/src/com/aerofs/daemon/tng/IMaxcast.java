/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.lib.event.Prio;
import com.aerofs.base.id.SID;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

public interface IMaxcast
{
    ListenableFuture<Void> sendDatagram_(int maxcastId, SID sid, byte[] payload, Prio pri);

    ListenableFuture<Void> updateLocalStoreInterest_(ImmutableSet<SID> added, ImmutableSet<SID> removed);
}
