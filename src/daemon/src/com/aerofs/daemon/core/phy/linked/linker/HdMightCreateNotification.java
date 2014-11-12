package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.lib.event.Prio;

class HdMightCreateNotification implements IEventHandler<EIMightCreateNotification>
{
    private boolean _disabled;

    @Override
    public void handle_(EIMightCreateNotification ev, Prio prio)
    {
        if (!_disabled) ev._root.mightCreate_(ev._absPath, ev._rescan);
    }

    public void setDisabled(boolean disabled)
    {
        _disabled = disabled;
    }
}
