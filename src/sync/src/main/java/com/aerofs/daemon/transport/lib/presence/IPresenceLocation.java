package com.aerofs.daemon.transport.lib.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;


/**
 * Presence locations are a structured way to represent where a given DID can be found
 *
 * They are exchanged over SSMP
 */
public interface IPresenceLocation
{
    /**
     * The Presence Location should be associated with a transport type
     * @return the TransportType
     */
    TransportType transportType();

    /**
     * Serialize the location to a String
     * @return the serialized location
     */
    String exportLocation();
}
