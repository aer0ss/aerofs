package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// we maintain outgoing stream state at the transport layer rather than
// the core because transport has better knowledge on when a stream has
// to be ended due to network errors. also, some other protocols may need
// more stream-related state than what is maintained here.
//
// NB. access to this object must be protected by synchronized, as transports
// may be multi-threaded.
//
// FIXME (AG): This class should be refactored and replaced with atomic maps. (Thanks JP!)
//
public class StreamManager
{
    private static final Logger l = Loggers.getLogger(StreamManager.class);

    public static class OutgoingStream {
        final DID _did;
        final Object _cookie;
        //int _seq; the core checks the sequence numbers end-to-end already.
                // and therefore this field is useful only for debugging

        OutgoingStream(DID did, Object cookie)
        {
            _did = did;
            _cookie = cookie;
        }
    }

    private static class IncomingStream {
        boolean _begun;
        //int _seq; the core checks the sequence numbers end-to-end already.
        // and therefore this field is useful only for debugging
    }

    // TODO ask the core to pass down device ids, instead of storing them here?
    private final Map<StreamID, OutgoingStream> _sid2ostrm =
        new TreeMap<StreamID, OutgoingStream>();
    private final Map<DID, Set<StreamID>> _did2sids =
        new TreeMap<DID, Set<StreamID>>();
    private final Map<DID, Map<StreamID, IncomingStream>> _did2istrms =
        new TreeMap<DID, Map<StreamID, IncomingStream>>();

    public synchronized void newOutgoingStream(StreamID sid, OutgoingStream ostrm)
    {
        assert sid != null;
        Util.verify(_sid2ostrm.put(sid, ostrm) == null);
        Set<StreamID> sids = _did2sids.get(ostrm._did);
        if (sids == null) {
            sids = new TreeSet<StreamID>();
            _did2sids.put(ostrm._did, sids);
        }
        Util.verify(sids.add(sid));
    }

    public synchronized OutgoingStream getOutgoingStreamThrows(StreamID id)
        throws ExStreamInvalid
    {
        OutgoingStream ostrm = _sid2ostrm.get(id);
        if (ostrm == null) throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        return ostrm;
    }

    public synchronized OutgoingStream removeOutgoingStream(StreamID sid)
    {
        OutgoingStream ostrm = _sid2ostrm.remove(sid);
        if (ostrm != null) {
            Set<StreamID> sids = _did2sids.get(ostrm._did);
            assert sids != null;
            Util.verify(sids.remove(sid));
            if (sids.isEmpty()) _did2sids.remove(ostrm._did);
        }

        return ostrm;
    }

    public OutgoingStream removeOutgoingStreamThrows(StreamID strm)
        throws ExStreamInvalid
    {
        OutgoingStream ostrm = removeOutgoingStream(strm);
        if (ostrm == null) throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        return ostrm;
    }

    public synchronized void removeAllOutgoingStreams(DID did)
    {
        Set<StreamID> sids = _did2sids.remove(did);
        if (sids != null) {
            for (StreamID sid : sids) {
                l.info("remove ostrm " + sid);
                _sid2ostrm.remove(sid);
            }
        }
    }

    // TODO use StreamKey (also for relevant events)?
    public synchronized void newIncomingStream(DID did, StreamID strm)
    {
        Map<StreamID, IncomingStream> strms = _did2istrms.get(did);
        if (strms == null) {
            strms = new TreeMap<StreamID, IncomingStream>();
            _did2istrms.put(did, strms);
        }
        Util.verify(strms.put(strm, new IncomingStream()) == null);
    }

    public synchronized void removeIncomingStream(DID did, StreamID strm)
    {
        Map<StreamID, IncomingStream> strms = _did2istrms.get(did);
        if (strms != null && strms.remove(strm) != null && strms.isEmpty()) {
            _did2istrms.remove(did);
        }
    }

    public synchronized void removeIncomingStreamThrows(DID did, StreamID strm)
        throws ExStreamInvalid
    {
        Map<StreamID, IncomingStream> strms = _did2istrms.get(did);
        if (strms == null || strms.remove(strm) == null) {
            throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        }
        if (strms.isEmpty()) _did2istrms.remove(did);
    }

    public synchronized Set<StreamID> removeAllIncomingStreams(DID did)
    {
        Map<StreamID, IncomingStream> strms = _did2istrms.remove(did);
        if (strms == null) return Collections.emptySet();
        else return strms.keySet();
    }

    // FIXME (AG): This is the worst 14 lines of code ever. Like Ever. Like Ever Ever.
    /**
     * @return  Whether the stream has already begun. If it has not already begun, then it is marked
     *          as begun from now on.
     * @throws ExStreamInvalid if the stream is not found
     */
    public synchronized boolean hasStreamBegun(DID did, StreamID sid)
            throws ExStreamInvalid
    {
        Map<StreamID, IncomingStream> strms = _did2istrms.get(did);
        if (strms == null) {
            throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        }

        IncomingStream strm = strms.get(sid);
        if (strm == null) {
            throw new ExStreamInvalid(InvalidationReason.STREAM_NOT_FOUND);
        } else if (strm._begun) {
            return true;
        } else {
            strm._begun = true;
            strms.put(sid, strm);
            return false;
        }
    }

    public synchronized boolean streamExists(DID did, StreamID streamID)
    {
        Map<StreamID, IncomingStream> streams = _did2istrms.get(did);
        return streams != null && streams.containsKey(streamID);
    }
}
