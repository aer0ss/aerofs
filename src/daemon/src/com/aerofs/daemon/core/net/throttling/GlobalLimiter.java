package com.aerofs.daemon.core.net.throttling;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.net.IUnicastOutputLayer;
import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.ICfgDatabaseListener;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Limit;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static com.aerofs.daemon.core.net.throttling.LimitParam._MAX_UL_BW;
import static com.aerofs.daemon.core.net.throttling.LimitParam._MIN_UL_BW;
import static com.aerofs.lib.Param.Throttling.MIN_BANDWIDTH_UI;
import static com.aerofs.lib.Param.Throttling.UNLIMITED_BANDWIDTH;

// TODO: How to I find out available bandwidth?
// TODO: Have to have a parameter for amount of headers the transport adds
public class GlobalLimiter extends AbstractLimiter implements IUnicastOutputLayer, ICfgDatabaseListener
{
    private static final String _GLOBAL_LIMITER_NAME = "Gbl";
    private static final String _PER_DEVICE_LIMITER_PREFIX = "Per";

    @Nonnull
    private final IUnicastOutputLayer _lower;

    @Nonnull
    private final Map<DID, ILimiter> _deviceQ;

    static {
        assert MIN_BANDWIDTH_UI > _MIN_UL_BW;
    }

    private static long getUploadBw_()
    {
        long ulbw = 0;
        long bwread = Cfg.db().getLong(Key.MAX_UP_RATE);
        if (bwread == UNLIMITED_BANDWIDTH) {
            ulbw = _MAX_UL_BW;
        } else if (bwread > _MIN_UL_BW) {
            ulbw = bwread;
        } else {
            ulbw = _MIN_UL_BW;
        }

        return ulbw;
    }

    public static class Factory
    {
        private final TC _tc;
        private final CoreScheduler _sched;

        @Inject
        public Factory(CoreScheduler sched, TC tc)
        {
            _sched = sched;
            _tc = tc;
        }

        public GlobalLimiter create_(IUnicastOutputLayer lower)
        {
            return new GlobalLimiter(this, lower);
        }
    }

    private final Factory _f;

    private GlobalLimiter(Factory f, IUnicastOutputLayer lower)
    {
        super(f._sched, Util.l(GlobalLimiter.class),
            _MIN_UL_BW, getUploadBw_(), getUploadBw_() /* sigh */,
            _MAX_SHAPING_Q_BACKLOG);

        _f = f;
        _lower = lower;
        _deviceQ = new HashMap<DID, ILimiter>();

        Cfg.db().addListener(this);

        l.debug("construct " + _GLOBAL_LIMITER_NAME + ": " + this);
    }

    /**
     * <b>VERY VERY IMPORTANT:</b> Entry point into the entire choking system!
     */
    public void process_(Outgoing o, Prio p)
        throws Exception
    {
        // if a per-device choker exists, start the message flowing through it,
        // otherwise start the message through us

        ILimiter lim = this;
        DID d = o.getCtx().did();

        if (_deviceQ.containsKey(d)) {
            lim = _deviceQ.get(d);
        }

        l.trace(name() + ": o route:" + lim.name());

        lim.processOutgoing_(o, p);
    }

    //
    // ILimiter
    //

    @Override
    public void processConfirmedOutgoing_(Outgoing o, Prio p)
        throws Exception
    {
        if (l.isDebugEnabled()) {
            l.trace(name() + ": msg route lower: t:" + Outgoing.toStringType(o.getType()) +
                " b:" + o.getLength());
            l.trace(printstat_());
        }

        switch (o.getType()) {
            case UNICAST:
                _lower.sendUnicastDatagram_(o.serialize(), o.getCtx());
                break;
            case STREAM_BEGIN:
            case STREAM_CHUNK:
                o.finishProcessing(null);
                break;
            default:
                assert false;
        }
    }

    @Override
    public void processControlLimit_(DID d, Limit.PBLimit pbl)
    {
        // the only check we do to ensure that this has the parameters we need
        assert pbl.getType() == Limit.PBLimit.Type.ALLOCATE && pbl.hasBandwidth();

        ILimiter lim = _deviceQ.get(d);
        if (lim != null) {
            l.trace(name()+ ": pbl route:" + lim.name());
            lim.processControlLimit_(d, pbl);
        } else {
            lim = new PerDeviceLimiter(_f._sched, this, _PER_DEVICE_LIMITER_PREFIX + "/" + d,
                _MIN_UL_BW,
                ((pbl.getBandwidth() < _MIN_UL_BW) ? _MIN_UL_BW : pbl.getBandwidth()),
                pbl.getBandwidth(),
                _MAX_SHAPING_Q_BACKLOG);
            _deviceQ.put(d, lim);

            l.debug(name() + ": create:" + lim);
        }
    }

    @Override
    public String name()
    {
        return _GLOBAL_LIMITER_NAME;
    }

    //
    // IUpperLayer, ILowerLayer
    //

    @Override
    public void sendUnicastDatagram_(byte[] bs, PeerContext pc)
        throws Exception
    {
        l.trace("send datagram " + pc);

        Outgoing o = new Outgoing(_f._tc, bs, pc);
        process_(o, _f._tc.prio());
    }

    @Override
    public void beginOutgoingStream_(StreamID streamId, byte[] bs, PeerContext pc, Token tk)
        throws Exception
    {
        l.trace("begin outgoing stream:" + streamId.toString() + " " + pc);

        Outgoing o = new Outgoing(_f._tc, bs, pc, streamId, 0, tk);
        process_(o, _f._tc.prio());
        o.pauseProcessing();

        _lower.beginOutgoingStream_(o.getSid(), o.serialize(), o.getCtx(), o.getTok());
    }

    @Override
    public void sendOutgoingStreamChunk_(StreamID streamId, int seq, byte[] bs, PeerContext pc, Token tk)
        throws Exception
    {
        l.trace("send outgoing chunk stream:" + streamId + " " + seq + " " + pc);

        Outgoing o = new Outgoing(_f._tc, bs, pc, streamId, seq, tk);
        process_(o, _f._tc.prio());
        o.pauseProcessing();

        _lower.sendOutgoingStreamChunk_(o.getSid(), o.getSeq(), o.serialize(), o.getCtx(),
                o.getTok());
    }

    @Override
    public void endOutgoingStream_(StreamID streamId, PeerContext pc)
        throws ExNoResource, ExAborted
    {
        l.trace("end outgoing stream:" + streamId + " " + pc);

        _lower.endOutgoingStream_(streamId, pc);
    }

    @Override
    public void endIncomingStream_(StreamID streamId, PeerContext pc)
        throws ExNoResource, ExAborted
    {
        l.trace("end incoming stream:" + streamId + " " + pc);

        _lower.endIncomingStream_(streamId, pc);
    }

    @Override
    public void abortOutgoingStream_(StreamID streamId, InvalidationReason reason, PeerContext pc)
        throws ExNoResource, ExAborted
    {
        l.trace("abort outgoing stream:" + streamId + " " + pc);

        _lower.abortOutgoingStream_(streamId, reason, pc);
    }

    //
    // ICfgDatabaseListener
    //

    @Override
    public void valueChanged_(Key key)
    {
        if (key != Key.MAX_UP_RATE) return;

        long newBw = getUploadBw_();
        setBwParams_(newBw);
    }
}
