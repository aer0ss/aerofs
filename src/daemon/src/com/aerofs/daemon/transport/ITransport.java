package com.aerofs.daemon.transport;

import com.aerofs.daemon.IModule;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.transport.lib.IIdentifier;
import com.aerofs.lib.ITransferStat;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;

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
public interface ITransport extends IModule, IIdentifier, IDebug, ITransferStat
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
     * This method is obsolete in the new transport code.
     *
     * @return queue by which you can deliver events to this module.
     */
    IBlockingPrioritizedEventSink<IEvent> q();
}
