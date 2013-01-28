package com.aerofs.daemon.core.net.throttling;

import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.lib.Util;
import javax.annotation.Nonnull;

class PerDeviceLimiter extends AbstractLimiter
{
    @Nonnull
    private ILimiter _nextLimiter;

    @Nonnull
    private final String _name;

    public PerDeviceLimiter(Scheduler sched, ILimiter nextLevel, String name,
            long minBucket, long bucket, long fillRate, int pendingSize)
    {
        super(sched, Util.l(PerDeviceLimiter.class),
            minBucket, bucket, fillRate, pendingSize);

        l = Util.l(PerDeviceLimiter.class);
        _nextLimiter = nextLevel;
        _name = name;

        l.trace("construct " + _name + ": " + this);
    }

    /**
     * Tokens are only needed if per-device queues have items in them
     */
    @Override
    protected void indicateTokensNeeded_(Outgoing o)
    {
        l.trace(name() + ": nd tok");
        o.setTokensNeeded();
    }

    //
    // ILimiter
    //

    @Override
    public void processConfirmedOutgoing_(Outgoing o, Prio p)
        throws Exception
    {
        if (l.isDebugEnabled()) {
            l.trace(name() + ": o route lower:" + _nextLimiter.name());
            l.trace(printstat_());
        }

        _nextLimiter.processOutgoing_(o, p);
    }

    @Override
    public String name()
    {
        return _name;
    }
}
