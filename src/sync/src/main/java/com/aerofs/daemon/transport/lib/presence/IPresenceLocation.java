package com.aerofs.daemon.transport.lib.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.ids.DID;
import com.google.gson.JsonObject;


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
     * Serialize the location to a String
     * @return the serialized location
     */
    String exportLocation();

    /**
     * Returns the version of the data formatting
     * When data used to describe the location is updated, the version should increase.
     *
     * 3 digits versions, beginning at 100.
     *  minor updates preserving backward compatibility (adding a field) increase unites (101, 102...)
     *  major updates non backward-compatible should increase hundreds (102 -> 200)
     *
     * @return the version
     */
    int version();

    /**
     * Returns a Json Object containing the location, the transport, and the format version
     * @return the Json Object representing the location
     */
    default JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", version());
        obj.addProperty("transport", transportType().toString());
        obj.addProperty("location", exportLocation());
        return obj;
    }

    /**
     * Decide whether the incoming version is compatible with our system or not.
     *
     * @param incomingVersion the version of the presence to be processed
     * @param currentVersion the current version of the parser
     * @return true if compatible
     */
    static boolean versionCompatible(int incomingVersion, int currentVersion)
    {
        return (incomingVersion/100 == currentVersion/100)
                && incomingVersion >= currentVersion;
    }
}
