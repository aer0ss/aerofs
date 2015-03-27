/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.lib.id.StreamID;

import java.io.InputStream;
import java.util.Collection;

public class TransportListener
{
    public void onNewStream(DID did, StreamID streamID, InputStream inputStream) {}

    public void onStoreAvailableForDevice(DID did, Collection<SID> sids) {}

    public void onStoreUnavailableForDevice(DID did, Collection<SID> sids) {}

    public void onIncomingPacket(DID did, UserID userID, byte[] packet) {}
}
