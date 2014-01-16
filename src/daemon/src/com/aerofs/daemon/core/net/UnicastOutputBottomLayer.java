package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.CoreDeviceLRU;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.event.net.tx.EOBeginStream;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.event.net.tx.EOTxAbortStream;
import com.aerofs.daemon.event.net.tx.EOTxEndStream;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class UnicastOutputBottomLayer implements IUnicastOutputLayer
{
    private static final Logger l = Loggers.getLogger(UnicastOutputBottomLayer.class);

    // FIXME (AG): NONE OF THE TRANSPORT EVENTS ACTUALLY REQUIRES CORE/ABSTRACTEBIMC!! GAH! FUCK ME!

    public static class Factory
    {
        private final CoreDeviceLRU _dlru;
        private final TokenManager _tokenManager;
        private final Transports _tps;
        private final OutgoingStreams _outgoingStreams;
        private final TransferStatisticsManager _tsm;

        @Inject
        public Factory(
                Transports tps,
                TokenManager tokenManager,
                CoreDeviceLRU dlru,
                OutgoingStreams oss,
                TransferStatisticsManager tsm)
        {
            _tps = tps;
            _tokenManager = tokenManager;
            _dlru = dlru;
            _outgoingStreams = oss;
            _tsm = tsm;
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
    public void sendUnicastDatagram_(byte[] bs, Endpoint ep)
        throws ExNoResource, ExNotFound
    {
        _f._dlru.addDevice_(ep.did());

        ep.tp().q().enqueueThrows(new EOUnicastMessage(ep.did(), bs), TC.currentThreadPrio());
    }

    @Override
    public void beginOutgoingStream_(StreamID streamId, byte[] bs, Endpoint ep, Token tk)
        throws Exception
    {
        _f._dlru.addDevice_(ep.did());

        OutgoingStream stream = _f._outgoingStreams.getStreamThrows(streamId);

        stream.waitIfTooManyChunks_();

        IIMCExecutor imce = _f._tps.getIMCE_(ep.tp());
        EOBeginStream ev = new EOBeginStream(streamId, stream, ep.did(), bs, ep.tp(), imce, _f._tsm);
        try {
            CoreIMC.enqueueBlocking_(ev, tk);
        } catch (Exception e) {
            l.warn("begin stream failed strmid " + streamId + " " + ep + ": " + e);
            throw e;
        }
    }

    @Override
    public void sendOutgoingStreamChunk_(StreamID streamId, int seq, byte[] bs, Endpoint ep, Token tk)
            throws Exception
    {
        _f._dlru.addDevice_(ep.did());

        OutgoingStream stream = _f._outgoingStreams.getStreamThrows(streamId);

        stream.throwIfFailedChunk();
        stream.waitIfTooManyChunks_();

        IIMCExecutor imce = _f._tps.getIMCE_(ep.tp());
        EOChunk ev = new EOChunk(streamId, stream, seq, ep.did(), bs, ep.tp(), imce, _f._tsm);

        CoreIMC.enqueueBlocking_(ev, _f._tokenManager);
    }

    @Override
    public void abortOutgoingStream_(StreamID streamId, InvalidationReason reason, Endpoint ep)
        throws ExNoResource, ExAborted
    {
        // the transport layer shall guarantee delivery of the abortion
        // to the receiver. Like sending chunks, here we don't wait for
        // the abortion to be acknowledged.
        //
        // TODO don't use IEBIMC for EOTx/RxAbortStream, EOTx/RxEndStream

        IIMCExecutor imce = _f._tps.getIMCE_(ep.tp());

        EOTxAbortStream ev = new EOTxAbortStream(streamId, reason, imce);
        CoreIMC.enqueueBlocking_(ev, _f._tokenManager);
    }

    @Override
    public void endOutgoingStream_(StreamID streamId, Endpoint ep)
        throws ExNoResource, ExAborted
    {
        // send end-message event even if no actual message was sent due to
        // errors after _id was set, since we're not sure if transport state
        // for this message has been established on errors.

        IIMCExecutor imce = _f._tps.getIMCE_(ep.tp());

        EOTxEndStream ev = new EOTxEndStream(streamId, imce);
        CoreIMC.enqueueBlocking_(ev, _f._tokenManager);
    }

    @Override
    public void endIncomingStream_(StreamID streamId, Endpoint ep)
            throws ExNoResource, ExAborted
    {
        IIMCExecutor imce = _f._tps.getIMCE_(ep.tp());

        EORxEndStream ev = new EORxEndStream(ep.did(), streamId, imce);
        CoreIMC.enqueueBlocking_(ev, _f._tokenManager);
    }
}
