package com.aerofs.daemon.core.linker.event;

import com.aerofs.lib.event.IEvent;

public class EIMightCreateNotification implements IEvent
{
    public final String _absPath;

    /**
     * @param absPath the absolute path of the file
     */
    public EIMightCreateNotification(String absPath)
    {
        _absPath = absPath;
    }
}
