package com.aerofs.daemon.transport.lib.presence;

import com.aerofs.ids.DID;

import java.util.Set;

/**
 * Implemented by classes that can receive Presence Location
 *
 * Currently, such events are sent by the XMPPPresenceConnection class,
 *   when a device connects to the XMPP server (and we receive its "metadata"
 *   containing the list of its locations).
 */
public interface IPresenceLocationReceiver
{
    /**
     * Process the given Presence Location
     *
     * @param locations the location
     */
    void onPresenceReceived(DID did, Set<IPresenceLocation> locations);
}
