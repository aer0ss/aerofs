package com.aerofs.daemon.lib;

import com.aerofs.daemon.transport.lib.IPresenceLocation;

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
     * @param presenceLocation the location
     */
    void onPresenceReceived(IPresenceLocation presenceLocation);
}
