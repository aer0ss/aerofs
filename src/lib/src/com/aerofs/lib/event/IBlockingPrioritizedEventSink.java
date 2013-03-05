/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.event;

import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.base.ex.ExNoResource;

public interface IBlockingPrioritizedEventSink<T> extends IDumpStatMisc
{
    /**
     * *WARNING*: to avoid dead locking, enqueueBlocking
     * mustn't be used if the
     * calling thread consumes the events from the module which consumes this
     * event queue, and the latter module may block entirely due to 1)
     * calling enqueueBlocking on the caller's queue, or, 2) enqueuing IMC
     * events into the caller's queue.
     */
    void enqueueBlocking(T ev, Prio prio);

    void enqueueThrows(T ev, Prio prio) throws ExNoResource;

    /**
     * @return false if the queue is full
     */
    boolean enqueue(T ev, Prio prio);
}
