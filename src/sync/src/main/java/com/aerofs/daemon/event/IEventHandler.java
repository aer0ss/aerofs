package com.aerofs.daemon.event;

import com.aerofs.lib.event.IEvent;

public interface IEventHandler<E extends IEvent>
{
    /**
     *  @param ev {@link IEvent} the handler should handle
     *
     */
    void handle_(E ev);
}
