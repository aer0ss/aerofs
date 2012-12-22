/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.lib.async.FailedFutureCallback;
import com.aerofs.lib.async.FutureUtil;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.lib.async.FutureUtil.addCallback;
import static com.aerofs.proto.Transport.PBStream.Type.BEGIN_STREAM;
import static com.aerofs.proto.Transport.PBStream.Type.PAYLOAD;
import static com.aerofs.proto.Transport.PBStream.Type.TX_ABORT_STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

class OutgoingStream extends AbstractStream implements IOutgoingStream
{
    private final IConnection _connection;
    private final AtomicBoolean _begun = new AtomicBoolean(false);
    private final AtomicBoolean _invalidated = new AtomicBoolean(false);
    private final UncancellableFuture<Void> _beginFuture = UncancellableFuture.create();

    private int _seqnum = 0;

    static OutgoingStream getInstance_(ISingleThreadedPrioritizedExecutor executor,
            IConnection connection, StreamID id, DID did, SID sid, Prio pri)
            throws ExStreamAlreadyExists
    {
        final OutgoingStream stream = new OutgoingStream(executor, connection, id, did, sid, pri);

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

    private OutgoingStream(ISingleThreadedPrioritizedExecutor executor, IConnection connection,
            StreamID id, DID did, SID sid, Prio pri)
    {
        super(executor, id, did, sid, pri);
        this._connection = connection;
    }

    private void chainToCloseFuture_(ListenableFuture<Void> operationFuture)
    {
        addCallback(operationFuture, new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable t)
            {
                _invalidated.set(true);

                getSettableCloseFuture_().setException(t);
            }
        });
    }

    private ListenableFuture<Void> send_(PBTPHeader hdr, @Nullable byte[] payload)
    {
        return _connection.send_(new OutgoingAeroFSPacket(hdr, payload), getPriority_());
    }

    // FIXME: in all cases we do not handle abort before send case
    // i.e abort is called on stream before the packet is actually sent over the wire (no way
    // to cancel right now)

    ListenableFuture<Void> begin_()
    {
        if (_invalidated.get()) {
            return FutureUtil.chain(UncancellableFuture.<Void>create(), getSettableCloseFuture_());
        }

        if (!_begun.getAndSet(true)) {
            execute(new Runnable()
            {
                @Override
                public void run()
                {
                    _beginFuture.chain(send_(createBeginMessage_(), null));
                    chainToCloseFuture_(_beginFuture);
                }
            });
        }

        return _beginFuture;
    }

    @Override
    public ListenableFuture<Void> send_(final byte[] payload)
    {
        // keep this check out here. We want only send calls _not_ already in the
        // pipeline to be terminated. If this check were inside the run() block
        // then when invalidated is set (by any thread) any already-enqueued
        // events will be terminated. This is especially a problem if we do the
        // following: SEND-SEND-SEND-END. If END is executed by T2 before any one
        // of the SEND calls is dispatched by the transport event loop, then _all_ the
        // SEND calls will fail because they will check _invalidated before sending

        if (_invalidated.get()) {
            return FutureUtil.chain(UncancellableFuture.<Void>create(), getSettableCloseFuture_());
        }

        // FIXME: allow chained sends, or assert no send before begin

        final UncancellableFuture<Void> future = UncancellableFuture.create();

        execute(new Runnable()
        {
            @Override
            public void run()
            {
                future.chain(send_(createPayloadHeader_(), payload));
                chainToCloseFuture_(future);
            }
        });

        return future;
    }

    private ListenableFuture<Void> abort_(final boolean abortedByPeer,
            final InvalidationReason reason)
    {
        if (!_invalidated.getAndSet(true)) {
            getSettableCloseFuture_().setException(
                    new ExStreamInvalid(this.getStreamId_(), reason));

            execute(new Runnable()
            {
                @Override
                public void run()
                {
                    if (!abortedByPeer) {
                        send_(createAbortMessage_(reason), null);
                    }
                }
            });
        }

        return getCloseFuture_();
    }

    @Override
    public ListenableFuture<Void> abort_(InvalidationReason reason)
    {
        return abort_(false, reason);
    }

    ListenableFuture<Void> abortByReceiver_(InvalidationReason reason)
    {
        return abort_(true, reason);
    }

    @Override
    public ListenableFuture<Void> end_()
    {
        // We need to execute the end call on the executor to preserver
        // order of execution. Previous send calls should be processed
        // before the end call
        execute(new Runnable()
        {
            @Override
            public void run()
            {
                if (!_invalidated.getAndSet(true)) {
                    getSettableCloseFuture_().setException(
                            new ExStreamInvalid(getStreamId_(), InvalidationReason.ENDED));
                }
            }

        });

        return getCloseFuture_();
    }

    private PBTPHeader createBeginMessage_()
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setSid(getSid_().toPB())
                .setStream(PBStream.newBuilder()
                        .setType(BEGIN_STREAM)
                        .setStreamId(getStreamId_().getInt()))
                .build();
    }

    private PBTPHeader createPayloadHeader_()
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setSid(getSid_().toPB())
                .setStream(PBStream.newBuilder()
                        .setType(PAYLOAD)
                        .setStreamId(getStreamId_().getInt())
                        .setSeqNum(_seqnum++))
                .build();
    }

    private PBTPHeader createAbortMessage_(InvalidationReason reason)
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setSid(getSid_().toPB())
                .setStream(PBStream.newBuilder()
                        .setType(TX_ABORT_STREAM)
                        .setStreamId(getStreamId_().getInt())
                        .setReason(reason))
                .build();
    }

    @Override
    public String toString()
    {
        return "<-[" + super.toString() + "]";
    }
}
