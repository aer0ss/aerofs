/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;

import java.io.InputStream;
import java.util.Collection;

public class TransportListener
{
    public void onDeviceAvailable(DID did, Collection<SID> sids) {}

    public void onDeviceUnavailable(DID did, Collection<SID> sids) {}

    public void onIncomingPacket(DID did, UserID userID, byte[] packet) {}
}
