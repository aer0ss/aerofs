package com.aerofs.daemon.core;

import com.aerofs.daemon.lib.ExponentialRetry;
import com.google.inject.Inject;

/**
 * The core's dependency-injection wrapper for the ExponentialRetry class
 */
public class CoreExponentialRetry extends ExponentialRetry
{
    @Inject
    public CoreExponentialRetry(CoreScheduler sched)
    {
        super(sched);
    }
}