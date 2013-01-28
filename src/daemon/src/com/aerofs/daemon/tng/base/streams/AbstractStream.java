/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.base.id.DID;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IStream;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * <strong>IMPORTANT:</strong> This class should never be concrete.
 */
abstract class AbstractStream implements IStream
{
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final StreamID _id;
    private final DID _did;
    private final SID _sid;
    private final Prio _pri;
    private final int _hash;
    private final UncancellableFuture<Void> _closeFuture = UncancellableFuture.createCloseFuture();

    protected AbstractStream(ISingleThreadedPrioritizedExecutor executor, StreamID id, DID did,
            SID sid, Prio pri)
    {
        this._executor = executor;
        this._id = id;
        this._did = did;
        this._sid = sid;
        this._pri = pri;
        this._hash = hash(id, did, sid, pri);
    }

    private static int hash(StreamID id, DID did, SID sid, Prio pri) // FIXME: broken
    {
        HashFunction hf = Hashing.goodFastHash(Integer.SIZE);
        HashCode hc = hf.newHasher()
                .putLong(id.hashCode())
                .putLong(did.hashCode())
                .putLong(sid.hashCode())
                .putLong(pri.hashCode())
                .hash();

        return hc.asInt();
    }

    @Override
    public final boolean equals(Object o)
    {
        if (o == this) return true;
        if (!(o instanceof AbstractStream)) return false;

        AbstractStream stream = (AbstractStream) o;

        return stream.getStreamId_().equals(getStreamId_()) && stream.getDid_().equals(getDid_()) &&
                stream.getSid_().equals(getSid_()) && stream.getPriority_().equals(getPriority_());
    }

    @Override
    public final int hashCode()
    {
        return _hash;
    }

    @Override
    public StreamID getStreamId_()
    {
        return _id;
    }

    @Override
    public DID getDid_()
    {
        return _did;
    }

    @Override
    public SID getSid_()
    {
        return _sid;
    }

    @Override
    public Prio getPriority_()
    {
        return _pri;
    }

    @Override
    public ListenableFuture<Void> getCloseFuture_()
    {
        return _closeFuture;
    }

    protected UncancellableFuture<Void> getSettableCloseFuture_()
    {
        return _closeFuture;
    }

    @Override
    public String toString()
    {
        return "id:" + getStreamId_() + " d:" + getDid_() + " s:" + getSid_() + " pri:" +
                getPriority_();
    }

    protected final void execute(Runnable runnable)
    {
        _executor.execute(runnable, getPriority_());
    }
}
