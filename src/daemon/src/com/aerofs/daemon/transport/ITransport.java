package com.aerofs.daemon.transport;

import com.aerofs.daemon.IModule;
import com.aerofs.daemon.lib.IDebug;
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
public interface ITransport extends IModule, IDebug, ITransferStat
{
    /**
     * Identifier
     *
     * @return <em>constant</em> identifier for the transport. This should
     * probably be unique, but it is the transport's responsibility to
     * guarantee this.
     */
    public String id();

    /**
     * Ranking relative to siblings
     *
     * @return <em>constant</em> ranking relative to siblings for the transport.
     * This should also probably be unique, but again, it is the transport's
     * responsibility to guarantee this.
     */
    public int rank();

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
