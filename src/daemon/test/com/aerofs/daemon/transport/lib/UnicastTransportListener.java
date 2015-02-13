/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.transport.TransportListener;
import com.google.common.collect.Queues;

import java.util.concurrent.LinkedBlockingQueue;

public class UnicastTransportListener extends TransportListener
{
    public static final class Received
    {
        public final DID did;
        public final UserID userID;
        public final byte[] packet;

        public Received(DID did, UserID userID, byte[] packet)
        {
            this.did = did;
            this.userID = userID;
            this.packet = packet;
        }
    }

    public final LinkedBlockingQueue<Received> received = Queues.newLinkedBlockingQueue(100);

    @Override
    public void onIncomingPacket(DID did, UserID userID, byte[] packet)
    {
        received.offer(new Received(did, userID, packet));
    }
}
