package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.lib.id.DID;

// this event may be fired even though the configure hasn't been changed.
// it's the receiver's responsibility to determine if actual changes are made.
//
// the device id may equal the local device id. in this case, local transport
// has to be reconfigured and restarted.
//
public class EOTransportReconfigRemoteDevice implements IEvent {

    public final DID _did;
    public final String _tcpEndpoint;

    public EOTransportReconfigRemoteDevice(String tcpEndpoint, DID did)
    {
        _tcpEndpoint = tcpEndpoint;
        _did = did;
    }
}
