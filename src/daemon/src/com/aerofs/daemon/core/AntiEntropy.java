package com.aerofs.daemon.core;
import com.aerofs.base.Loggers;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class AntiEntropy
{
    private static final Logger l = Loggers.getLogger(AntiEntropy.class);

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
        l.debug("start " + sidx + " seq " + seq);
        _sched.schedule(_factEv.create_(sidx, seq), 0);
    }
}
