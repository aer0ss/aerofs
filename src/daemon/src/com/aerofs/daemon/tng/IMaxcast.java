/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

public interface IMaxcast
{
    ListenableFuture<Void> sendDatagram_(int maxcastId, SID sid, byte[] payload, Prio pri);

    ListenableFuture<Void> updateLocalStoreInterest_(ImmutableSet<SID> added, ImmutableSet<SID> removed);

    ListenableFuture<ImmutableSet<DID>> getMaxcastUnreachableOnlineDevices_();
}
