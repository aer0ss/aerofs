package com.aerofs.daemon.event.lib;

import com.aerofs.lib.event.AbstractEBSelfHandling;

public class EBRunnable extends AbstractEBSelfHandling
{
    private final Runnable _runnable;

    public EBRunnable(Runnable r)
    {
        _runnable = r;
    }

    @Override
    public void handle_()
    {
        _runnable.run();
    }

    @Override
    public String toString()
    {
        return _runnable.toString();
    }
}
