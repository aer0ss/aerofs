package com.aerofs.daemon.tap;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.event.Prio;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Tap.ITapService;
import com.aerofs.proto.Tap.MessageTypeCollection;
import com.aerofs.proto.Tap.PBAckReply;
import com.aerofs.proto.Tap.PBChunkCollection;
import com.aerofs.proto.Tap.PBVoid;
import com.aerofs.proto.Tap.StartTransportCall;
import com.aerofs.proto.Tap.TransportEvent;
import com.aerofs.proto.Tap.UUIDCollection;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.atomic.AtomicBoolean;

public class MockTapServiceImpl implements ITapService
{
    private static final Logger l = Loggers.getLogger(MockTapServiceImpl.class);
    private final AtomicBoolean _started = new AtomicBoolean(false);

    @Inject
    public MockTapServiceImpl()
    {

    }

    @Override
    public PBException encodeError(Throwable error)
    {
        return PBException.newBuilder().setMessage("Error from Transport").build();
    }

    private void assertStarted()
            throws Exception
    {
        if (!_started.get()) {
            throw new IllegalStateException("Transport not started");
        }
    }

    @Override
    public ListenableFuture<PBAckReply> startTransport(StartTransportCall.Type type)
            throws Exception
    {
        l.info("startTransport: " + type);
        boolean wasStarted = _started.getAndSet(true);
        if (wasStarted) {
            throw new IllegalStateException("Started transport twice");
        }
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> denyNone()
            throws Exception
    {
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> denyAll()
            throws Exception
    {
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> deny(MessageTypeCollection messageTypes)
            throws Exception
    {
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> sendMaxcastDatagram(Integer id, ByteString sid,
            ByteString payload, Boolean highPriority)
            throws Exception
    {
        assertStarted();
        l.info("sendPacket: MID=" + id + ", SID=" + new SID(sid.toByteArray()) + ", payload=[len " +
                payload.size() + "]" + ", prio=" + (highPriority ? Prio.HI : Prio.LO));
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBVoid> updateLocalStoreInterest(UUIDCollection storesAdded,
            UUIDCollection storesRemoved)
            throws Exception
    {
        assertStarted();
        StringBuilder str = new StringBuilder();
        str.append("added[");
        for (ByteString sid : storesAdded.getUuidsList()) {
            str.append(new SID(sid.toByteArray())).append(", ");
        }
        str.append("], removed[");

        for (ByteString sid : storesRemoved.getUuidsList()) {
            str.append(new SID(sid.toByteArray())).append(", ");
        }
        str.append("]");

        l.info("updateLocalStoreInterest: " + str);
        return UncancellableFuture.createSucceeded(PBVoid.getDefaultInstance());
    }

    @Override
    public ListenableFuture<UUIDCollection> getMaxcastUnreachableOnlineDevices()
            throws Exception
    {
        assertStarted();
        l.info("getMaxcastUnreachableOnlineDevices:");
        return UncancellableFuture.createSucceeded(UUIDCollection.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> sendUnicastDatagram(ByteString did, ByteString sid,
            ByteString payload, Boolean highPriority)
            throws Exception
    {
        assertStarted();
        l.info("sendPacket: DID=" + new DID(did.toByteArray()) + ", SID=" +
                new SID(sid.toByteArray()) + ", payload=[len " + payload.size() + "]" +
                ", prio=" + (highPriority ? Prio.HI : Prio.LO));
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBVoid> pulse(ByteString did, Boolean highPriority)
            throws Exception
    {
        assertStarted();
        l.info("startPulse: DID=" + new DID(did.toByteArray()) + ", prio=" +
                (highPriority ? Prio.HI : Prio.LO));
        return UncancellableFuture.createSucceeded(PBVoid.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> begin(Integer streamId, ByteString did, ByteString sid,
            Boolean highPriority)
            throws Exception
    {
        assertStarted();
        l.info("beginStream: streamId=" + streamId + ", DID=" + new DID(did.toByteArray()) +
                ", SID=" + new SID(sid.toByteArray()) + ", prio=" +
                (highPriority ? Prio.HI : Prio.LO));
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> send(Integer streamId, ByteString did, ByteString payload)
            throws Exception
    {
        assertStarted();
        l.info("IOutgoingStream.send: streamId=" + streamId + ", DID=" +
                new DID(did.toByteArray()) + ", payload=[len " + payload.size() + "]");
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> abortOutgoing(Integer streamId, ByteString did,
            InvalidationReason reason)
            throws Exception
    {
        assertStarted();
        l.info("IOutgoingStream.abort: streamId=" + streamId + ", DID=" +
                new DID(did.toByteArray()) + ", Reason=" + reason);
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> endOutgoing(Integer streamId, ByteString did)
            throws Exception
    {
        assertStarted();
        l.info("IOutgoingStream.end: streamId=" + streamId + ", DID=" + new DID(did.toByteArray()));
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBChunkCollection> receive(Integer streamId, ByteString did)
            throws Exception
    {
        assertStarted();
        l.info("IIncomingStream.receive: streamId=" + streamId + ", DID=" +
                new DID(did.toByteArray()));
        return UncancellableFuture.createSucceeded(PBChunkCollection.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> abortIncoming(Integer streamId, ByteString did,
            InvalidationReason reason)
            throws Exception
    {
        assertStarted();
        l.info("IIncomingtream.abort: streamId=" + streamId + ", DID=" +
                new DID(did.toByteArray()) + ", Reason=" + reason);
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> endIncoming(Integer streamId, ByteString did)
            throws Exception
    {
        assertStarted();
        l.info("IIncomingStream.end: streamId=" + streamId + ", DID=" + new DID(did.toByteArray()));
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<TransportEvent> awaitTransportEvent()
            throws Exception
    {
        assertStarted();
        throw new NotImplementedException();
    }
}
