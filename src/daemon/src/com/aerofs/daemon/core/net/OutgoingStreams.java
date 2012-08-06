package com.aerofs.daemon.core.net;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;

// we don't use OutputStream to avoid confusion with java.io.OutputStream
//
public class OutgoingStreams {

    public class OutgoingStream {

        private final PeerContext _pc;
        private final Token _tk;
        private StreamID _strid;
        private int _seq;
        private InvalidationReason _invalidationReason;

        private OutgoingStream(PeerContext pc, Token tk)
        {
            _pc = pc;
            _tk = tk;
        }

        public void sendChunk_(byte[] bs) throws Exception
        {
            if (_strid == null) {
                _strid = nextID_();
                _stack.output().beginOutgoingStream_(_strid, bs, _pc, _tk);
            } else {
                _stack.output().sendOutgoingStreamChunk_(_strid, ++_seq, bs, _pc, _tk);
            }
        }

        public void abort_(InvalidationReason reason)
        {
            if (_strid != null) {
                try {
                    _stack.output().abortOutgoingStream_(_strid, reason, _pc);
                } catch (Exception e) {
                    Util.l(this).warn("cannot abort " + _strid + ". backlogged: " +
                            Util.l(e));
                    _invalidationReason = reason;
                    _aborted.add(this);
                }
            }
        }

        public void end_()
        {
            if (_strid != null) {
                try {
                    _stack.output().endOutgoingStream_(_strid, _pc);
                } catch (Exception e) {
                    Util.l(this).warn("cannot end " + _strid + ". backlogged: " +
                            Util.l(e));
                    _ended.add(this);
                }
            }
        }
    }

    private int _id = 0;

    // these are streams that have failed to abort or end
    private final List<OutgoingStream> _aborted = new LinkedList<OutgoingStream>();
    private final List<OutgoingStream> _ended = new LinkedList<OutgoingStream>();

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
    public OutgoingStream newStream(Endpoint ep, SIndex sidx, Token tk)
        throws ExNoResource, ExAborted
    {
        Iterator<OutgoingStream> iter = _aborted.iterator();
        while (iter.hasNext()) {
            OutgoingStream os = iter.next();
            _stack.output().abortOutgoingStream_(os._strid, os._invalidationReason, os._pc);
            iter.remove();
        }

        iter = _ended.iterator();
        while (iter.hasNext()) {
            OutgoingStream os = iter.next();
            _stack.output().endOutgoingStream_(os._strid, os._pc);
            iter.remove();
        }

        return new OutgoingStream(new PeerContext(ep, sidx), tk);
    }
}
