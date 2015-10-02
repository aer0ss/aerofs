package com.aerofs.daemon.transport.presence;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.ids.DID;
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

    private final DID did;
    private final InetSocketAddress zephyrSocketAddress;

    public ZephyrPresenceLocation(final DID did, final InetSocketAddress zephyrSocketAddress) {
        Preconditions.checkState(zephyrSocketAddress != null);

        this.did = did;
        this.zephyrSocketAddress = zephyrSocketAddress;
    }

    @Override
    public TransportFactory.TransportType transportType() {
        return TransportFactory.TransportType.ZEPHYR;
    }

    @Override
    public String exportLocation() {
        // The InetSocketAddress can use only a hostname or only an IP
        // If we have both we prefer the IP
        if (zephyrSocketAddress.getAddress() != null) {
            return zephyrSocketAddress.getAddress().getHostAddress() + ":" + zephyrSocketAddress.getPort();
        }

        if (zephyrSocketAddress.getHostName() != null) {
            return zephyrSocketAddress.getHostName() + ":" + zephyrSocketAddress.getPort();
        }

        l.warn("Unable to export Zephyr address: ", zephyrSocketAddress);
        return null;
    }

    @Override
    public DID did() { return did; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ZephyrPresenceLocation that = (ZephyrPresenceLocation) o;

        if (did != null ? !did.equals(that.did) : that.did != null) return false;
        return !(zephyrSocketAddress != null ? !zephyrSocketAddress.equals(that.zephyrSocketAddress) : that.zephyrSocketAddress != null);

    }

    @Override
    public int hashCode() {
        int result = did != null ? did.hashCode() : 0;
        result = 31 * result + (zephyrSocketAddress != null ? zephyrSocketAddress.hashCode() : 0);
        return result;
    }
}
