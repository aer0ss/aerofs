package com.aerofs.daemon.core;

import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;

/**
 * A DI wrapper class for the core queue. Because the system may need multiple
 * instances of the BlockingPrioQueue class, and multitons are discouraged
 * in AeroFS Guice, we create this class to represent the single system-wide
 * core queue.
 */
public class CoreQueue extends BlockingPrioQueue<IEvent>
{
    public CoreQueue()
    {
        super(DaemonParam.QUEUE_LENGTH_CORE);
        Dumpables.add("q", this);
    }
}
