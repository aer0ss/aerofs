package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.IPresenceLocation;
import com.aerofs.ids.DID;
import com.google.gson.JsonObject;

import java.net.*;

public class PresenceLocationFactory
{
    public static IPresenceLocation fromJson(DID did, JsonObject jsonObject)
            throws ExInvalidPresenceLocation
    {
        // Check validity
        if (!jsonObject.has("version") || !jsonObject.has("transport")
                || !jsonObject.has("location")) {
            throw new ExInvalidPresenceLocation("Incomplete json object");
        }

        // Parse content
        int version = jsonObject.getAsJsonPrimitive("version").getAsInt();
        String location = jsonObject.getAsJsonPrimitive("location").getAsString();
        String transport = jsonObject.getAsJsonPrimitive("transport").getAsString();

        try {
            switch (TransportType.valueOf(transport)) {
                case LANTCP: return getTCPPresenceLocation(did, version, location);
                default: throw new ExInvalidPresenceLocation("Transport not implemented " + TransportType.valueOf(transport).toString());
            }
        } catch (IllegalArgumentException e) {
            // The transport is not known
            throw new ExInvalidPresenceLocation("Unknown transport type", e);
        }
    }

    public static TCPPresenceLocation getTCPPresenceLocation(DID did, int version, String location)
            throws ExInvalidPresenceLocation
    {
        // Check the version
        if (!IPresenceLocation.versionCompatible(version, TCPPresenceLocation.VERSION)) {
            throw new ExInvalidPresenceLocation("Version " + version + " is not compatible (we are "
                    + TCPPresenceLocation.VERSION + ")");
        }

        try {
            URI uri = new URI("presence://" + location + "/");
            return new TCPPresenceLocation(did, InetAddress.getByName(uri.getHost()), uri.getPort());
        } catch (URISyntaxException|IllegalArgumentException e) {
            throw new ExInvalidPresenceLocation("Malformed socket address " + location, e);
        } catch (UnknownHostException e) {
            throw new ExInvalidPresenceLocation("Unknown host in socket address " + location, e);
        }
    }
}
