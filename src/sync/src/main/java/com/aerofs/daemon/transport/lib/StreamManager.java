package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;

// we maintain outgoing stream state at the transport layer rather than
// the core because transport has better knowledge on when a stream has
// to be ended due to network errors. also, some other protocols may need
// more stream-related state than what is maintained here.
//
// NB: there is one StreamManager per Transport
//
public class StreamManager
{
    private static final Logger l = Loggers.getLogger(StreamManager.class);

    private final Map<DID, Map<StreamID, Boolean>> _osk = new ConcurrentHashMap<>();
    private final Map<DID, Map<StreamID, Boolean>> _isk = new ConcurrentHashMap<>();

    private static void put(Map<DID, Map<StreamID, Boolean>> m, StreamKey sk)
    {
        Map<StreamID, Boolean> s = m.get(sk.did);
        if (s == null) {
            m.putIfAbsent(sk.did, new ConcurrentHashMap<>());
            s = m.get(sk.did);
        }
        s.put(sk.strmid, true);
    }

    private static void remove(Map<DID, Map<StreamID, Boolean>> m, StreamKey sk)
    {
        Map<StreamID, Boolean> s = m.get(sk.did);
        if (s != null) s.remove(sk.strmid);
    }

    private final long _timeout;
    private final AtomicInteger _id = new AtomicInteger();
    private final Map<StreamKey, OutgoingStream> _ostrms = new ConcurrentHashMap<>();
    private final Map<StreamKey, IncomingStream> _istrms = new ConcurrentHashMap<>();

    public StreamManager(long timeout)
    {
        _timeout = timeout;
    }

    public StreamKey newOutgoingStreamKey(DID did) {
        StreamKey sk;
        do {
            sk = new StreamKey(did, new StreamID(_id.getAndIncrement()));
        } while (_ostrms.containsKey(sk));
        return sk;
    }

    public OutgoingStream newOutgoingStream(StreamKey sk, Channel channel) {
        OutgoingStream ostrm = new OutgoingStream(this, sk, channel, _timeout);
        put(_osk, sk);
        checkState(_ostrms.put(sk, ostrm) == null);
        return ostrm;
    }

    public void pauseOutgoingStream(StreamKey sk) {
        OutgoingStream os = _ostrms.get(sk);
        if (os != null) os.pause();
    }

    public void resumeOutgoingStream(StreamKey sk) {
        OutgoingStream os = _ostrms.get(sk);
        if (os != null) os.resume();
    }

    public OutgoingStream removeOutgoingStream(StreamKey sk)
    {
        remove(_osk, sk);
        return _ostrms.remove(sk);
    }

    public void removeAllOutgoingStreams(DID did)
    {
        Map<StreamID, Boolean> s = _osk.remove(did);
        if (s == null) return;

        for (StreamID streamID : s.keySet()) {
            l.info("{} remove outgoing stream {}", did, streamID);
            _ostrms.remove(new StreamKey(did, streamID));
        }
    }

    public void newIncomingStream(StreamKey sk, Channel channel)
    {
        put(_isk, sk);
        checkState(_istrms.put(sk, new IncomingStream(sk, channel, _timeout)) == null);
    }

    public IncomingStream getIncomingStream(StreamKey sk) throws ExStreamInvalid
    {
        IncomingStream istrm = _istrms.get(sk);
        if (istrm == null) throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        return istrm;
    }

    public void removeIncomingStream(StreamKey sk, InvalidationReason reason)
    {
        remove(_isk, sk);
        IncomingStream is = _istrms.remove(sk);
        if (is != null) is.fail(reason);
    }

    public Set<StreamID> removeAllIncomingStreams(DID did)
    {
        Map<StreamID, Boolean> s = _isk.remove(did);
        if (s == null) return Collections.emptySet();

        for (StreamID streamID : s.keySet()) {
            IncomingStream is = _istrms.remove(new StreamKey(did, streamID));
            if (is != null) is.fail(InvalidationReason.STREAM_NOT_FOUND);
        }

        return s.keySet();
    }

    public boolean streamExists(DID did, StreamID streamID)
    {
        return _istrms.containsKey(new StreamKey(did, streamID));
    }
}
