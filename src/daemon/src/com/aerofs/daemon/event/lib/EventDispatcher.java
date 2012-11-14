package com.aerofs.daemon.event.lib;

import java.util.HashMap;
import java.util.Map;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.SystemUtil;

public class EventDispatcher {

    // because this hash map is a performance critical data structure, we
    // use a big capacity here
    private final Map<Class<? extends IEvent>, IEventHandler<? extends IEvent>> _map =
        new HashMap<Class<? extends IEvent>, IEventHandler<? extends IEvent>>(128);

    public EventDispatcher setHandler_(Class<? extends IEvent> c,
            IEventHandler<? extends IEvent> hd)
    {
//        // must comply with naming convention
//        // class names might be obfuscated in production
//        assert _getSimpleName.startsWith("IEB") ||
//                (TC.isCoreThread() ? _getSimpleName.startsWith("IEI") :
//                    _getSimpleName.startsWith("IEO"));

        _map.put(c, hd);
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
            } else {
                SystemUtil.fatal("unsupported event " + ev.getClass());
            }
        }
    }
}
