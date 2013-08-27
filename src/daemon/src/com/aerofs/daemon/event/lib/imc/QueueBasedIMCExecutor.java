package com.aerofs.daemon.event.lib.imc;

import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

public class QueueBasedIMCExecutor implements IIMCExecutor {
    final IBlockingPrioritizedEventSink<IEvent> _q;

    public QueueBasedIMCExecutor(IBlockingPrioritizedEventSink<IEvent> q)
    {
        _q = q;
    }

    @Override
    public void done_(IEvent ev)
    {
        synchronized (ev) { ev.notify(); }
    }

    @Override
    public boolean enqueue_(IEvent ev, Prio prio)
    {
        return _q.enqueue(ev, prio);
    }

    @Override
    public void enqueueBlocking_(IEvent ev, Prio prio)
    {
        _q.enqueueBlocking(ev, prio);
    }

    @Override
    public void execute_(IEvent ev, Prio prio)
    {
        synchronized (ev) {
            // enqueuing must be done while holding the lock  otherwise we might
            // miss notify();
            _q.enqueueBlocking(ev, prio);
            ThreadUtil.waitUninterruptable(ev);
        }
    }
}
