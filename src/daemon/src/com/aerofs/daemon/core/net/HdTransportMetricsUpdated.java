package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EITransportMetricsUpdated;
import com.aerofs.daemon.lib.Prio;
import com.google.inject.Inject;

public class HdTransportMetricsUpdated implements IEventHandler<EITransportMetricsUpdated>
{
    private final Metrics _m;

    @Inject
    public HdTransportMetricsUpdated(Metrics m)
    {
        _m = m;
    }

    @Override
    public void handle_(EITransportMetricsUpdated ev, Prio prio)
    {
        _m.setRecommendedMaxcastSize_(ev._maxcastSize);
    }

}
