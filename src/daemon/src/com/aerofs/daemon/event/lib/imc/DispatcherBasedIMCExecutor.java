package com.aerofs.daemon.event.lib.imc;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.Prio;

public class DispatcherBasedIMCExecutor implements IIMCExecutor {
    final EventDispatcher _d;

    public DispatcherBasedIMCExecutor(EventDispatcher d)
    {
        _d = d;
    }

    @Override
    public void done_(IEvent ev)
    {
    }

    @Override
    public void enqueueThrows_(IEvent ev, Prio prio)
    {
        _d.dispatch_(ev, prio);
    }

    @Override
    public void enqueueBlocking_(IEvent ev, Prio prio)
    {
        _d.dispatch_(ev, prio);
    }

    @Override
    public void execute_(IEvent ev, Prio prio)
    {
        _d.dispatch_(ev, prio);
    }

    @Override
    public boolean enqueue_(IEvent ev, Prio prio)
    {
        _d.dispatch_(ev, prio);
        return true;
    }
}
