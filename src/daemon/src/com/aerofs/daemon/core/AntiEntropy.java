package com.aerofs.daemon.core;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class AntiEntropy
{
    private final CoreScheduler _sched;
    private final EIAntiEntropy.Factory _factEv;

    @Inject
    public AntiEntropy(CoreScheduler sched, EIAntiEntropy.Factory factEv)
    {
        _sched = sched;
        _factEv = factEv;
    }

    public void start(SIndex sidx, int seq)
    {
        Util.l(this).info("start " + sidx + " seq " + seq);
        _sched.schedule(_factEv.create_(sidx, seq), 0);
    }
}
