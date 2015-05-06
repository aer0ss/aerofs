package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.ids.DID;


/**
 * Presence locations are a structured way to represent where a given DID can be found
 *
 * They are used by the XMPP Presence Service to allow the clients to exchange their locations
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
     * Serialize the location to a String (JSON formatted)
     * @return the serialized location
     */
    String exportLocation();
}
