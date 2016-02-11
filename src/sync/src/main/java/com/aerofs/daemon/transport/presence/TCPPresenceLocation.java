package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.google.common.base.Preconditions;

import java.net.*;

/**
 * Presence locations are a structured way to represent where a given DID can be found
 * For a TCP Unicast connection, this is a socket address (IP address + listening port)
 *
 * Example: 1.2.3.4:34099
 */
public class TCPPresenceLocation implements IPresenceLocation {
    private final InetSocketAddress _addr;

    public TCPPresenceLocation(final InetAddress IPAddress, final int listeningPort) {
        // Build the list of socket addresses, from the IPs and the port
        _addr = new InetSocketAddress(IPAddress, listeningPort);
    }

    public static TCPPresenceLocation fromExportedLocation(String location)
            throws ExInvalidPresenceLocation
    {
        try {
            URI uri = new URI("presence://" + location + "/");
            return new TCPPresenceLocation(InetAddress.getByName(uri.getHost()), uri.getPort());
        } catch (URISyntaxException |IllegalArgumentException e) {
            throw new ExInvalidPresenceLocation("Malformed socket address " + location, e);
        } catch (UnknownHostException e) {
            throw new ExInvalidPresenceLocation("Unknown host in socket address " + location, e);
        }
    }

    public TransportType transportType() { return TransportType.LANTCP; }

    /**
     * Serialize the location to a String
     *
     * @return the serialized location
     * Ex: "192.168.0.2:4567"
     */
    public String exportLocation() {
        Preconditions.checkState(_addr.getAddress() != null);
        InetAddress a = _addr.getAddress();
        return (a instanceof Inet6Address ? "[" + a.getHostAddress() + "]" : a.getHostAddress())
                + ":" + _addr.getPort();
    }

    public InetSocketAddress socketAddress() {
        return _addr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return _addr.equals(((TCPPresenceLocation) o).socketAddress());
    }

    @Override
    public int hashCode() {
        return _addr.hashCode();
    }

    @Override
    public String toString() { return exportLocation(); }
}
