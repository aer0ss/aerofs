package com.aerofs.daemon.core;

import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.net.proto.GetVersCall;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.net.NetworkInterface;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public class EIAntiEntropy extends AbstractEBSelfHandling
{
    private static final Logger l = Util.l(EIAntiEntropy.class);

    private final Factory _f;
    private final SIndex _sidx;
    private final int _seq;

    private final ILinkStateListener _l = new ILinkStateListener()
    {
        @Override
        public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
                ImmutableSet<NetworkInterface> removed,
                final ImmutableSet<NetworkInterface> current,
                final ImmutableSet<NetworkInterface> previous)
        {
            if (previous.isEmpty() && !current.isEmpty()) performAntiEntropy_();
        }
    };

    public static class Factory
    {
        private final CoreScheduler _sched;
        private final LinkStateService _lss;
        private final GetVersCall _pgvc;
        private final To.Factory _factTo;
        private final MapSIndex2Store _sidx2s;
        private final TokenManager _tokenManager;

        @Inject
        public Factory(GetVersCall pgvc, LinkStateService lss, MapSIndex2Store sidx2s,
                CoreScheduler sched, To.Factory factTo, TokenManager tokenManager)
        {
            _pgvc = pgvc;
            _lss = lss;
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

        // IMPORTANT: we know that in our implementation, listeners are called on the core thread
        _f._lss.addListener_(_l, sameThreadExecutor());
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
        boolean ret = performAntiEntropyImpl_();
        if (!ret) _f._lss.removeListener_(_l);
        return ret;
    }

    private boolean performAntiEntropyImpl_()
    {
        Store s = _f._sidx2s.getNullable_(_sidx);

        if (s == null) {
            l.info(_sidx + " no longer exists. return");
            return false;

        } else if (_seq != s.getAntiEntropySeq_()) {
            l.info(s + ": seq mismatch " + _seq + " v " + s.getAntiEntropySeq_() + ". return");
            return false;

        } else if (!s.hasOnlinePotentialMemberDevices_()) {
            l.info(s + ": no online devs. return");
            return false;

        } else if (!_f._lss.isUp_()) {
            l.info(s + ": link is down. skip");

        } else {
            To to = _f._factTo.create_(_sidx, To.RANDCAST);
            try {
                // Normally the rpc is passed a To object, and the DID chosen
                // in the NSL layer. However, GetVersCall needs to know the
                // DID from which it is pulling
                DID didTo = to.pick_();
                assert didTo != null;
                Token tk = _f._tokenManager.acquireThrows_(Cat.HOUSEKEEPING, "antiEntropy");
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
}
