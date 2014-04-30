package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.IStartable;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

//
// FIXME (AG): invert how HealthCheckService is created
//
// the right thing to do is pass HealthCheckService to each one of the service
// classes, which then register themselves with it, with the appropriate timeout
//
public final class HealthCheckService implements IStartable
{
    private static final long DEFAULT_INITIAL_DELAY = 30 * C.SEC;
    private static final long DEFAULT_INTERVAL = 30 * C.MIN;

    //
    // constants for the core progress watcher
    //

    private static final class CPWConstants
    {
        private static final long INITIAL_DELAY = DEFAULT_INITIAL_DELAY;
        private static final long INTERVAL = (long) (1.5 * DEFAULT_INTERVAL);
    }

    //
    // constants for the deadlock detector
    //

    private static final class DLDConstants
    {
        private static final long INITIAL_DELAY = DEFAULT_INITIAL_DELAY;
        private static final long INTERVAL = 10 * C.MIN;
    }

    //
    // constants for the diagnostics dumper
    //

    private static final class DDConstants
    {
        private static final long INITIAL_DELAY = DEFAULT_INITIAL_DELAY;
        private static final long INTERVAL = 10 * C.MIN;
    }

    //
    // runner
    // prevents multiple simultaneous runs
    //

    private class ServiceRunner implements Runnable
    {
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
        public void run()
        {
            long currentTime = System.currentTimeMillis();

            if ((currentTime - _lastRunTime) >= _minElapsedInterval) {
                _serviceRunnable.run();
                _lastRunTime = System.currentTimeMillis();
            }
        }
    }

    //
    // members
    //

    private static final Logger l = Loggers.getLogger(HealthCheckService.class);

    private final ScheduledExecutorService _healthCheckExecutor = Executors.newScheduledThreadPool(3); // we could even run one, but...

    private final CoreProgressWatcher _coreProgressWatcher;
    private final DeadlockDetector _deadlockDetector;
    private final DiagnosticsDumper _diagnosticsDumper;

    @Inject
    public HealthCheckService(CoreProgressWatcher coreProgressWatcher, DeadlockDetector deadlockDetector, DiagnosticsDumper diagnosticsDumper)
    {
        _coreProgressWatcher = coreProgressWatcher;
        _deadlockDetector = deadlockDetector;
        _diagnosticsDumper = diagnosticsDumper;
    }

    @Override
    public void start_()
    {
        l.info("scheduling health checks");

        _healthCheckExecutor.scheduleAtFixedRate(new ServiceRunner(_coreProgressWatcher, CPWConstants.INTERVAL), CPWConstants.INITIAL_DELAY, CPWConstants.INTERVAL, MILLISECONDS);
        _healthCheckExecutor.scheduleAtFixedRate(new ServiceRunner(_deadlockDetector, DLDConstants.INTERVAL), DLDConstants.INITIAL_DELAY, DLDConstants.INTERVAL, MILLISECONDS);
        _healthCheckExecutor.scheduleAtFixedRate(new ServiceRunner(_diagnosticsDumper, DDConstants.INTERVAL), DDConstants.INITIAL_DELAY, DDConstants.INTERVAL, MILLISECONDS);
    }
}
