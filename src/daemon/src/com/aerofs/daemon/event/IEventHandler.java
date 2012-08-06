package com.aerofs.daemon.event;

import com.aerofs.daemon.lib.Prio;

public interface IEventHandler<E extends IEvent>
{
    /**
     *
     * @param ev {@link IEvent} the handler should handle
     * @param prio {@link Prio} priority of the event to be handled
     */
    void handle_(E ev, Prio prio);
}
