package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.IPresenceLocation;
import com.aerofs.ids.DID;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Presence locations are a structured way to represent where a given DID can be found
 * For a TCP Unicast connection, this is the list of socket addresses, ie the list of
 *  IP addresses + listening port
 *
 * Example: [1.2.3.4:34099, 10.0.0.3:34099]
 */
public class TCPPresenceLocation implements IPresenceLocation {
    private final DID _did;
    private final InetSocketAddress _socketAddress;

    public TCPPresenceLocation(DID did, InetAddress IPAddress, int listeningPort) {
        _did = did;

        // Build the list of socket addresses, from the IPs and the port
        _socketAddress = new InetSocketAddress(IPAddress, listeningPort);
    }

    public DID did() { return _did; }

    public TransportType transportType() { return TransportType.LANTCP; }

    /**
     * Serialize the location to a String
     * @return the serialized location
     */
    public String exportLocation() {
        return _socketAddress.toString();
    }
}
