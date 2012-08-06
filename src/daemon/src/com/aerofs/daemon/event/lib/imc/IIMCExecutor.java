package com.aerofs.daemon.event.lib.imc;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.ex.ExNoResource;

public interface IIMCExecutor {

    // shall go to sleep
    void execute_(IEvent ev, Prio prio);

    /**
     * @return false if the queue is full
     */
    boolean enqueue_(IEvent ev, Prio prio);

    void enqueueThrows_(IEvent ev, Prio prio) throws ExNoResource;

    void enqueueBlocking_(IEvent ev, Prio prio);

    // shall wake up the thread executing ev
    void done_(IEvent ev);
}
