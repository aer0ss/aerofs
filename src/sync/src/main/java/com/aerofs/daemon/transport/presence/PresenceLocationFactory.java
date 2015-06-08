package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.IPresenceLocation;
import com.aerofs.ids.DID;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.net.*;

public class PresenceLocationFactory
{
    @Nullable
    static IPresenceLocation fromJson(DID did, JsonObject jsonObject)
            throws URISyntaxException, UnknownHostException
    {
        // Check validity
        if (!jsonObject.has("version") || !jsonObject.has("transport")
                || !jsonObject.has("location")) {
            return null;
        }

        // Parse content
        int version = jsonObject.getAsJsonPrimitive("version").getAsInt();
        String location = jsonObject.getAsJsonPrimitive("location").getAsString();
        String transport = jsonObject.getAsJsonPrimitive("transport").getAsString();

        try {
            switch (TransportType.valueOf(transport)) {
                case LANTCP: return getTCPPresenceLocation(did, version, location);
                case ZEPHYR: return getTCPPresenceLocation(did, version, location);
                default: return null;
            }
        } catch (IllegalArgumentException e) {
            // The transport is not known
            return null;
        }
    }

    @Nullable
    public static TCPPresenceLocation getTCPPresenceLocation(DID did, int version, String location)
            throws URISyntaxException, UnknownHostException
    {
        // Check the version
        if (!IPresenceLocation.versionCompatible(version, TCPPresenceLocation.VERSION)) {
            return null;
        }

        URI uri = new URI("presence://"+location+"/");
        return new TCPPresenceLocation(did, InetAddress.getByName(uri.getHost()), uri.getPort());
    }
}
