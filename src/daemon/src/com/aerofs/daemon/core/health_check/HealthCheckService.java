package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.IStartable;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

//
// FIXME (AG): invert how HealthCheckService is created
//
// the right thing to do is pass HealthCheckService to each one of the service
// classes, which then register themselves with it, with the appropriate timeout
//
public final class HealthCheckService implements IStartable
{
    private static final long DEFAULT_INITIAL_DELAY = 3 * C.SEC;
    private static final long DEFAULT_INTERVAL = 3 * C.MIN;

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
        private static final long INTERVAL = DEFAULT_INTERVAL;
    }

    //
    // constants for the transport diagnostics dumper
    //

    private static final class TDDConstants
    {
        private static final long INITIAL_DELAY = DEFAULT_INITIAL_DELAY;
        private static final long INTERVAL = DEFAULT_INTERVAL;
    }

    //
    // members
    //

    private static final Logger l = Loggers.getLogger(HealthCheckService.class);

    private final ScheduledExecutorService _healthCheckExecutor = Executors.newScheduledThreadPool(3); // we could even run one, but...

    private final CoreProgressWatcher _coreProgressWatcher;
    private final DeadlockDetector _deadlockDetector;
    private final TransportDiagnosticsDumper _transportDiagnosticsDumper;

    @Inject
    public HealthCheckService(CoreProgressWatcher coreProgressWatcher, DeadlockDetector deadlockDetector, TransportDiagnosticsDumper transportDiagnosticsDumper)
    {
        _coreProgressWatcher = coreProgressWatcher;
        _deadlockDetector = deadlockDetector;
        _transportDiagnosticsDumper = transportDiagnosticsDumper;
    }

    @Override
    public void start_()
    {
        l.info("scheduling health checks");

        _healthCheckExecutor.scheduleAtFixedRate(_coreProgressWatcher, CPWConstants.INITIAL_DELAY, CPWConstants.INTERVAL, MILLISECONDS);
        _healthCheckExecutor.scheduleAtFixedRate(_deadlockDetector, DLDConstants.INITIAL_DELAY, DLDConstants.INTERVAL, MILLISECONDS);
        _healthCheckExecutor.scheduleAtFixedRate(_transportDiagnosticsDumper, TDDConstants.INITIAL_DELAY, TDDConstants.INTERVAL, MILLISECONDS);
    }
}
