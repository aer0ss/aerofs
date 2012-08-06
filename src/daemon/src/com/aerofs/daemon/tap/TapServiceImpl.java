/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.core.net.PeerStreamMap;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.IStream;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.base.streams.Chunk;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.FailedFutureCallback;
import com.aerofs.lib.async.FutureUtil;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.proto.Tap;
import com.aerofs.proto.Tap.ITapService;
import com.aerofs.proto.Tap.MessageTypeCollection;
import com.aerofs.proto.Tap.PBAckReply;
import com.aerofs.proto.Tap.PBChunk;
import com.aerofs.proto.Tap.PBChunkCollection;
import com.aerofs.proto.Tap.PBVoid;
import com.aerofs.proto.Tap.StartTransportCall;
import com.aerofs.proto.Tap.UUIDCollection;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;

public class TapServiceImpl implements ITapService
{
    private static final Logger l = Util.l(TapServiceImpl.class);

    private ITransport _transport;
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final TransportFactory _transportFactory;
    private final OutgoingMessageFilterListener _messageFilterListener;
    private final LinkedNonblockingQueue<Tap.TransportEvent> _eventQueue = LinkedNonblockingQueue.create();

    // The mappings between streamIDs and IStream objects
    private final PeerStreamMap<IIncomingStream> _incomingStreams = PeerStreamMap.create();
    private final PeerStreamMap<IOutgoingStream> _outgoingStreams = PeerStreamMap.create();

    @Inject
    public TapServiceImpl(ISingleThreadedPrioritizedExecutor executor, TransportFactory factory)
    {
        _executor = executor;
        _transportFactory = factory;
        _messageFilterListener = new OutgoingMessageFilterListener(executor);
    }

    /*
     * Protobuf service methods
     */

    @Override
    public Common.PBException encodeError(Throwable error)
    {
        l.error(Util.stackTrace2string(error));

        Common.PBException.Builder reply = Common.PBException.newBuilder();
        reply.setType(Type.INTERNAL_ERROR);
        if (error.getMessage() != null) {
            reply.setLocalizedMessage(error.getMessage());
        }
        reply.setStackTrace(Util.stackTrace2string(error));
        return reply.build();
    }

    @Override
    public synchronized ListenableFuture<PBAckReply> startTransport(StartTransportCall.Type type)
            throws Exception
    {
        l.info("startTransport: " + type);

        if (_transport != null) {
            throw new IllegalStateException("Started transport twice");
        }

        ITransportListener listener = new TapTransportListener(_incomingStreams, _eventQueue);
        IPipelineFactory pipelineFactory = new TapPipelineFactory(_executor, listener,
                _messageFilterListener);

        _transport = _transportFactory.create(type, listener, pipelineFactory);
        if (_transport == null) {
            throw new IllegalArgumentException("No such transport type");
        }

        _transport.start_();
        _transportFactory.getLinkStateService().start_();

        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> denyNone()
            throws Exception
    {
        _messageFilterListener.denyNone_();
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> denyAll()
            throws Exception
    {
        _messageFilterListener.denyAll_();
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> deny(MessageTypeCollection messageTypes)
            throws Exception
    {
        _messageFilterListener.deny_(messageTypes.getTypesList());
        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Tap.TransportEvent> awaitTransportEvent()
            throws Exception
    {
        assertStarted();
        return _eventQueue.takeWhenAvailable();
    }

    @Override
    public ListenableFuture<PBAckReply> sendMaxcastDatagram(Integer id, ByteString sid,
            ByteString payload, Boolean highPrio)
            throws Exception
    {
        assertStarted();
        return makePBAckReplyFuture(
                _transport.sendDatagram_(id, new SID(sid.toByteArray()), payload.toByteArray(),
                        highPrio ? Prio.HI : Prio.LO));
    }

    @Override
    public ListenableFuture<PBVoid> updateLocalStoreInterest(UUIDCollection storesAdded,
            UUIDCollection storesRemoved)
            throws Exception
    {
        assertStarted();
        ImmutableSet.Builder<SID> added = ImmutableSet.builder();
        for (int i = 0; i < storesAdded.getUuidsCount(); i++) {
            added.add(new SID(storesAdded.getUuids(i).toByteArray()));
        }

        ImmutableSet.Builder<SID> removed = ImmutableSet.builder();
        for (int i = 0; i < storesRemoved.getUuidsCount(); i++) {
            removed.add(new SID(storesRemoved.getUuids(i).toByteArray()));
        }

        return makePBVoidFuture(
                _transport.updateLocalStoreInterest_(added.build(), removed.build()));
    }

    @Override
    public ListenableFuture<UUIDCollection> getMaxcastUnreachableOnlineDevices()
            throws Exception
    {
        assertStarted();
        ListenableFuture<ImmutableSet<DID>> future = _transport.getMaxcastUnreachableOnlineDevices_();
        return Futures.transform(future, new AsyncFunction<ImmutableSet<DID>, UUIDCollection>()
        {
            @Override
            public ListenableFuture<UUIDCollection> apply(ImmutableSet<DID> input)
                    throws Exception
            {
                UUIDCollection.Builder message = UUIDCollection.newBuilder();
                for (DID did : input) {
                    message.addUuids(did.toPB());
                }
                return UncancellableFuture.createSucceeded(message.build());
            }
        });
    }

    @Override
    public ListenableFuture<PBAckReply> sendUnicastDatagram(ByteString did, ByteString sid,
            ByteString payload, Boolean highPrio)
            throws Exception
    {
        assertStarted();
        return makePBAckReplyFuture(
                _transport.sendDatagram_(new DID(did.toByteArray()), new SID(sid.toByteArray()),
                        payload.toByteArray(), highPrio ? Prio.HI : Prio.LO));
    }

    @Override
    public ListenableFuture<PBVoid> pulse(ByteString did, Boolean highPrio)
            throws Exception
    {
        assertStarted();
        return makePBVoidFuture(
                _transport.pulse_(new DID(did.toByteArray()), highPrio ? Prio.HI : Prio.LO));
    }

    /*
     * Stream related calls
     */

    @Override
    public ListenableFuture<PBAckReply> begin(Integer streamId, ByteString did, ByteString sid,
            Boolean highPrio)
            throws Exception
    {
        assertStarted();
        ListenableFuture<IOutgoingStream> future = _transport.beginStream_(new StreamID(streamId),
                new DID(did.toByteArray()), new SID(sid.toByteArray()),
                highPrio ? Prio.HI : Prio.LO);

        return Futures.transform(future, new AsyncFunction<IOutgoingStream, PBAckReply>()
        {
            @Override
            public ListenableFuture<PBAckReply> apply(IOutgoingStream stream)
                    throws Exception
            {
                _outgoingStreams.addStream(stream.getDid_(), stream);
                return makePBAckReplyFuture(UncancellableFuture.<Void>createSucceeded(null));
            }
        }, _executor);
    }

    @Override
    public ListenableFuture<PBAckReply> send(Integer streamId, ByteString did, ByteString payload)
            throws Exception
    {
        assertStarted();
        IOutgoingStream stream = _outgoingStreams.getStream(new DID(did.toByteArray()),
                new StreamID(streamId));

        ListenableFuture<PBAckReply> future = makePBAckReplyFuture(
                stream.send_(payload.toByteArray()));

        FutureUtil.addCallback(future, new RemoveFromMapOnFailureCallback(stream), _executor);
        return future;
    }

    @Override
    public ListenableFuture<PBAckReply> abortOutgoing(Integer streamId, ByteString did,
            InvalidationReason reason)
            throws Exception
    {
        assertStarted();
        IOutgoingStream stream = _outgoingStreams.getStream(new DID(did.toByteArray()),
                new StreamID(streamId));

        FutureUtil.addCallback(stream.abort_(reason), new RemoveFromMapOnFailureCallback(stream),
                _executor);

        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> endOutgoing(Integer streamId, ByteString did)
            throws Exception
    {
        assertStarted();
        IOutgoingStream stream = _outgoingStreams.getStream(new DID(did.toByteArray()),
                new StreamID(streamId));

        FutureUtil.addCallback(stream.end_(), new RemoveFromMapOnFailureCallback(stream),
                _executor);

        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBChunkCollection> receive(Integer streamId, ByteString did)
            throws Exception
    {
        assertStarted();
        IIncomingStream stream = _incomingStreams.getStream(new DID(did.toByteArray()),
                new StreamID(streamId));

        ListenableFuture<PBChunkCollection> future = Futures.transform(stream.receive_(),
                new AsyncFunction<ImmutableList<Chunk>, PBChunkCollection>()
                {
                    @Override
                    public ListenableFuture<PBChunkCollection> apply(ImmutableList<Chunk> input)
                            throws Exception
                    {
                        PBChunkCollection.Builder collection = PBChunkCollection.newBuilder();
                        for (Chunk chunk : input) {
                            // FIXME: Lots of copying here... should only be once
                            ByteArrayInputStream is = chunk.getChunkIs_();
                            byte[] data = new byte[is.available()];
                            is.read(data);

                            PBChunk.Builder chunkBuilder = PBChunk.newBuilder();
                            chunkBuilder.setSeqNum(chunk.getSeqnum_());
                            chunkBuilder.setWireLength(chunk.getWirelen_());
                            chunkBuilder.setPayload(ByteString.copyFrom(data));
                            collection.addChunks(chunkBuilder.build());
                        }
                        return UncancellableFuture.createSucceeded(collection.build());
                    }

                });

        FutureUtil.addCallback(future, new RemoveFromMapOnFailureCallback(stream), _executor);
        return future;
    }

    @Override
    public ListenableFuture<PBAckReply> abortIncoming(Integer streamId, ByteString did,
            InvalidationReason reason)
            throws Exception
    {
        assertStarted();
        IIncomingStream stream = _incomingStreams.getStream(new DID(did.toByteArray()),
                new StreamID(streamId));

        FutureUtil.addCallback(stream.abort_(reason), new RemoveFromMapOnFailureCallback(stream),
                _executor);

        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    @Override
    public ListenableFuture<PBAckReply> endIncoming(Integer streamId, ByteString did)
            throws Exception
    {
        assertStarted();
        IIncomingStream stream = _incomingStreams.getStream(new DID(did.toByteArray()),
                new StreamID(streamId));

        FutureUtil.addCallback(stream.end_(), new RemoveFromMapOnFailureCallback(stream),
                _executor);

        return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
    }

    private void assertStarted()
    {
        if (_transport == null) {
            throw new IllegalStateException("Transport not started");
        }
    }

    /**
     * Returns a PBAckReply future when given a Void future
     *
     * @param future The future to wrap
     * @return A PBAckReply future
     */
    private static ListenableFuture<PBAckReply> makePBAckReplyFuture(ListenableFuture<Void> future)
    {
        return Futures.transform(future, new AsyncFunction<Void, PBAckReply>()
        {
            @Override
            public ListenableFuture<PBAckReply> apply(Void in)
                    throws Exception
            {
                return UncancellableFuture.createSucceeded(PBAckReply.getDefaultInstance());
            }

        });
    }

    /**
     * Returns a PBVoid future when given a Void future
     *
     * @param future The future to wrap
     * @return A PBVoid future
     */
    private static ListenableFuture<PBVoid> makePBVoidFuture(ListenableFuture<Void> future)
    {
        return Futures.transform(future, new AsyncFunction<Void, PBVoid>()
        {
            @Override
            public ListenableFuture<PBVoid> apply(Void in)
                    throws Exception
            {
                return UncancellableFuture.createSucceeded(PBVoid.getDefaultInstance());
            }

        });
    }

    private class RemoveFromMapOnFailureCallback extends FailedFutureCallback
    {
        private final IStream _stream;

        private RemoveFromMapOnFailureCallback(IStream stream)
        {
            _stream = stream;
        }

        @Override
        public void onFailure(Throwable throwable)
        {
            l.info("removing stream with throwable: " + throwable.getMessage());
            if (_stream instanceof IIncomingStream) {
                _incomingStreams.removeStream(_stream.getDid_(), _stream.getStreamId_());
            } else if (_stream instanceof IOutgoingStream) {
                _outgoingStreams.removeStream(_stream.getDid_(), _stream.getStreamId_());
            }
        }
    }

    ;
}
