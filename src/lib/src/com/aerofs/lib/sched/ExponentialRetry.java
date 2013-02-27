package com.aerofs.lib.sched;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

public class ExponentialRetry
{
    private static final Logger l = Util.l(ExponentialRetry.class);

    private final IScheduler _sched;

    public ExponentialRetry(IScheduler sched)
    {
        _sched = sched;
    }

    public void retry(String name, Callable<Void> call, Class<?>... excludes)
    {
        retry(name, Param.EXP_RETRY_MIN_DEFAULT, Param.EXP_RETRY_MAX_DEFAULT, call, excludes);
    }

    public void retry(final String name,
            long intervalMin,
            final long intervalMax,
            final Callable<Void> call,
            final Class<?>... excludes)
    {
        final OutArg<Long> itv = new OutArg<Long>(intervalMin);

        try {
            call.call();

        } catch (RuntimeException e) {
            // we tolerate no runtime exceptions
            throw e;

        } catch (Exception e) {
            AbstractEBSelfHandling ev = new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    try {
                        call.call();
                    } catch (Exception e) {
                        itv.set(Math.min(itv.get() * 2, intervalMax));
                        l.warn(name + " failed. exp-retry in " + itv + ": " + Util.e(e, excludes));
                        _sched.schedule(this, itv.get());
                    }
                }
            };
            l.warn("retry " + name + " in " + itv + ": " + Util.e(e, excludes));
            _sched.schedule(ev, itv.get());
        }
    }
}
