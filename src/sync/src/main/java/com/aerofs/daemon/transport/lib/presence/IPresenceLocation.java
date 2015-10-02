package com.aerofs.daemon.transport.lib.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.ids.DID;


/**
 * Presence locations are a structured way to represent where a given DID can be found
 *
 * They are broadcasted over SSMP
 */
public interface IPresenceLocation
{
    /**
     * The Presence Location should be associated with a DID
     * @return the DID
     */
    DID did();

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
