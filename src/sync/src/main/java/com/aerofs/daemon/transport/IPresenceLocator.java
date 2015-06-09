package com.aerofs.daemon.transport;

import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import java.util.Collection;


/**
 * Implemented by classes that provide a location for the current device.
 */
public interface IPresenceLocator
{
    /**
     * Retrieve the Presence Location list of the device on this transport
     */
    Collection<IPresenceLocation> getPresenceLocations();
}
