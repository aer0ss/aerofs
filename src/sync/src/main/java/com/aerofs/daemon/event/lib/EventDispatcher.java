package com.aerofs.daemon.event.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Maps;

import java.util.Map;

public class EventDispatcher
{
    private final Map<Class<? extends IEvent>, IEventHandler<? extends IEvent>> _map =
            Maps.newHashMap();

    private IEventHandler<? extends IEvent> _hdDefault;

    public EventDispatcher setHandler_(Class<? extends IEvent> c,
            IEventHandler<? extends IEvent> hd)
    {
        _map.put(c, hd);
        return this;
    }

    /**
     * Register a handler to which all unmapped events will be dispatched. In the lack of a default
     * handler, dispatchin an unmapped event would result in SystemUtil.fatal().
     *
     * Normally, unmapped events indicate programming error. Therefore, you should NOT set a default
     * handler unless you know what exactly you are doing.
     */
    public EventDispatcher setDefaultHandler_(IEventHandler<? extends IEvent> hd)
    {
        _hdDefault = hd;
        return this;
    }

    @SuppressWarnings("unchecked")
    public void dispatch_(IEvent ev, Prio prio)
    {
        if (ev instanceof AbstractEBSelfHandling) {
            ((AbstractEBSelfHandling) ev).handle_();
        } else {
            IEventHandler<? extends IEvent> hd = _map.get(ev.getClass());
            if (hd != null) {
                ((IEventHandler<IEvent>) hd).handle_(ev, prio);
            } else if (_hdDefault != null) {
                ((IEventHandler<IEvent>) _hdDefault).handle_(ev, prio);
            } else {
                SystemUtil.fatal("unsupported event " + ev.getClass());
            }
        }
    }
}
