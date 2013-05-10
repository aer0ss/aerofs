package com.aerofs.daemon.core.net.throttling;

import com.aerofs.base.Loggers;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.Scheduler;

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
        super(sched, Loggers.getLogger(PerDeviceLimiter.class),
            minBucket, bucket, fillRate, pendingSize);

        l = Loggers.getLogger(PerDeviceLimiter.class);
        _nextLimiter = nextLevel;
        _name = name;

        l.trace("construct {}: ", _name, this);
    }

    /**
     * Tokens are only needed if per-device queues have items in them
     */
    @Override
    protected void indicateTokensNeeded_(Outgoing o)
    {
        l.trace("{}: nd tok", name());
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
            l.trace("{}: o route lower:{}", name(), _nextLimiter.name());
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
