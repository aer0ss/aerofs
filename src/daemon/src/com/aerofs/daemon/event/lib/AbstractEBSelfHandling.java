package com.aerofs.daemon.event.lib;

import com.aerofs.daemon.event.IEvent;

/**
 * The abstract class for all self-handling events. An event requires a separate handler object to
 * be registered with the event dispatcher, before the event can be processed. However, an event
 * that inherits this class needs no such handler.
 */
public abstract class AbstractEBSelfHandling implements IEvent
{
    public abstract void handle_();

    public final Class<? extends IEvent> type()
    {
        return AbstractEBSelfHandling.class;
    }
}
