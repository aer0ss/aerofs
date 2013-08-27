package com.aerofs.daemon.event.lib.imc;

import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

public interface IIMCExecutor
{
    /**
     * Executes the event. The caller is blocked until
     * the event has completed execution.
     * <p/>
     * @param ev {@link IEvent} to be executed
     * @param prio {@link Prio} priority of the event
     */
    void execute_(IEvent ev, Prio prio);

    /**
     * Enqueues an event for execution (non-blocking)
     * @param ev {@link IEvent} event to be enqueued for future execution
     * @param prio {@link Prio} priority with which the event should be enqueued as
     *             Higher priority items will be dequeued first
     * @return false if the queue is full
     */
    boolean enqueue_(IEvent ev, Prio prio);

    /**
     * Enqueues an event for execution (blocking)
     * @param ev {@link IEvent} event to be enqueued for future execution
     * @param prio {@link Prio} priority with which the event should be enqueued as
     *             Higher priority items will be dequeued first
     */
    void enqueueBlocking_(IEvent ev, Prio prio);

    /**
     * Wakes up the thread waiting on {@code ev} to complete
     * @param ev {@link IEvent} that completed execution
     */
    void done_(IEvent ev);
}
