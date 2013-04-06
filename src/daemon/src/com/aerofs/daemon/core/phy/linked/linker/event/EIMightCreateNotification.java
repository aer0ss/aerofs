package com.aerofs.daemon.core.phy.linked.linker.event;

import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.lib.event.IEvent;

public class EIMightCreateNotification implements IEvent
{
    public final LinkerRoot _root;
    public final String _absPath;

    public EIMightCreateNotification(LinkerRoot root, String absPath)
    {
        _root = root;
        _absPath = absPath;
    }
}
