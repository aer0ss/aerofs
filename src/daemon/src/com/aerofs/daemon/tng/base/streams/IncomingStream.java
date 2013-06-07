/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.base.id.DID;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.base.async.FailedFutureCallback;
import com.aerofs.base.async.UncancellableFuture;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.base.async.FutureUtil.addCallback;
import static com.aerofs.proto.Transport.PBStream.InvalidationReason;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

class IncomingStream extends AbstractStream implements IIncomingStream
{
    private final AtomicBoolean _invalidated = new AtomicBoolean(false);
    private final UncancellableFuture<ImmutableList<Chunk>> NO_PENDING_RECV_CALL_FUTURE = makeRecvFuture();
    private final LinkedList<Chunk> _chunks = new LinkedList<Chunk>(); // protected by this

    private UncancellableFuture<ImmutableList<Chunk>> _pendingRecvFuture = NO_PENDING_RECV_CALL_FUTURE; // protected by this

    private int _seqnum = 0;

    private UncancellableFuture<ImmutableList<Chunk>> makeRecvFuture()
    {
        final UncancellableFuture<ImmutableList<Chunk>> future = UncancellableFuture.create();

        addCallback(getCloseFuture_(), new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                future.setException(t);
            }

        });

        return future;
    }

    public static IncomingStream getInstance_(ISingleThreadedPrioritizedExecutor executor,
            IConnection connection, StreamID id, DID did, Prio pri)
            throws ExStreamAlreadyExists
    {
        final IncomingStream stream = new IncomingStream(executor, id, did, pri);

        addCallback(connection.getCloseFuture_(), new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                stream.getSettableCloseFuture_().setException(t);
            }

        }, sameThreadExecutor());

        return stream;
    }

    private IncomingStream(ISingleThreadedPrioritizedExecutor executor, StreamID id, DID did, Prio pri)
    {
        super(executor, id, did, pri);
    }

    @Override
    public ListenableFuture<Void> abort_(final InvalidationReason reason)
    {
        if (!_invalidated.getAndSet(true)) {
            getSettableCloseFuture_().setException(
                    new ExStreamInvalid(this.getStreamId_(), reason));
        }

        return getCloseFuture_();
    }

    @Override
    public ListenableFuture<Void> end_()
    {
        if (!_invalidated.getAndSet(true)) {
            getSettableCloseFuture_().setException(
                    new ExStreamInvalid(this.getStreamId_(), InvalidationReason.ENDED));
        }

        return getCloseFuture_();
    }

    @Override
    public ListenableFuture<ImmutableList<Chunk>> receive_()
    {
        if (_invalidated.get()) {
            final UncancellableFuture<ImmutableList<Chunk>> failed = UncancellableFuture.create();

            addCallback(getCloseFuture_(), new FailedFutureCallback()
            {
                @Override
                public void onFailure(Throwable t)
                {
                    failed.setException(t);
                }

            }, sameThreadExecutor());

            return failed;
        }

        UncancellableFuture<ImmutableList<Chunk>> returned;

        synchronized (this) {
            if (_pendingRecvFuture == NO_PENDING_RECV_CALL_FUTURE) {
                returned = _pendingRecvFuture = makeRecvFuture();
                if (!_chunks.isEmpty()) {
                    _pendingRecvFuture.set(ImmutableList.copyOf(_chunks));
                    _chunks.clear();
                    _pendingRecvFuture = NO_PENDING_RECV_CALL_FUTURE;
                }
            } else {
                assert _chunks.isEmpty();
                returned = _pendingRecvFuture;
            }
        }

        return returned;
    }

    private void addChunk_(Chunk chunk)
    {
        _chunks.add(chunk);

        synchronized (this) {
            if (_pendingRecvFuture != NO_PENDING_RECV_CALL_FUTURE) {
                _pendingRecvFuture.set(ImmutableList.copyOf(_chunks));
                _chunks.clear();
                _pendingRecvFuture = NO_PENDING_RECV_CALL_FUTURE;
            }
        }
    }

    // FIXME: verfiy that this runs in executor thread
    void onBytesReceived_(int seqnum, ByteArrayInputStream chunkIs, int wirelen)
    {
        // happens if the core aborts the stream while the transport is still delivering packets
        // IMPORTANT: logical error if upper layer _ends_ stream while packets still coming in

        if (_invalidated.get()) return;

        try {
            checkAndIncrementSeqnum_(seqnum);
            addChunk_(new Chunk(seqnum, chunkIs, wirelen));
        } catch (ExStreamInvalid e) {
            abort_(e.getReason_());
        }
    }

    /**
     * Check the sequence number of the chunk received by the remote peer against the one that's
     * expected locally
     *
     * @param receivedSeqnum sequence number sent by the remote peer
     * @return next expected sequence number for this stream
     * @throws ExStreamInvalid if the expected sequence number and the received sequence number
     * don't match
     */
    private int checkAndIncrementSeqnum_(int receivedSeqnum)
            throws ExStreamInvalid
    {
        if (receivedSeqnum != _seqnum) {
            throw new ExStreamInvalid(this.getStreamId_(), InvalidationReason.OUT_OF_ORDER);
        }

        return ++_seqnum;
    }

    @Override
    public String toString()
    {
        return "->[" + super.toString() + "]";
    }
}