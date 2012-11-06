/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

public final class IncomingStreams
{
    private static final Logger l = Util.l(IncomingStreams.class);

    public final static class StreamKey implements Comparable<StreamKey>
    {
        final DID _did;
        final StreamID _strmid;

        public StreamKey(DID sender, StreamID strmid)
        {
            _did = sender;
            _strmid = strmid;
        }

        @Override
        public int compareTo(StreamKey key)
        {
            int ret = _did.compareTo(key._did);
            return ret != 0 ? ret : _strmid.compareTo(key._strmid);
        }

        @Override
        public String toString()
        {
            return _did + ":" + _strmid;
        }
    }

    private final static class IncomingStream
    {
        final PeerContext _pc;
        final Queue<ByteArrayInputStream> _chunks = Lists.newLinkedList();
        TCB _tcb;
        InvalidationReason _invalidationReason;
        int _seq; // the last seq received. see IUnicastOutputLayer.sendOutgoingStreamChunk comment
        public int _seqMismatchChunkCount;

        IncomingStream(PeerContext pc)
        {
            _pc = pc;
        }

        @Override
        public String toString()
        {
            return "istrm " + _pc.tp() + ":" + _pc.did() + " seq:" + _seq;
        }
    }

    private final FrequentDefectSender _fds = new FrequentDefectSender();
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
            _stack.output().endIncomingStream_(en.getKey()._strmid, en.getValue()._pc);
            iter.remove();
        }

        IncomingStream stream = new IncomingStream(pc);
        _map.put(key, stream);

        l.info("create " + stream + ":" + key);
    }

    // N.B. the caller must end the stream if timeout happens, otherwise
    // processChunk_() would break the rule imposed by TC.resume_().
    public ByteArrayInputStream recvChunk_(StreamKey key, Token tk)
            throws ExTimeout, ExStreamInvalid, ExNoResource, ExAborted
    {
        IncomingStream stream = _map.get(key);
        assert stream != null : key;
        assert stream._tcb == null : stream._tcb;

        if (stream._invalidationReason == null && stream._chunks.isEmpty()) {
            // no chunk available. wait for one
            stream._tcb = TC.tcb();

            try {
                tk.pause_(Cfg.timeout(), "recvChunk " + key);
            } finally {
                stream._tcb = null;
            }

            assert stream._invalidationReason != null || !stream._chunks.isEmpty();
        }

        if (stream._invalidationReason != null) {
            throw new ExStreamInvalid(stream._invalidationReason);
        }

        return stream._chunks.poll();
    }

    private void resume_(IncomingStream stream)
    {
        if (stream._tcb != null) stream._tcb.resume_();
    }

    public void processChunk_(StreamKey key, int seq, ByteArrayInputStream chunk)
    {
        IncomingStream stream = _map.get(key);
        if (stream == null) {
            l.warn("recv chunk after end " + stream + ":" + key);
        } else if (seq != ++stream._seq) {
            stream._seqMismatchChunkCount++; // [puzzled] this should happen only once, no?

            if (stream._invalidationReason == null) { // not aborted
                _fds.logSendAsync("istrm " + stream._pc.tp() +
                        ":" + key + " expect seq " + stream._seq + " actual " + seq);
                aborted_(key, InvalidationReason.OUT_OF_ORDER); // notify receiver
                end_(key); // notify lower layers
            } else {
                l.warn("istrm " + stream + " recv chunk after abort seq:" + seq);
            }
        } else {
            stream._chunks.add(chunk);
            resume_(stream);
        }
    }

    public void aborted_(StreamKey key, InvalidationReason reason)
    {
        assert reason != null;

        IncomingStream stream = _map.get(key);
        if (stream == null) {
            l.warn("abort " + stream + " key:" + key + " after stream end");
        } else {
            l.warn("abort " + stream + " key:" + key + " rsn:" + reason);

            stream._invalidationReason = reason;
            resume_(stream);
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
    public void end_(StreamKey key)
    {
        IncomingStream stream = _map.remove(key);
        if (stream == null) {
            l.warn("end called but no stream key:" + key);
            return;
        }

        try {
            l.info("end" + stream + " key:" + key);

            _stack.output().endIncomingStream_(key._strmid, stream._pc);
        } catch (Exception e) {
            l.warn("cannot end " + stream + " key:" + key + ", backlog it: " + Util.e(e));

            _ended.put(key, stream);
        }
    }
}
