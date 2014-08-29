/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.throttling;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.CoreDeviceLRU;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.core.net.IUnicastInputLayer;
import com.aerofs.daemon.core.net.IUnicastOutputLayer;
import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.net.RawMessage;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.ICfgDatabaseListener;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.proto.Limit;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.aerofs.lib.LibParam.Throttling.UNLIMITED_BANDWIDTH;
import static com.google.common.base.Preconditions.checkNotNull;

// This is a control system
// FIXME: Need to automatically determine link speed
public class LimitMonitor implements IUnicastInputLayer, ICfgDatabaseListener, IDeviceEvictionListener
{
    private static final long _RECOMPUTE_INTERVAL = 1 * C.SEC;
    private static final long _MS_PER_SEC = 1 * C.SEC; // msec
    private static final long _LIMIT_LESS_RESPONSE_DELAY = 10 * C.SEC; // msec

    private static final long _MAX_BW_OVERAGE = 1024; // bytes/sec
    private static final long _MIN_TRANSFER_LEVEL = 500; // bytes/sec
    private static final double _BW_HISTORIC_SMOOTHING_FACTOR = 0.88; // arbitrary (alpha)
    private static final double _PENALTY_FACTOR = 1.05; // arbitrary

    private static Logger l = Loggers.getLogger(LimitMonitor.class);

    private class TransmitInfo
    {
        public long rollingBw = 0; // bytes/sec
        public long bytesIn = 0; // bytes
        public long firstLimitLess = 0;
        public boolean paused = false;
        Endpoint lastEndpoint; // FIXME: this is a hack!!!
    }

    // FIXME: HACK HACK HACK
    // I have to do this because our there's no way to send messages down if you're
    // in the input layer stack

    // input layer stack

    private final IUnicastInputLayer _upperUnicastInput;
    private final IUnicastOutputLayer _lowerInput;

    // output layer stack

    private final IUnicastOutputLayer _lowerUnicastOutput;
    private final ILimiter _lim;

    // LRU

    private final Map<DID, TransmitInfo> _transmitMap;

    // bandwidth

    private long _totalBw; // bytes/sec

    /**
     * Soft-limit on the maximum download bandwidth for this peer. If
     * <code>_totalBw</code> is less than this limit requests for more bandwidth
     * are honored. If it's greater than this limit they are ignored.
     */
    private long _availBwLoLvl; // bytes/sec

    /**
     * Absolute maximum download bandwidth this peer will use. If bandwidth
     * exceeds this level, peers will be rate-limited. This means that there is
     * a range between <code>_availBwLoLvl</code> and <code>_availBwHiLvl</code>
     * within which peers will <i>neither</i> be rate-limited <i>nor</i> allowed
     * more bandwidth.
     */
    private long _availBwHiLvl; // bytes/sec

    private static TreeSet<Map.Entry<DID, TransmitInfo>> createOrderedByBwMap()
    {
        return new TreeSet<>(
                new Comparator<Map.Entry<DID, TransmitInfo>>()
                {
                    @Override
                    public int compare(Map.Entry<DID, TransmitInfo> a,
                            Map.Entry<DID, TransmitInfo> b)
                    {
                        if (a.getValue().rollingBw ==
                                b.getValue().rollingBw) {
                            // arbitrarily break ties via did
                            // there will be no did duplicates because the map
                            // breaks them
                            return a.getKey().compareTo(b.getKey());
                        } else if (a.getValue().rollingBw <
                                b.getValue().rollingBw) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                });
    }

    public static class Factory
    {
        private final CoreDeviceLRU _dlru;
        private final CoreScheduler _sched;

        @Inject
        public Factory(CoreScheduler sched, CoreDeviceLRU dlru)
        {
            _sched = sched;
            _dlru = dlru;
        }

        public LimitMonitor create_(ILimiter lim, IUnicastInputLayer upperUnicastInput,
                IUnicastOutputLayer lowerUnicastOutput)
        {
            return new LimitMonitor(this, lim, upperUnicastInput, lowerUnicastOutput);
        }
    }

    private final Factory _f;

    private LimitMonitor(Factory f, ILimiter lim, IUnicastInputLayer upperUnicastInput,
            IUnicastOutputLayer lowerUnicastOutput)
    {
        _f = f;
        _lim = lim;
        _upperUnicastInput = upperUnicastInput;
        _lowerUnicastOutput = lowerUnicastOutput;
        _lowerInput = lowerUnicastOutput; // [sigh] HACK -> see notes above
        _transmitMap = new HashMap<>();
        setBandwidth_(Cfg.db().getLong(Key.MAX_DOWN_RATE));

        Cfg.db().addListener(this);
        _f._dlru.addEvictionListener_(this);
    }

    public void init_()
    {
        scheduleRecompute(_RECOMPUTE_INTERVAL);
    }

    private static void logbw(DID d, double abw, double rbw)
    {
        l.trace("d:{} cbw:{} rbw:{}", d, abw, rbw);
    }

    private static void lognewalloc(double bw, DID d)
    {
        l.trace("newbw:{} -> d:{}", bw, d);
    }

    private void logsysbw()
    {
        l.trace("ubw:{} abw[l]:{} abw[h]:{}", _totalBw, _availBwLoLvl, _availBwHiLvl);
    }

    private void notifyLayersOfStreamError(StreamID sid, Endpoint ep, InvalidationReason r)
    {
        l.warn("s err:" + r + " sid:" + sid + " d:" + ep.did());

        onStreamAborted_(sid, ep, r);
        try {
            _lowerInput.endIncomingStream_(sid, ep);
        } catch (Exception e) {
            l.error("can't end stream ex:" + e);
        }
    }

    private void readStream(StreamID sid, RawMessage r, Endpoint ep)
    {
        l.trace("rx: t:S sid:{} d:{} b:{}", sid, ep.did(), r._wirelen);

        try {
            processBytesIn(r._is, r._wirelen, ep);
        } catch (Exception e) {
            l.error("ignoring e for pkt from: " + ep.did());
            notifyLayersOfStreamError(sid, ep, InvalidationReason.CHOKE_ERROR);
        }
    }

    private void sendThrottleControl(byte[] msg, Endpoint ep)
            throws Exception
    {
        checkNotNull(ep);
        l.trace("tx lim:tc -> {}", ep.did());

        _lowerUnicastOutput.sendUnicastDatagram_(msg, null, ep);
    }

    private void sendalloc(long bw, Endpoint ep)
            throws Exception
    {
        Limit.PBLimit bwmsg =
                Limit.PBLimit.newBuilder()
                        .setType(Limit.PBLimit.Type.ALLOCATE)
                        .setBandwidth(bw)
                        .build();

        ByteArrayOutputStream ba = new ByteArrayOutputStream(bwmsg.getSerializedSize());
        bwmsg.writeDelimitedTo(ba);
        sendThrottleControl(ba.toByteArray(), ep);

        lognewalloc(bw, ep.did());
    }

    private void respondToTimeout()
    {
        // Maintains a list of paused devices. These devices do not get considered
        // in the bandwidth total because they are always throttled.
        List<TransmitInfo> pausedDevices = Lists.newLinkedList();

        // FIXME: this isn't the most performant approach in which to find out which people have used the most bandwidth...
        // FIXME: look at google-collections and figure out what is the best approach
        TreeSet<Map.Entry<DID, TransmitInfo>> bwhogs = createOrderedByBwMap();
        _totalBw = 0;
        for (Map.Entry<DID, TransmitInfo> tme : _transmitMap.entrySet()) {
            TransmitInfo ti = tme.getValue();
            ti.rollingBw =
                    (long) Math.floor(
                            (_BW_HISTORIC_SMOOTHING_FACTOR * ti.rollingBw) +
                                    ((1 - _BW_HISTORIC_SMOOTHING_FACTOR) *
                                             (ti.bytesIn / (_RECOMPUTE_INTERVAL / (double) _MS_PER_SEC)))
                    );

            logbw(tme.getKey(), ti.bytesIn, ti.rollingBw);

            ti.bytesIn = 0;

            if (ti.paused) {
                // Since we cut the bandwidth for paused devices to a fixed amount
                // we do not need to consider their current bandwidths when
                // reducing
                pausedDevices.add(ti);
            } else if (ti.rollingBw > _MIN_TRANSFER_LEVEL) {
                _totalBw += ti.rollingBw;
                bwhogs.add(tme);
            }
        }

        logsysbw();

        // Send small, fixed-sized bandwidth allocations to paused devices.
        // We can't send a bandwidth allocation of 0 because the sender will
        // never be able to request more bandwidth (the timeout on the sender
        // will be indefinite, so no more messages will be sent). Too small,
        // and we'll be waiting days. Too large, and pausing will not have the
        // desired effect.
        for (TransmitInfo ti : pausedDevices) {
            if (l.isTraceEnabled()) {
                l.trace("{} is paused", ti.lastEndpoint.did());
            }

            try {
                sendalloc(_MIN_TRANSFER_LEVEL, ti.lastEndpoint);
            } catch (Exception e) {
                l.error("ignore e:" + e.getClass() + " did:" + ti.lastEndpoint.did()
                        + " while perf lim");
            }
        }

        if (_totalBw > _availBwHiLvl) {
            l.trace("tbw > abw[h]: perf lim");

            final long bwdiff = _totalBw - _availBwLoLvl;
            long rembwdiff = bwdiff;
            for (Map.Entry<DID, TransmitInfo> bwh : bwhogs) {
                if (rembwdiff <= 0) break;

                TransmitInfo ti = bwh.getValue();

                long reduc = 0;
                if (ti.rollingBw >= (_availBwLoLvl * 2)) {
                    reduc = ti.rollingBw - _availBwLoLvl;
                } else {
                    reduc = (long)
                            ((ti.rollingBw / (double) _totalBw) * bwdiff * _PENALTY_FACTOR);
                }

                l.trace("calc red:{} d:{}", reduc, ti.lastEndpoint.did());

                try {
                    long newbw =
                            (reduc > ti.rollingBw ? 0 : (ti.rollingBw - reduc));
                    sendalloc(newbw, ti.lastEndpoint);
                    rembwdiff -= reduc;
                } catch (Exception e) {
                    l.error("ignore e:" + e.getClass() +
                            " did:" + bwh.getKey() + " while perf lim");
                }
            }
        }
    }

    /**
     * <b>IMPORTANT:</b> I have chosen not to recalculate the bandwidth here
     * (although I could). Instead, I use my previous knowledge (i.e. the
     * bandwidth I calculated at the last interval) in order to decide how much
     * to provision for this guy. <b>IMPORTANT:</b> Always over-provision.
     * <b>IMPORTANT:</b> Only respond if you can provision bandwidth
     */
    private void respondToLimitLess(TransmitInfo ti, Endpoint ep)
    {
        logsysbw();

        if (_totalBw < _availBwLoLvl) {
            l.trace("ubw < abw[l]");

            try {
                final long newbw;
                if (ti.paused) {
                    newbw = _MIN_TRANSFER_LEVEL;
                    l.info("{} requested bw but is paused", ep.did());
                } else {
                    newbw = ti.rollingBw + (_availBwLoLvl - _totalBw);
                }
                sendalloc(newbw, ep);
            } catch (Exception e) {
                l.error("ignore e:" + e.getClass() + " d:" + ep.did() + " while lim less");
            }
        } else {
            l.trace("ubw >= abw[l]");
        }
    }

    private void scheduleRecompute(long ms)
    {
        l.trace("schd bwrc:{}", (System.currentTimeMillis() + _RECOMPUTE_INTERVAL));

        _f._sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                respondToTimeout();
                scheduleRecompute(_RECOMPUTE_INTERVAL);
            }
        }, ms);
    }

    private void processBytesIn(InputStream is, int wirelen, Endpoint ep)
            throws ExThrottling
    {
        // note that we've received this many packets from the peer

        DID d = ep.did();
        if (!_transmitMap.containsKey(d)) {
            l.trace("newti: d:{}", d);
            _transmitMap.put(d, new TransmitInfo());
        }

        TransmitInfo ti = _transmitMap.get(d);
        ti.bytesIn += wirelen;
        ti.lastEndpoint = ep;

        // check if the peer is requesting a realloc

        Limit.PBLimit pbl;
        try {
            pbl = Limit.PBLimit.parseDelimitedFrom(is);
        } catch (IOException e) {
            l.warn("bad lhdr e:" + e);
            throw new ExThrottling("PBLimit missing");
        }

        assert pbl != null;

        if (pbl.getType() == Limit.PBLimit.Type.REQUEST_BW) {
            l.trace("lhdr:REQ");

            long now = System.currentTimeMillis();
            if (ti.firstLimitLess == 0) ti.firstLimitLess = now;

            if ((now - ti.firstLimitLess) >= _LIMIT_LESS_RESPONSE_DELAY) {
                respondToLimitLess(ti, ep);
                ti.firstLimitLess = 0;
            } else {
                l.trace("wait more lhdr");
            }
        } else {
            l.trace("lhdr:ALL");

            if (pbl.getType() == Limit.PBLimit.Type.ALLOCATE) {
                _lim.processControlLimit_(d, pbl);
            }
            ti.firstLimitLess = 0;
        }
    }

    private void setBandwidth_(long bw)
    {
        if (bw == UNLIMITED_BANDWIDTH || (bw > (LimitParam._MAX_DL_BW - _MAX_BW_OVERAGE))) {
            _availBwLoLvl = LimitParam._MAX_DL_BW;
            _availBwHiLvl = LimitParam._MAX_DL_BW;
        } else if (bw >= LimitParam._MIN_DL_BW) {
            _availBwLoLvl = bw;
            _availBwHiLvl = _availBwLoLvl + _MAX_BW_OVERAGE;
        } else {
            _availBwLoLvl = LimitParam._MIN_DL_BW;
            _availBwHiLvl = _availBwLoLvl + _MAX_BW_OVERAGE;
        }
    }

    public void pauseDevice_(DID did) throws ExNotFound
    {
        if (!_transmitMap.containsKey(did)) {
            throw new ExNotFound(did.toString() + " not found in LimitMonitor. no pause");
        }

        TransmitInfo ti = _transmitMap.get(did);
        assert !ti.paused;
        ti.paused = true;
        l.info("incoming traffic for " + did + " paused");
    }

    public void resumeDevice_(DID did) throws ExNotFound
    {
        if (!_transmitMap.containsKey(did)) {
            throw new ExNotFound(did.toString() + " not found in LimitMonitor. no resume");
        }

        TransmitInfo ti = _transmitMap.get(did);
        assert ti.paused;
        ti.paused = false;
        l.info("incoming traffic for " + did + " resumed");
    }

    //
    // IUnicastInputLayer
    //

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc)
    {
        l.trace("rx: t:UC d:{} b:{}", pc.ep(), r._wirelen);

        try {
            processBytesIn(r._is, r._wirelen, pc.ep());
            // FIXME: somehow I have a feeling this is going to blow up in my face...
            if (r._is.available() != 0) {
                _upperUnicastInput.onUnicastDatagramReceived_(r, pc);
            } else {
                l.trace("is: no b");
            }
        } catch (IOException e) {
            l.error("ignoring ioe for pkt from: {} e: {}", pc.ep(), e);
        } catch (ExThrottling e) {
            l.error("ignoring ext for pkt from: {} e: {}", pc.ep(), e);
        }
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc)
    {
        readStream(streamId, r, pc.ep());
        _upperUnicastInput.onStreamBegun_(streamId, r, pc);
    }

    @Override
    public void onStreamChunkReceived_(StreamID streamId, int seq, RawMessage r, PeerContext pc)
    {
        readStream(streamId, r, pc.ep());
        _upperUnicastInput.onStreamChunkReceived_(streamId, seq, r, pc);
    }

    @Override
    public void onStreamAborted_(StreamID streamId, Endpoint ep, InvalidationReason reason)
    {
        _upperUnicastInput.onStreamAborted_(streamId, ep, reason);
    }

    //
    // ICfgReloadListener
    //

    @Override
    public void valueChanged_(Key key)
    {
        if (key != Key.MAX_DOWN_RATE) return;

        setBandwidth_(Cfg.db().getLong(Key.MAX_DOWN_RATE));
    }

    //
    // IDeviceEvictionListener
    //

    @Override
    public void evicted_(DID d)
    {
        TransmitInfo ti = _transmitMap.remove(d);
        if (ti != null) {
            l.trace("rem ti d:{}", d);
        }
    }
}
