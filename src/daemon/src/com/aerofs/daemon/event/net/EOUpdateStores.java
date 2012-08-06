package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.SID;

/**
 * This event is issued at initial launch and when stores are added or
 * removed
 */
public class EOUpdateStores extends AbstractEBIMC
{
    public final SID[] _sidAdded;
    public final SID[] _sidRemoved;

    /**
     * @param sidsAdded must be immutable
     * @param sidsRemoved must be immutable
     */
    public EOUpdateStores(IIMCExecutor imce, SID[] sidAdded,
            SID[] sidRemoved)
    {
        super(imce);
        _sidAdded = sidAdded;
        _sidRemoved = sidRemoved;
    }
}
