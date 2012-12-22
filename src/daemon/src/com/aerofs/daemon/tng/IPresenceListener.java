/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.google.common.collect.ImmutableSet;

public interface IPresenceListener
{
    void onPeerOnline(DID did, ImmutableSet<SID> stores);

    void onPeerOffline(DID did, ImmutableSet<SID> stores);

    void onAllPeersOffline();
}
