/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.net.EITransportMetricsUpdated;
import com.aerofs.daemon.event.net.tng.EIPresence;
import com.aerofs.daemon.event.net.tng.Endpoint;
import com.aerofs.daemon.event.net.tng.rx.EIChunk;
import com.aerofs.daemon.event.net.tng.rx.EIMaxcastMessage;
import com.aerofs.daemon.event.net.tng.rx.EIStreamAborted;
import com.aerofs.daemon.event.net.tng.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.tng.rx.EIUnicastMessage;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.base.streams.Chunk;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.FailedFutureCallback;
import com.aerofs.lib.async.FutureUtil;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.Iterator;

public class CoreQueueBasedTransportListener implements ITransportListener
{
    private static final Logger l = Util.l(CoreQueueBasedTransportListener.class);

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final CoreQueue _coreQueue;
    private final PeerStreamMap<IIncomingStream> _incomingStreamMap;
    private ITransport _transport = null;

    public CoreQueueBasedTransportListener(ISingleThreadedPrioritizedExecutor executor, CoreQueue q,
            PeerStreamMap<IIncomingStream> incomingStreamMap)
    {
        _executor = executor;
        _coreQueue = q;
        _incomingStreamMap = incomingStreamMap;
    }

    public void setTransport(ITransport transport)
    {
        assert transport != null;
        assert _transport == null;

        _transport = transport;
    }

    @Override
    public void onMaxcastMaxPacketSizeUpdated(int newsize)
    {
        IEvent event = new EITransportMetricsUpdated(newsize);
        _coreQueue.enqueueBlocking(event, Prio.LO);
    }

    @Override
    public void onMaxcastDatagramReceived(DID did, SID sid,
                                          ByteArrayInputStream is, int wirelen)
    {
        assert _transport != null;
        Endpoint endpoint = new Endpoint(_transport, did);
        IEvent event = new EIMaxcastMessage(endpoint, sid, is, wirelen);
        _coreQueue.enqueueBlocking(event, Prio.LO);
    }

    @Override
    public void onPeerOnline(DID did, ImmutableSet<SID> stores)
    {
        assert _transport != null;
        for (SID store : stores) {
            IEvent event = new EIPresence(_transport, true, did, store);
            _coreQueue.enqueueBlocking(event, Prio.LO);
        }
    }

    @Override
    public void onPeerOffline(DID did, ImmutableSet<SID> stores)
    {
        assert _transport != null;
        for (SID store : stores) {
            IEvent event = new EIPresence(_transport, false, did, store);
            _coreQueue.enqueueBlocking(event, Prio.LO);
        }
    }

    @Override
    public void onAllPeersOffline()
    {
        assert _transport != null;
        IEvent event = new EIPresence(_transport, false, null);
        _coreQueue.enqueueBlocking(event, Prio.LO);
    }

    @Override
    public void onUnicastDatagramReceived(DID did,
                                          SID sid,
                                          ByteArrayInputStream is,
                                          int wirelen)
    {
        assert _transport != null;
        Endpoint endpoint = new Endpoint(_transport, did);
        IEvent event = new EIUnicastMessage(endpoint, sid, is, wirelen);
        _coreQueue.enqueueBlocking(event, Prio.LO);
    }

    @Override
    public void onStreamBegun(final IIncomingStream stream)
    {
        assert _transport != null;

        try {
            _incomingStreamMap.addStream(stream.getDid_(), stream);

            // Have the stream remove itself from this map when it dies
            FutureUtil.addCallback(stream.getCloseFuture_(), new FailedFutureCallback()
            {
                @Override
                public void onFailure(Throwable throwable)
                {
                    _incomingStreamMap.removeStream(stream.getDid_(), stream.getStreamId_());
                }
            });
        } catch (ExStreamAlreadyExists e) {
            l.warn(e.getMessage());
            return;
        }

        l.debug("starting receive loop on incoming stream: " + stream);

        final Endpoint endpoint = new Endpoint(_transport, stream.getDid_());

        // Start the read loop here
        ListenableFuture<ImmutableList<Chunk>> future = stream.receive_();
        FutureUtil.addCallback(future, new FutureCallback<ImmutableList<Chunk>>()
        {
            @Override
            public void onSuccess(ImmutableList<Chunk> chunks)
            {
                assert !chunks.isEmpty();

                Iterator<Chunk> iterator = chunks.iterator();

                // This is the first set of data, so send the Begin Stream event with this data
                l.debug("sending begin stream event to core");
                Chunk firstChunk = iterator.next();
                _coreQueue.enqueueBlocking(new EIStreamBegun(endpoint, stream,
                        firstChunk.getChunkIs_(), firstChunk.getWirelen_()), Prio.LO);

                // Have the stream report an abortion if not ended gracefully
                FutureUtil.addCallback(stream.getCloseFuture_(), new FailedFutureCallback()
                {
                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        l.debug("stream died: " + throwable);

                        InvalidationReason reason = InvalidationReason.INTERNAL_ERROR;
                        if (throwable instanceof  ExStreamInvalid) {
                            ExStreamInvalid exception = (ExStreamInvalid) throwable;
                            reason = exception.getReason_();

                            if (reason == InvalidationReason.ENDED) {
                                // This was closed gracefully
                                return;
                            }
                        }

                        // FIXME: If the core aborts, this event will be sent as well
                        _coreQueue.enqueueBlocking(new EIStreamAborted(endpoint,
                                stream.getStreamId_(), reason), Prio.LO);
                    }
                }, _executor);

                // Send the rest of the chunks
                enqueueChunks(stream, iterator);

                // Receive forever
                receive(stream);
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                l.debug("failed reading from stream: " + throwable);
            }
        }, _executor);
    }

    private void receive(final IIncomingStream stream)
    {
        ListenableFuture<ImmutableList<Chunk>> future = stream.receive_();
        FutureUtil.addCallback(future, new FutureCallback<ImmutableList<Chunk>>()
        {
            @Override
            public void onSuccess(ImmutableList<Chunk> chunks)
            {
                assert !chunks.isEmpty();

                enqueueChunks(stream, chunks.iterator());

                // Receive again
                receive(stream);
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                l.debug("failed reading from stream: " + throwable);
            }
        }, _executor);
    }

    private void enqueueChunks(IIncomingStream stream, Iterator<Chunk> iterator)
    {
        while (iterator.hasNext()) {
            Chunk chunk = iterator.next();
            Endpoint endpoint = new Endpoint(_transport, stream.getDid_());
            _coreQueue.enqueueBlocking(new EIChunk(endpoint, stream, chunk.getSeqnum_(),
                    chunk.getChunkIs_(), chunk.getWirelen_()), Prio.LO);
        }
    }
}
