package com.aerofs.daemon.event.net;

import com.aerofs.lib.event.IEvent;

public class EITransportMetricsUpdated implements IEvent {

    public final int _maxcastSize;

    public EITransportMetricsUpdated(int size)
    {
        _maxcastSize = size;
    }
}
