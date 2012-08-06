package com.aerofs.daemon.core.linker.event;

import com.aerofs.daemon.event.IEvent;

public class EIMightDeleteNotification implements IEvent
{
    public final String _absPath;

    /**
     * @param absPath the absolute path of the file
     */
    public EIMightDeleteNotification(String absPath)
    {
        _absPath = absPath;
    }
}
