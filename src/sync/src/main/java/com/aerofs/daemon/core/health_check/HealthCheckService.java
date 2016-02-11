package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.IStartable;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class HealthCheckService implements IStartable
{
    private static final Logger l = Loggers.getLogger(HealthCheckService.class);

    private static final long DEFAULT_INITIAL_DELAY = 30 * C.SEC;
    private static final long DEFAULT_INTERVAL = 5 * C.MIN;

    public interface ScheduledRunnable extends Runnable {
        default long delay()
        {
            return DEFAULT_INITIAL_DELAY;
        }

        default long interval()
        {
            return DEFAULT_INTERVAL;
        }
    }

    //
    // runner
    // prevents multiple simultaneous runs
    //

    private static class ServiceRunner implements Runnable {
        private final Runnable _serviceRunnable;
        private double _minElapsedInterval;

        private long _lastRunTime;

        private ServiceRunner(Runnable serviceRunnable, long interval)
        {
            _serviceRunnable = serviceRunnable;
            _minElapsedInterval = interval * 0.8;

            checkArgument(_minElapsedInterval > 0);
            checkArgument(_minElapsedInterval <= interval);
        }

        @Override
        public final void run()
        {
            long currentTime = System.currentTimeMillis();

            if ((currentTime - _lastRunTime) >= _minElapsedInterval) {
                _serviceRunnable.run();
                _lastRunTime = System.currentTimeMillis();
            }
        }
    }

    private final ScheduledExecutorService _healthCheckExecutor;
    private final ImmutableSet<ScheduledRunnable> _runners;

    @Inject
    public HealthCheckService(Set<ScheduledRunnable> runners)
    {
        // create our custom thread factory so that we can name threads properly
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder();
        threadFactoryBuilder.setDaemon(true);
        threadFactoryBuilder.setNameFormat("hc%d");
        threadFactoryBuilder.setPriority(Thread.NORM_PRIORITY);
        threadFactoryBuilder.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        _healthCheckExecutor = Executors.newScheduledThreadPool(3, threadFactoryBuilder.build());
        _runners = ImmutableSet.copyOf(runners);
    }

    @Override
    public void start_()
    {
        l.info("scheduling health checks");
        for (ScheduledRunnable r : _runners) {
            _healthCheckExecutor.scheduleWithFixedDelay(new ServiceRunner(r, r.interval()),
                    r.delay(), r.interval(), MILLISECONDS);
        }
    }
}
