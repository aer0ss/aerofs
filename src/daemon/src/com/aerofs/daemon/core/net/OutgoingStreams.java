/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;


// we don't use OutputStream to avoid confusion with java.physical.OutputStream
//
public class OutgoingStreams
{
    private static final Logger l = Loggers.getLogger(OutgoingStreams.class);

    public final class OutgoingStream implements IOutgoingStreamFeedback
    {
        private final Endpoint _ep;
        private final Token _tk;
        private final StreamID _strmid;
        private boolean _started;
        private int _seq;
        private InvalidationReason _invalidationReason;
        private EOChunk _firstFailedChunk;
        private final ChunksCounter _chunksCounter;

        private OutgoingStream(Endpoint ep, Token tk)
        {
            _ep = ep;
            _tk = tk;
            _strmid = nextID_();
            _chunksCounter = new ChunksCounter();
        }

        // Only for debugging performance issues
        //private final ThroughputCounter _tput = new ThroughputCounter("send chunk");

        public void sendChunk_(byte[] bs) throws Exception
        {
            if (!_started) {
                _started = true;
                _stack.output().beginOutgoingStream_(_strmid, bs, _ep, _tk);
            } else {
                _stack.output().sendOutgoingStreamChunk_(_strmid, ++_seq, bs, _ep, _tk);
            }

            //_tput.observe(bs.length);
        }

        public void abort_(InvalidationReason reason)
        {
            l.warn("abort {} {}", this, reason.name());

            if (_strmid != null) {
                _streams.remove(_strmid);
                try {
                    _stack.output().abortOutgoingStream_(_strmid, reason, _ep);
                } catch (Exception e) {
                    l.warn("fail abort {}. backlogged: ", this, Util.e(e));
                    _invalidationReason = reason;
                    _failedToAbort.add(this);
                }
            }
        }

        public void end_()
        {
            l.info("end {}", this);

            if (_strmid != null) {
                _streams.remove(_strmid);
                try {
                    _stack.output().endOutgoingStream_(_strmid, _ep);
                } catch (Exception e) {
                    l.warn("fail end " + this + ". backlogged: " + Util.e(e));
                    _failedToEnd.add(this);
                }
            }
        }

        @Override
        public String toString()
        {
            return "ostrm " + _ep.tp() + ":" + _ep.did() + ":" + _strmid + " seq:" + _seq;
        }

        @Override
        public void incChunkCount()
        {
            _chunksCounter.incChunkCount();
        }

        @Override
        public void decChunkCount()
        {
            _chunksCounter.decChunkCount();
        }

        public void waitIfTooManyChunks_() throws ExAborted
        {
            _chunksCounter.waitIfTooManyChunks_(_tk);
        }

        @Override
        public void setFirstFailedChunk(EOChunk chunk)
        {
            if (_firstFailedChunk == null) {
                checkNotNull(chunk.exception());
                _firstFailedChunk = chunk;
            }
        }

        public void throwIfFailedChunk() throws Exception
        {
            if (_firstFailedChunk != null) throw _firstFailedChunk.exception();
        }
    }

    private int _id = 0;

    private final Map<StreamID, OutgoingStream> _streams = Maps.newConcurrentMap();

    // these are streams that have failed to abort or end
    private final List<OutgoingStream> _failedToAbort = new LinkedList<OutgoingStream>();
    private final List<OutgoingStream> _failedToEnd = new LinkedList<OutgoingStream>();

    private final UnicastInputOutputStack _stack;

    @Inject
    public OutgoingStreams(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    private StreamID nextID_()
    {
        return new StreamID(_id++);
    }

    /* usage:
     *
     *  os = newStream();
     *  try { ... } finally ( os.end_(); }
     *
     *  new stream won't be created until all backlog streams are finished.
     */
    public OutgoingStream newStream(Endpoint ep, Token tk)
        throws ExNoResource, ExAborted
    {
        Iterator<OutgoingStream> iter = _failedToAbort.iterator();
        while (iter.hasNext()) {
            OutgoingStream os = iter.next();
            _stack.output().abortOutgoingStream_(os._strmid, os._invalidationReason, os._ep);
            iter.remove();
        }

        iter = _failedToEnd.iterator();
        while (iter.hasNext()) {
            OutgoingStream os = iter.next();
            _stack.output().endOutgoingStream_(os._strmid, os._ep);
            iter.remove();
        }

        OutgoingStream stream = new OutgoingStream(ep, tk);
        _streams.put(stream._strmid, stream);
        l.info("create {}", stream);
        return stream;
    }

    public OutgoingStream getStreamThrows(@Nonnull StreamID streamID)
            throws ExStreamInvalid
    {
        OutgoingStream stream = _streams.get(checkNotNull(streamID));
        if (stream == null) throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        return stream;
    }
}
