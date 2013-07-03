/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.TC.TCB;
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
import static com.google.common.base.Preconditions.checkState;


// we don't use OutputStream to avoid confusion with java.physical.OutputStream
//
public class OutgoingStreams
{
    private static final Logger l = Loggers.getLogger(OutgoingStreams.class);

    public final class OutgoingStream
    {
        private final PeerContext _pc;
        private final Token _tk;
        private final StreamID _strmid;
        private boolean _started;
        private int _seq;
        private InvalidationReason _invalidationReason;
        private EOChunk _firstFailedChunk;

        // In order to improve the throughput between the core and the transport, we don't want to
        // wait until stream chunks are be sent by the transport. However, we don't want to enqueue
        // too many chunks at once since this would be using the transport's event loop for flow
        // control. So we do flow control manually here, by counting how many chunks we've enqueued
        // on the transport event queue and blocking if we're above some maximum.
        private static final int MAX_WAITING_CHUNKS = 10;
        private volatile int _waitingChunks; // how many EOChunks are sitting on the transport queue
        private final Object _waitingChunksLock = new Object(); // protects access to _waitingChunks

        private OutgoingStream(PeerContext pc, Token tk)
        {
            _pc = pc;
            _tk = tk;
            _strmid = nextID_();
        }

        // Only for debugging performance issues
        //private final Tput _tput = new Tput("send chunk");

        public void sendChunk_(byte[] bs) throws Exception
        {
            if (!_started) {
                _started = true;
                _stack.output().beginOutgoingStream_(_strmid, bs, _pc, _tk);
            } else {
                _stack.output().sendOutgoingStreamChunk_(_strmid, ++_seq, bs, _pc, _tk);
            }

            //_tput.observe(bs.length);
        }

        public void abort_(InvalidationReason reason)
        {
            l.warn("abort " + this);

            if (_strmid != null) {
                _streams.remove(_strmid);
                try {
                    _stack.output().abortOutgoingStream_(_strmid, reason, _pc);
                } catch (Exception e) {
                    l.warn("fail abort " + this + ". backlogged: " + Util.e(e));
                    _invalidationReason = reason;
                    _failedToAbort.add(this);
                }
            }
        }

        public void end_()
        {
            l.info("end " + this);

            if (_strmid != null) {
                _streams.remove(_strmid);
                try {
                    _stack.output().endOutgoingStream_(_strmid, _pc);
                } catch (Exception e) {
                    l.warn("fail end " + this + ". backlogged: " + Util.e(e));
                    _failedToEnd.add(this);
                }
            }
        }

        @Override
        public String toString()
        {
            return "ostrm " + _pc.tp() + ":" + _pc.did() + ":" + _strmid + " seq:" + _seq;
        }

        public void incChunkCount()
        {
            synchronized (_waitingChunksLock) {
                _waitingChunks++;
            }
        }

        public void decChunkCount()
        {
            synchronized (_waitingChunksLock) {
                _waitingChunks--;
                checkState(_waitingChunks >= 0);
                if (_waitingChunks < MAX_WAITING_CHUNKS) _waitingChunksLock.notify();
            }
        }

        public void waitIfTooManyChunks_() throws ExAborted
        {
            synchronized (_waitingChunksLock) {
                if (_waitingChunks >= MAX_WAITING_CHUNKS) {
                    TCB tcb = _tk.pseudoPause_("waiting for tp to send chunks");
                    try {
                        _waitingChunksLock.wait();
                    } catch (InterruptedException e) {
                        // ignore
                    } finally {
                        tcb.pseudoResumed_();
                    }
                }
            }
        }

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
            _stack.output().abortOutgoingStream_(os._strmid, os._invalidationReason, os._pc);
            iter.remove();
        }

        iter = _failedToEnd.iterator();
        while (iter.hasNext()) {
            OutgoingStream os = iter.next();
            _stack.output().endOutgoingStream_(os._strmid, os._pc);
            iter.remove();
        }

        OutgoingStream stream = new OutgoingStream(new PeerContext(ep), tk);
        _streams.put(stream._strmid, stream);
        l.info("create " + stream);
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
