package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreDeviceLRU;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.event.net.tx.EOBeginStream;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.event.net.tx.EOTxAbortStream;
import com.aerofs.daemon.event.net.tx.EOTxEndStream;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;

public class UnicastOutputBottomLayer implements IUnicastOutputLayer
{
    private static final Logger l = Loggers.getLogger(UnicastOutputBottomLayer.class);


    public static class Factory
    {
        private final CoreDeviceLRU _dlru;
        private final TC _tc;
        private final Transports _tps;

        @Inject
        public Factory(Transports tps, TC tc, CoreDeviceLRU dlru)
        {
            _tps = tps;
            _tc = tc;
            _dlru = dlru;
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

    @Override
    public void sendUnicastDatagram_(byte[] bs, PeerContext pc)
        throws ExNoResource, ExNotFound
    {
        _f._dlru.addDevice_(pc.did());

        pc.tp().q().enqueueThrows(new EOUnicastMessage(pc.did(), bs), _f._tc.prio());
    }

    @Override
    public void beginOutgoingStream_(StreamID streamId, byte[] bs, PeerContext pc, Token tk)
        throws Exception
    {
        _f._dlru.addDevice_(pc.did());

        IIMCExecutor imce = _f._tps.getIMCE_(pc.tp());
        EOBeginStream ev = new EOBeginStream(streamId, pc.did(), bs, imce);
        try {
            CoreIMC.execute_(ev, _f._tc, tk);
        } catch (Exception e) {
            l.debug("begin stream failed strmid " + streamId + " " + pc + ": " + e);
            throw e;
        }
    }

    @Override
    public void sendOutgoingStreamChunk_(StreamID streamId, int seq, byte[] bs, PeerContext pc,
            Token tk) throws Exception
    {
        _f._dlru.addDevice_(pc.did());

        IIMCExecutor imce = _f._tps.getIMCE_(pc.tp());
        EOChunk ev = new EOChunk(streamId, seq, pc.did(), bs, imce);
        try {
            CoreIMC.execute_(ev, _f._tc, tk);
        } catch (Exception e) {
            l.debug("send chunk failed strmid " + streamId + " " + pc + ": " + e);
            throw e;
        }
    }

    @Override
    public void abortOutgoingStream_(StreamID streamId, InvalidationReason reason, PeerContext lc)
        throws ExNoResource, ExAborted
    {
        // the transport layer shall guarantee delivery of the abortion
        // to the receiver. Like sending chunks, here we don't wait for
        // the abortion to be acknowledged.
        //
        // TODO don't use IEBIMC for EOTx/RxAbortStream, EOTx/RxEndStream

        IIMCExecutor imce = _f._tps.getIMCE_(lc.ep().tp());

        EOTxAbortStream ev = new EOTxAbortStream(streamId, reason, imce);
        CoreIMC.enqueueBlocking_(ev, _f._tc, Cat.UNLIMITED);
    }

    @Override
    public void endOutgoingStream_(StreamID streamId, PeerContext pc)
        throws ExNoResource, ExAborted
    {
        // send end-message event even if no actual message was sent due to
        // errors after _id was set, since we're not sure if transport state
        // for this message has been established on errors.

        IIMCExecutor imce = _f._tps.getIMCE_(pc.ep().tp());

        EOTxEndStream ev = new EOTxEndStream(streamId, imce);
        CoreIMC.enqueueBlocking_(ev, _f._tc, Cat.UNLIMITED);
    }

    @Override
    public void endIncomingStream_(StreamID streamId, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        IIMCExecutor imce = _f._tps.getIMCE_(pc.ep().tp());

        EORxEndStream ev = new EORxEndStream(pc.did(), streamId, imce);
        CoreIMC.enqueueBlocking_(ev, _f._tc, Cat.UNLIMITED);
    }
}
