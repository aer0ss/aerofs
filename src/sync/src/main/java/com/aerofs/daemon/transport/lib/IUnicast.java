package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides packet transmission services to a single remote device.
 * <br/>
 * <strong>IMPORTANT IMPLEMENTATION NOTES:</strong>
 * <ul>
 *     <li><code>wtr</code>: if <code>wtr</code> is
 *     not <code>null</code>, implementations <strong>MUST</strong> call
 *     {@link IResultWaiter#okay()} if the packet was successfully transmitted,
 *     or {@link IResultWaiter#error(Exception)} if there was an error in
 *     transmitting the packet</li>
 *     <li><code>cookie</code>: implementers can assume the following:
 *     Implementers have to ensure that regardless of how stream packets are
 *     sent, they <strong>MUST</strong> arrive in order at the peer.</li>
 * </ul>
 */
public interface IUnicast
{
    /**
     * Send a packet to a peer
     * <br/>
     * Implementers: see implementation notes at head of {@link IUnicast}
     *
     * @param did {@link DID} of the peer packet should be sent to
     * @param bss bytes to be sent, including valid transport header
     * @param wtr {@link IResultWaiter} representing the object to be notified
     * if 1) the data is successfully transmitted, or 2) there was an error in
     * transmitting the data
     * @return a "cookie" to be used in calls to {@link #send(Object, byte[][], IResultWaiter)}
     */
    Object send(DID did, byte[][] bss, @Nullable IResultWaiter wtr)
            throws ExTransportUnavailable, ExDeviceUnavailable;

    /**
     * Send a packet to a peer
     * <br/>
     * Implementers: see implementation notes at head of {@link IUnicast}
     *
     * @param cookie an object representing a "stream cookie" returned by a
     *               previous call to {@link #send(DID, byte[][], IResultWaiter)}
     * @param bss bytes to be sent, including valid transport header
     * @param wtr {@link IResultWaiter} representing the object to be notified
     * if 1) the data is successfully transmitted, or 2) there was an error in
     * transmitting the data
     */
    void send(@Nonnull Object cookie, byte[][] bss, @Nullable IResultWaiter wtr);
}
