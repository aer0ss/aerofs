package com.aerofs.daemon;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.lib.metrics.RockLogReporter;
import com.aerofs.lib.rocklog.RockLog;
import com.google.inject.Inject;

import static java.util.concurrent.TimeUnit.MINUTES;

public class Daemon implements IModule
{
    private final Core _core;

    // TODO add more submodules e.g. transports
    @Inject
    public Daemon(Core core, RockLog rockLog)
    {
        _core = core;
        RockLogReporter.enable(rockLog, 10, MINUTES);
    }

    @Override
    public void init_() throws Exception
    {
        _core.init_();
    }

    @Override
    public void start_()
    {
        _core.start_();
    }
}
