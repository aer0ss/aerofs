/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio;

import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.zephyr.core.Dispatcher;

import static com.aerofs.daemon.lib.DaemonParam.QUEUE_LENGTH_DEFAULT;

public class ClientDispatcher extends Dispatcher
{
    public void enqueue(AbstractEBSelfHandling ev, Prio pri)
    {
        try {
            _eq.enqueueThrows(ev, pri);
        } catch (ExNoResource e) {
            l.warn("zd: fail enq ev - resched for immediate ex");
            _sc.schedule(ev, 0);
        } finally {
            _sel.wakeup();
        }
    }

    protected final void processNonNioEvents()
    {
        OutArg<Prio> pri = new OutArg<Prio>(Prio.LO);
        while (true) {
            try {
                AbstractEBSelfHandling ev = (AbstractEBSelfHandling) _eq.tryDequeue(pri);
                if (ev == null) break;
                ev.handle_();
            } catch (ClassCastException e) {
                assert false : ("zd: dequeued non-ebsha");
            }
        }
    }

    private final BlockingPrioQueue<IEvent> _eq = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH_DEFAULT);
    private final Scheduler _sc = new Scheduler(_eq, "zdisp");
}
