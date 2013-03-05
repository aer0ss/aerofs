/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.tng;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreDeviceLRU;
import com.aerofs.daemon.core.net.PeerStreamMap;
import com.aerofs.daemon.core.net.PeerStreamMap.IncomingStreamMap;
import com.aerofs.daemon.core.net.PeerStreamMap.OutgoingStreamMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.concurrent.Future;

import static com.aerofs.daemon.core.tc.FutureBasedCoreIMC.blockingWaitForResult_;

public class UnicastOutputBottomLayer implements IUnicastOutputLayer
{
    public static class Factory
    {
        private final CoreDeviceLRU _dlru;
        private final TC _tc;
        private final IMapSIndex2SID _sidx2sid;
        private final PeerStreamMap<IIncomingStream> _incomingStreamMap;
        private final PeerStreamMap<IOutgoingStream> _outgoingStreamMap;

        @Inject
        public Factory(TC tc, CoreDeviceLRU dlru, IMapSIndex2SID sidx2sid,
                @IncomingStreamMap PeerStreamMap<IIncomingStream> incomingStreamMap,
                @OutgoingStreamMap PeerStreamMap<IOutgoingStream> outgoingStreamMap)
        {
            _tc = tc;
            _dlru = dlru;
            _sidx2sid = sidx2sid;
            _incomingStreamMap = incomingStreamMap;
            _outgoingStreamMap = outgoingStreamMap;
        }

        public UnicastOutputBottomLayer create_()
        {
            return new UnicastOutputBottomLayer(this);
        }
    }

    private final Factory _f;

    private UnicastOutputBottomLayer(Factory f)
    {
        _f = f;
    }


    private SID getSID_(PeerContext pc)
            throws ExNotFound
    {
        return _f._sidx2sid.getThrows_(pc.sidx());
    }

    @Override
    public void sendDatagram_(byte[] bs, PeerContext pc)
            throws Exception
    {
        _f._dlru.addDevice_(pc.did());
        pc.tp().sendDatagram_(pc.did(), getSID_(pc), bs, _f._tc.prio());
    }

    @Override
    public void beginOutgoingStream_(StreamID streamId, byte[] bs, PeerContext pc, Token tk)
            throws Exception
    {
        _f._dlru.addDevice_(pc.did());
        try {
            final Future<IOutgoingStream> f = pc.tp()
                    .beginStream_(streamId, pc.did(), getSID_(pc), _f._tc.prio());
            final IOutgoingStream stream = blockingWaitForResult_(f, tk, "begin out strm");

            // Update the map to include this new Outgoing Stream
            _f._outgoingStreamMap.addStream(pc.did(), stream);

            // Send the bytes to the newly created stream
            sendOutgoingStreamInternal_(streamId, bs, pc, tk);
        } catch (Exception e) {
            l.info("begin out strm failed streamId " + streamId + " " + pc + ": " + e);
            _f._outgoingStreamMap.removeStream(pc.did(), streamId);
            throw e;
        }
    }

    @Override
    public void sendOutgoingStreamChunk_(StreamID streamId, int seq, byte[] bs, PeerContext pc, Token tk)
            throws Exception
    {
        _f._dlru.addDevice_(pc.did());
        try {
            sendOutgoingStreamInternal_(streamId, bs, pc, tk);
        } catch (Exception e) {
            l.info("send out strm failed streamId " + streamId + " " + pc + ": " + e);
            _f._outgoingStreamMap.removeStream(pc.did(), streamId);
            throw e;
        }
    }

    private void sendOutgoingStreamInternal_(StreamID streamId, byte[] bs, PeerContext pc, Token tk)
            throws Exception
    {
        IOutgoingStream stream = _f._outgoingStreamMap.getStream(pc.did(), streamId);
        Future<Void> f = stream.send_(bs);
        blockingWaitForResult_(f, tk, "send out strm");
    }

    @Override
    public void endOutgoingStream_(StreamID streamId, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        // send end-message event even if no actual message was sent due to
        // errors after _id was set, since we're not sure if transport state
        // for this message has been established on errors.
        try {
            IOutgoingStream stream = _f._outgoingStreamMap.getStream(pc.did(), streamId);
            stream.end_();
            _f._outgoingStreamMap.removeStream(pc.did(), streamId);
        } catch (com.aerofs.daemon.tng.ex.ExStreamInvalid e) {
            l.warn("No such outgoing stream to end: " + e);
        }
    }

    @Override
    public void abortOutgoingStream_(StreamID streamId, InvalidationReason reason, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        // the transport layer shall guarantee delivery of the abortion
        // to the receiver. Like sending chunks, here we don't wait for
        // the abortion to be acknowledged.
        try {
            IOutgoingStream stream = _f._outgoingStreamMap.getStream(pc.did(), streamId);
            stream.abort_(reason);
            _f._outgoingStreamMap.removeStream(pc.did(), streamId);
        } catch (com.aerofs.daemon.tng.ex.ExStreamInvalid e) {
            l.warn("No such outgoing stream to abort: " + e);
        }
    }

    @Override
    public void endIncomingStream_(StreamID streamId, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        try {
            IIncomingStream stream = _f._incomingStreamMap.getStream(pc.did(), streamId);
            stream.end_();
            _f._incomingStreamMap.removeStream(pc.did(), streamId);
        } catch (com.aerofs.daemon.tng.ex.ExStreamInvalid e) {
            l.warn("No such incoming stream to end: " + e);
        }
    }

    private static final Logger l = Loggers.getLogger(UnicastOutputBottomLayer.class);
}
