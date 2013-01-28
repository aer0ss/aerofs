package com.aerofs.daemon.transport.xmpp;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EOTransportReconfigRemoteDevice;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.cfg.Cfg;

public class HdTransportReconfigRemoteDevice implements
        IEventHandler<EOTransportReconfigRemoteDevice> {

    @Override
    public void handle_(EOTransportReconfigRemoteDevice ev, Prio prio)
    {
        assert !ev._did.equals(Cfg.did());
    }
}
