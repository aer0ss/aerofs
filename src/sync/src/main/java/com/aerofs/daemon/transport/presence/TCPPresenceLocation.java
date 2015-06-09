package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.ids.DID;
import com.google.common.base.Preconditions;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Presence locations are a structured way to represent where a given DID can be found
 * For a TCP Unicast connection, this is a socket address (IP address + listening port)
 *
 * Example: 1.2.3.4:34099
 */
public class TCPPresenceLocation implements IPresenceLocation {
    private final DID _did;
    private final InetSocketAddress _socketAddress;
    public final static int VERSION = 100;

    public TCPPresenceLocation(final DID did, final InetAddress IPAddress, final int listeningPort) {
        _did = did;

        // Build the list of socket addresses, from the IPs and the port
        _socketAddress = new InetSocketAddress(IPAddress, listeningPort);
    }

    public TransportType transportType() { return TransportType.LANTCP; }

    /**
     * Serialize the location to a String
     *
     * @return the serialized location
     * Ex: "192.168.0.2:4567"
     */
    public String exportLocation() {
        Preconditions.checkState(_socketAddress.getAddress() != null);
        return _socketAddress.getAddress().getHostAddress() + ":" + _socketAddress.getPort();
    }

    public InetSocketAddress socketAddress() {
        return _socketAddress;
    }

    @Override
    public DID did() { return _did; }

    @Override
    public int version() { return VERSION; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TCPPresenceLocation that = (TCPPresenceLocation) o;

        if (_did != null ? !_did.equals(that.did()) : that.did() != null) return false;
        if (VERSION != that.version()) return false;
        return !(_socketAddress != null ? !_socketAddress.equals(that.socketAddress()) : that.socketAddress() != null);

    }

    @Override
    public int hashCode() {
        int result = _did != null ? _did.hashCode() : 0;
        result = 31 * result + (_socketAddress != null ? _socketAddress.hashCode() : 0);
        return result;
    }
}
