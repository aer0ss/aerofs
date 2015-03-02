package com.aerofs.daemon.event.net;

import com.aerofs.ids.SID;
import com.aerofs.lib.event.IEvent;

/**
 * This event is issued at initial launch and when stores are added or
 * removed
 */
public class EOUpdateStores implements IEvent
{
    public final SID[] _sidsAdded;
    public final SID[] _sidsRemoved;

    /**
     * @param sidsAdded must be immutable
     * @param sidsRemoved must be immutable
     */
    public EOUpdateStores(SID[] sidsAdded, SID[] sidsRemoved)
    {
        _sidsAdded = sidsAdded;
        _sidsRemoved = sidsRemoved;
    }
}
