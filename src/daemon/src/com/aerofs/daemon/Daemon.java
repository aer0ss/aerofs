package com.aerofs.daemon;

import com.aerofs.daemon.core.Core;
import com.google.inject.Inject;

public class Daemon implements IModule
{
    private final Core _core;

    // TODO add more submodules e.g. transports
    @Inject
    public Daemon(Core core)
    {
        _core = core;
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
