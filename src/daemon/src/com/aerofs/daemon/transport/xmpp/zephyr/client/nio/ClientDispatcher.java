/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.Scheduler;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.ex.ExNoResource;
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
