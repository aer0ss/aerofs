package com.aerofs.daemon.core.phy.linked.linker.event;

import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.lib.event.IEvent;

import static com.google.common.base.Preconditions.checkNotNull;

public class EIMightCreateNotification implements IEvent
{
    public final LinkerRoot _root;
    public final String _absPath;
    public final RescanSubtree _rescan;

    public enum RescanSubtree
    {
        DEFAULT,
        FORCE
    }

    public EIMightCreateNotification(LinkerRoot root, String absPath, RescanSubtree rescan)
    {
        _root = checkNotNull(root);
        _absPath = checkNotNull(absPath);
        _rescan = rescan;
    }
}
