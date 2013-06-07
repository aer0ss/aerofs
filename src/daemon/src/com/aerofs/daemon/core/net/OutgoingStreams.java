/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.slf4j.Logger;

// we don't use OutputStream to avoid confusion with java.physical.OutputStream
//
public class OutgoingStreams
{
    private static final Logger l = Loggers.getLogger(OutgoingStreams.class);

    public final class OutgoingStream
    {
        private final PeerContext _pc;
        private final Token _tk;
        private StreamID _strmid;
        private int _seq;
        private InvalidationReason _invalidationReason;

        private OutgoingStream(PeerContext pc, Token tk)
        {
            _pc = pc;
            _tk = tk;
        }

        public void sendChunk_(byte[] bs) throws Exception
        {
            if (_strmid == null) {
                _strmid = nextID_();
                _stack.output().beginOutgoingStream_(_strmid, bs, _pc, _tk);
            } else {
                _stack.output().sendOutgoingStreamChunk_(_strmid, ++_seq, bs, _pc, _tk);
            }
        }

        public void abort_(InvalidationReason reason)
        {
            l.warn("abort " + this);

            if (_strmid != null) {
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
    }

    private int _id = 0;

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
        l.info("create " + stream);
        return stream;
    }
}
