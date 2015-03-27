package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

/**
 * Handler for a {@link EIMaxcastMessage}
 */
public class HdMaxcastMessage implements IEventHandler<EIMaxcastMessage>
{
    private final CoreProtocolReactor _reactor;

    @Inject
    public HdMaxcastMessage(CoreProtocolReactor reactor)
    {
        _reactor = reactor;
    }

    @Override
    public void handle_(EIMaxcastMessage ev, Prio prio)
    {
        _reactor.onMaxcastMessageReceived_(ev._ep, ev.is());
    }
}
