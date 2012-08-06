package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.IEvent;

public class EITransportMetricsUpdated implements IEvent {

    public final int _maxcastSize;

    public EITransportMetricsUpdated(int size)
    {
        _maxcastSize = size;
    }
}
