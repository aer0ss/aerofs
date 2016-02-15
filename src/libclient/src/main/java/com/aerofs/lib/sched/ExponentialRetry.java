package com.aerofs.lib.sched;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

public class ExponentialRetry
{
    private static final Logger l = Loggers.getLogger(ExponentialRetry.class);

    public static class ExRetryLater extends Exception
    {
        private static final long serialVersionUID = 0L;

        public ExRetryLater(String msg) { super(msg); }
    }

    private final IScheduler _sched;

    public ExponentialRetry(IScheduler sched)
    {
        _sched = sched;
    }

    public void retry(String name, Callable<Void> call, Class<?>... excludes)
    {
        retry(name, LibParam.EXP_RETRY_MIN_DEFAULT, LibParam.EXP_RETRY_MAX_DEFAULT, call, excludes);
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
                        l.warn("{} failed. exp-retry in {}:", name, itv, suppress(e, excludes));
                        _sched.schedule(this, itv.get());
                    }
                }
            };
            l.warn("retry {} in {}:", name, itv, suppress(e, excludes));
            _sched.schedule(ev, itv.get());
        }
    }

    private static Throwable suppress(Throwable e, Class<?>... c)
    {
        return e instanceof ExRetryLater ? BaseLogUtil.suppress(e) : BaseLogUtil.suppress(e, c);
    }
}
