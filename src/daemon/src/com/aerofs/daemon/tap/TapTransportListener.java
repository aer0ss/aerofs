/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.core.net.PeerStreamMap;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.lib.SystemUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Tap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class TapTransportListener implements ITransportListener
{
    private final LinkedNonblockingQueue<Tap.TransportEvent> _eventQueue;
    private final PeerStreamMap<IIncomingStream> _incomingStreams;

    public TapTransportListener(PeerStreamMap<IIncomingStream> incomingStreamMap,
            LinkedNonblockingQueue<Tap.TransportEvent> eventQueue)
    {
        _eventQueue = eventQueue;
        _incomingStreams = incomingStreamMap;
    }

    @Override
    public void onMaxcastMaxPacketSizeUpdated(int newsize)
    {
        Tap.TransportEvent.Builder event = Tap.TransportEvent.newBuilder();
        event.setType(Tap.TransportEvent.Type.MAXCAST_MAX_PACKET_SIZE_UPDATED);
        event.setMaxcastMaxPacketSize(newsize);
        _eventQueue.offer(event.build());
    }

    @Override
    public void onMaxcastDatagramReceived(DID did, SID sid, ByteArrayInputStream is, int wirelen)
    {
        Tap.TransportEvent.Builder event = Tap.TransportEvent.newBuilder();
        event.setType(Tap.TransportEvent.Type.MAXCAST_PACKET_RECEIVED);
        event.setDid(did.toPB());
        event.setSid(sid.toPB());

        try {
            byte[] bytes = new byte[wirelen];
            is.read(bytes);
            event.setPayload(ByteString.copyFrom(bytes));
        } catch (IOException e) {
            SystemUtil.fatal(e);
        }

        _eventQueue.offer(event.build());
    }

    @Override
    public void onPeerOnline(DID did, ImmutableSet<SID> stores)
    {
        for (SID store : stores) {
            onPresenceChanged(true, did, store);
        }
    }

    @Override
    public void onPeerOffline(DID did, ImmutableSet<SID> stores)
    {
        for (SID store : stores) {
            onPresenceChanged(false, did, store);
        }
    }

    @Override
    public void onAllPeersOffline()
    {
        // FIXME: to be implemented when a new tap message is created!!!!
    }

    private void onPresenceChanged(boolean online, DID did, SID sid)
    {
        Tap.TransportEvent.Builder event = Tap.TransportEvent.newBuilder();
        event.setType(Tap.TransportEvent.Type.PRESENCE_CHANGED);
        event.setDid(did.toPB());
        event.setSid(sid.toPB());
        event.setOnline(online);
        _eventQueue.offer(event.build());
    }

    @Override
    public void onUnicastDatagramReceived(DID did, SID sid, ByteArrayInputStream is, int wirelen)
    {
        Tap.TransportEvent.Builder event = Tap.TransportEvent.newBuilder();
        event.setType(Tap.TransportEvent.Type.DATAGRAM_RECEIVED);
        event.setDid(did.toPB());
        event.setSid(sid.toPB());

        try {
            byte[] bytes = new byte[wirelen];
            is.read(bytes);
            event.setPayload(ByteString.copyFrom(bytes));
        } catch (IOException e) {
            SystemUtil.fatal(e);
        }

        _eventQueue.offer(event.build());
    }

    @Override
    public void onStreamBegun(IIncomingStream stream)
    {
        try {
            _incomingStreams.addStream(stream.getDid_(), stream);
        } catch (ExStreamAlreadyExists e) {
            SystemUtil.fatal(e.getMessage());
        }

        Tap.TransportEvent.Builder event = Tap.TransportEvent.newBuilder();
        event.setType(Tap.TransportEvent.Type.INCOMING_STREAM_BEGUN);
        event.setDid(stream.getDid_().toPB());
        event.setSid(stream.getSid_().toPB());
        event.setStreamId(stream.getStreamId_().getInt());
        event.setHighPriority(stream.getPriority_() == Prio.HI);
        _eventQueue.offer(event.build());
    }
}
