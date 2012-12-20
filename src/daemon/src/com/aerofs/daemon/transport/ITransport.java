package com.aerofs.daemon.transport;

import com.aerofs.daemon.IModule;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.lib.IBlockingPrioritizedEventSink;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.transport.lib.IIdentifier;
import com.aerofs.base.id.DID;

import java.util.Set;

/**
 * Implemented by classes that provide a message transport mechanism
 * <br/>
 * <br/>
 * Implementers must:
 * <ul>
 *     <li>Override <code>toString()</code> with <code>getName()</code></li>
 *     <li>Implement <code>IDebug</code> methods in a
 *         <strong>thread-safe</strong> way. Implementations can block, but
 *         not too long</li>
 * </ul>
 */
public interface ITransport extends IModule, IIdentifier, IDebug
{
    /**
     * Check if this <code>ITransport</code> is ready to process incoming/outgoing
     * messages
     *
     * @return <code>true</code> if the <code>ITransport</code> is ready for IO,
     * <code>false</code> if not
     */
    public boolean ready();

    /**
     * @return true if multicast is supported, false otherwise
     */
    public boolean supportsMulticast();

    /**
     * Get a collection of {@link DID} that are not reachable via multicast
     *
     * @return <strong>immutable</strong> (i.e. content mustn't change after
     * the method returns), read-only set of online peers that are unreachable
     * via multicast. This set <em>will be</em> included in the
     * multicast-unreachable device list of the <code>EOMaxcastMessage</code> event.
     * <strong>IMPORTANT:</strong> Implementers should <em>never</em> return
     * <code>null</code>. If there are no entries, an empty set should be returned.
     */
    Set<DID> getMulticastUnreachableOnlineDevices();

    /**
     * This method is obsolete in the new transport code.
     *
     * @return queue by which you can deliver events to this module.
     */
    IBlockingPrioritizedEventSink<IEvent> q();
}
