package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.lib.event.Prio;

import javax.annotation.Nullable;

/**
 * Provides packet transmission services to a single remote deivce.
 * <br/>
 * <strong>IMPORTANT IMPLEMENTATION NOTES:</strong>
 * <ul>
 *     <li><code>wtr</code>: if <code>wtr</code> is
 *     not <code>null</code>, implementations <strong>MUST</strong> call
 *     {@link IResultWaiter#okay()} if the packet was successfully transmitted,
 *     or {@link IResultWaiter#error(Exception)} if there was an error in
 *     transmitting the packet</li>
 *     <li><code>pri</code>: implementations <strong>MUST</strong> respect
 *     this parameter and must <strong>NEVER</strong> order without priority
 *     (this prevents priority inversion)</li>
 *     <li><code>cke</code>: implementers can assume the following:
 *         <ol>
 *             <li>If <code>cke</code> is <code>null</code>, the packet is
 *                 either 1) the first packet in a stream, or 2) not part of a
 *                 stream</li>
 *             <li>If <code>cke</code> is <em>not</em> <code>null</code>, the
 *                 packet is part of an existing stream</li>
 *         </ol>
 *         Implementers have to ensure that regardless of how stream packets are
 *         sent, they <strong>MUST</strong> arrive in order at the peer.</li>
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
     * @param wtr {@link IResultWaiter} representing the object to be notified
     * if 1) the data is successfully transmitted, or 2) there was an error in
     * transmitting the data
     * @param pri {@link Prio} priority of the packet to be sent. The higher the
     * priority, the sooner the implementation will schedule the actual send call
     * @param bss bytes to be sent
     * @param cke an object representing a "stream cookie". If <code>cke</code>
     * is <code>null</code>, the packet is either 2) the <em>first</em> packet in
     * a stream, or 2) not part of a stream. If <code>cke</code> is <em>not</em>
     * null however, the packet <em>is</em> part of a stream.
     * @return an object representing a "stream cookie". Callers
     * <strong>MUST</strong> use this return value as <code>cke</code> for
     * <em>all</em>subsequent chunks in the same stream
     */
    // FIXME (AG): split this into at least two calls: one for individual packets, another for streams
    // FIXME (AG): this API is confusing. It's unclear that bss already has to be a packet with a PBTPHeader. I would expect this header to be added by Unicast
    Object send(DID did, @Nullable IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cke) throws ExTransportUnavailable, ExDeviceUnavailable;
}
