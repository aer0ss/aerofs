package com.aerofs.daemon.transport.presence;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

/**
 * What is a Zephyr location? To establish a link on Zephyr, clients need first to agree on a
 * Zephyr server to use (currently appliances only use a single Zephyr server) and a ZID.
 *
 * For now, we are going to store the Zephyr server IP/port into the location.
 */
public class ZephyrPresenceLocation implements IPresenceLocation
{
    protected static final Logger l = Loggers.getLogger(ZephyrPresenceLocation.class);

    private final InetSocketAddress addr;

    public ZephyrPresenceLocation(final InetSocketAddress addr) {
        Preconditions.checkState(addr != null);
        this.addr = addr;
    }

    @Override
    public TransportFactory.TransportType transportType() {
        return TransportFactory.TransportType.ZEPHYR;
    }

    @Override
    public String exportLocation() {
        // The InetSocketAddress can use only a hostname or only an IP
        // If we have both we prefer the IP
        if (addr.getAddress() != null) {
            return addr.getAddress().getHostAddress() + ":" + addr.getPort();
        }

        if (addr.getHostName() != null) {
            return addr.getHostName() + ":" + addr.getPort();
        }

        l.warn("Unable to export Zephyr address: ", addr);
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return addr.equals(((ZephyrPresenceLocation) o).addr);
    }

    @Override
    public int hashCode() {
        return addr.hashCode();
    }

    @Override
    public String toString() { return exportLocation(); }
}
