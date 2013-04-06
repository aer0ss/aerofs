package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.linked.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.lib.event.Prio;

class HdMightDeleteNotification implements IEventHandler<EIMightDeleteNotification>
{
    private boolean _disabled;

    @Override
    public void handle_(EIMightDeleteNotification ev, Prio prio)
    {
        if (!_disabled) ev._root.mightDelete_(ev._absPath);
    }

    public void setDisabled(boolean disabled)
    {
        _disabled = disabled;
    }
}
