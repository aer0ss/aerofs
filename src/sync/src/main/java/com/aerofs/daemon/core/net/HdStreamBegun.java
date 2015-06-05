package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

/**
 * Handler for the {@link EIStreamBegun} event
 */
public class HdStreamBegun implements IEventHandler<EIStreamBegun>
{
    private final IUnicastInputLayer _input;

    @Inject
    public HdStreamBegun(IUnicastInputLayer input)
    {
        _input = input;
    }

    @Override
    public void handle_(EIStreamBegun ev, Prio prio)
    {
        PeerContext pc = new PeerContext(ev._ep, ev._userID);
        RawMessage r = new RawMessage(ev.is());
        _input.onStreamBegun_(ev._streamId, r, pc);
    }
}
