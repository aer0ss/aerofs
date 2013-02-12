package com.aerofs.daemon.core;

import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.GetVersCall;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.Util;
import com.aerofs.base.id.DID;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.*;

public class EIAntiEntropy extends AbstractEBSelfHandling
{
    private static final Logger l = Util.l(EIAntiEntropy.class);
    private static final Meter _hkFullMeter = Metrics.newMeter(
            new MetricName("vers", "ae", "hkfull"), "ae requests", TimeUnit.MINUTES);


    private final Factory _f;
    private final SIndex _sidx;
    private final int _seq;

    public static class Factory
    {
        private final CoreScheduler _sched;
        private final GetVersCall _pgvc;
        private final To.Factory _factTo;
        private final MapSIndex2Store _sidx2s;
        private final TokenManager _tokenManager;

        @Inject
        public Factory(GetVersCall pgvc, MapSIndex2Store sidx2s,
                CoreScheduler sched, To.Factory factTo, TokenManager tokenManager)
        {
            _pgvc = pgvc;
            _sched = sched;
            _factTo = factTo;
            _sidx2s = sidx2s;
            _tokenManager = tokenManager;
        }

        public EIAntiEntropy create_(SIndex sidx, int seq)
        {
            return new EIAntiEntropy(this, sidx, seq);
        }
    }

    private EIAntiEntropy(Factory f, SIndex sidx, int seq)
    {
        _f = f;
        _sidx = sidx;
        _seq = seq;
    }

    @Override
    public void handle_()
    {
        if (performAntiEntropy_()) _f._sched.schedule(this, DaemonParam.ANTI_ENTROPY_INTERVAL);
    }

    /**
     * @return whether to continue this anti-entropy instance in the future
     */
    private boolean performAntiEntropy_()
    {
        Store s = _f._sidx2s.getNullable_(_sidx);

        if (s == null) {
            l.debug(_sidx + " no longer exists. return");
            return false;

        } else if (_seq != s.getAntiEntropySeq_()) {
            l.debug(s + ": seq mismatch " + _seq + " v " + s.getAntiEntropySeq_() + ". return");
            return false;

        } else if (!s.hasOnlinePotentialMemberDevices_()) {
            l.debug(s + ": no online devs. return");
            return false;

        } else {
            To to = _f._factTo.create_(_sidx, To.RANDCAST);
            try {
                // Normally the rpc is passed a To object, and the DID chosen
                // in the NSL layer. However, GetVersCall needs to know the
                // DID from which it is pulling
                DID didTo = to.pick_();
                checkNotNull(didTo);

                Token tk = acquireToken_();
                try {
                    _f._pgvc.rpc_(_sidx, didTo, tk);
                } finally {
                    tk.reclaim_();
                }
            } catch (Exception e) {
                l.warn(s + ": " + Util.e(e));
            }
        }

        return true;
    }

    private Token acquireToken_()
            throws ExNoResource
    {
        try {
            return _f._tokenManager.acquireThrows_(Cat.HOUSEKEEPING, "AE");
        } catch (ExNoResource e) {
            _hkFullMeter.mark();
            throw e;
        }
    }
}
