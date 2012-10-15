package com.aerofs.daemon.core.net;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.id.DID;

public class IncomingStreams
{
    private static final Logger l = Util.l(IncomingStreams.class);

    public static class StreamKey implements Comparable<StreamKey>
    {
        final DID _did;
        final StreamID _strid;

        public StreamKey(DID sender, StreamID strid)
        {
            _did = sender;
            _strid = strid;
        }

        @Override
        public int compareTo(StreamKey k)
        {
            int ret = _did.compareTo(k._did);
            return ret != 0 ? ret : _strid.compareTo(k._strid);
        }

        @Override
        public String toString()
        {
            return _did + ":" + _strid;
        }
    }

    private static class IncomingStream
    {
        final PeerContext _pc;
        final Queue<ByteArrayInputStream> _chunks = Lists.newLinkedList();
        TCB _tcb;
        InvalidationReason _invalidationReason;
        int _seq; // the last seq received. see IUnicastOutputLayer.sendOutgoingStreamChunk comment

        IncomingStream(PeerContext pc)
        {
            _pc = pc;
        }
    }

    private final Map<StreamKey, IncomingStream> _map = Maps.newTreeMap();

    // streams that are ended but failed to call endIncomingStream_.
    // new streams are not allowed to create until all ended streams are gone.
    // the size of this list is bounded by Categories.
    private final Map<StreamKey, IncomingStream> _ended = Maps.newTreeMap();

    private final UnicastInputOutputStack _stack;

    @Inject
    public IncomingStreams(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    public void begun_(StreamKey key, PeerContext pc)
        throws ExProtocolError, ExNoResource, ExAborted
    {
        if (_map.containsKey(key)) {
            throw new ExProtocolError("stream " + key + " already begun");
        }

        // clear ended streams first
        Iterator<Entry<StreamKey, IncomingStream>> iter = _ended.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<StreamKey, IncomingStream> en = iter.next();
            _stack.output().endIncomingStream_(en.getKey()._strid, en.getValue()._pc);
            iter.remove();
        }

        IncomingStream value = new IncomingStream(pc);
        _map.put(key, value);
    }

    // N.B. the caller must end the stream if timeout happens, otherwise
    // processChunk_() would break the rule imposed by TC.resume_().
    public ByteArrayInputStream recvChunk_(StreamKey key, Token tk)
            throws ExTimeout, ExStreamInvalid, ExNoResource, ExAborted
    {
        IncomingStream v = _map.get(key);
        assert v != null : key;
        assert v._tcb == null : v._tcb;

        if (v._invalidationReason == null && v._chunks.isEmpty()) {
            // no chunk available. wait for one
            v._tcb = TC.tcb();

            try {
                tk.pause_(Cfg.timeout(), "recvChunk " + key);
            } finally {
                v._tcb = null;
            }

            assert v._invalidationReason != null || !v._chunks.isEmpty();

        }

        if (v._invalidationReason != null) throw new ExStreamInvalid(v._invalidationReason);

        return v._chunks.poll();
    }

    private void resume_(IncomingStream v)
    {
        if (v._tcb != null) v._tcb.resume_();
    }

    private final FrequentDefectSender _fds = new FrequentDefectSender();

    public void processChunk_(StreamKey key, int seq, ByteArrayInputStream chunk)
    {
        IncomingStream v = _map.get(key);
        if (v == null) {
            l.info("recv chunk after strm ends " + key);

        } else if (seq != ++v._seq) {
                _fds.logSendAsync("istrm " + key + " expect seq " + v._seq + " actual " + seq);

                // notify the receiver
                aborted_(key, InvalidationReason.OUT_OF_ORDER);
                // notify lower layers
                end_(key);

        } else {
            v._chunks.add(chunk);
            resume_(v);
        }
    }

    public void aborted_(StreamKey key, InvalidationReason reason)
    {
        IncomingStream v = _map.get(key);
        if (v == null) {
            l.info("aborted after strm ends " + key);

        } else {
            v._invalidationReason = reason;
            resume_(v);
        }
    }

    // use this method either to naturally end the stream or forcibly abort the
    // stream from the receiving side. The sender will be notified when the
    // receiver receives the next chunk.
    //
    // another approach to abort the stream is to send IEORxAbortStream, which
    // immediately tells the sender to abort, rather than of doing so on
    // receiving the next chunk.
    //
    public void end_(StreamKey k)
    {
        IncomingStream v = _map.remove(k);
        if (v == null) {
                l.warn("end no strm. from finally of a failed block?");
                return;
        }

        try {
            _stack.output().endIncomingStream_(k._strid, v._pc);
        } catch (Exception e) {
            l.warn("cannot end stream " + k + ", backlog it: " + Util.e(e));
            _ended.put(k, v);
        }
    }
}
