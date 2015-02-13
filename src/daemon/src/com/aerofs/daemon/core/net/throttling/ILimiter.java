/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.throttling;

import com.aerofs.ids.DID;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Limit;

/**
 * A <code>Limiter</code> is a class that implements some sort of throttling
 * mechanism to {@link Outgoing} message objects. You can have multiple
 * <code>Limiter</code> levels, each throttling using different criteria.
 * Limiting can be thought of as a pipeline, with stages as follows:
 * <p>
 * originator
 *      --> processOutgoing_ (L1)
 *     --> processConfirmedOutgoing_ (L1)
 *          --> processOutgoing_ (L2)
 *          --> processConfirmedOutgoing_ (L2)
 *              --> ...
 * <_p/>
 */
public interface ILimiter
{
    /**
     * Begin processing a <code>Outgoing</code>. <b>IMPORTANT:</b> this is the
     * entry-point into the limiting (aka. throttling) subsystem
     *
     * @param o <code>Outgoing</code> to process_. <b>IMPORTANT:</b> this
     * <code>Outgoing</code> has not been confirmed yet by the
     * <code>ILimiter</code>
     * @param p priority of the <code>Outgoing</code>
     * @throws Exception if processing of this <code>Outgoing</code> fails
     */
    public void processOutgoing_(Outgoing o, Prio p)
        throws Exception;

    /**
     * Process a <code>Outgoing</code> that has been confirmed by this
     * {@link ILimiter}
     *
     * @param o <code>Outgoing</code> that was confirmed and that should be processed
     * @param p priority of the <code>Outgoing</code>
     * @throws Exception if processing of this <code>Outgoing</code> fails
     */
    public void processConfirmedOutgoing_(Outgoing o, Prio p)
        throws Exception;

    /**
     * Process a {@link com.aerofs.proto.Limit.PBLimit} control message from a
     * peer
     *
     * @param d Device from which the <code>PBLimit</code> was received
     * @param pbl the <code>PBLimit</code> message received
     */
    public void processControlLimit_(DID d, Limit.PBLimit pbl);

    /**
     * @return a human-readable name for the concrete <code>ILimiter</code>
     * implementer
     */
    public String name();
}
