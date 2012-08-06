/*
 * Created by alleng, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.net.IPulseEvent;
import com.aerofs.lib.id.DID;

/**
 * Specific methods to be implemented by any classes handling pulse
 * events (either scheduled by the core or rescheduled by the transport)
 *
 * @param <T> specific event type that implements <code>IPulseEvent</code>
 * (currently {@link com.aerofs.daemon.event.net.EOTpSubsequentPulse} and
 * {@link com.aerofs.daemon.event.net.EOTpStartPulse}
*/
public interface IPulseHandlerImpl<T extends IPulseEvent>
{
    //
    // accessors
    //

    /**
     * @return {@link com.aerofs.daemon.transport.ITransport} that owns this
     *         <code>IPulseHandlerImpl</code>
     */
    public ITransportImpl tp();

    //
    // event handling implementation functions
    //

    /**
     * Checks to be done before a new pulse message is created and sent to
     * the remote peer
     *
     * @param ev <code>IPulseEvent</code> this handler is handling
     * @return true if the event should continue to be handled; false to
     *         terminate handling
     */
    public boolean prepulsechecks_(T ev);

    /**
     * Concrete implementation of the method called to notify the core that
     * the pulse stopped
     *
     * @param did {@link com.aerofs.lib.id.DID} of the remote peer for which
     * the pulse was stopped
     */
    public void notifypulsestopped_(DID did);

    /**
     * To be called after a new pulse message is created and sent to the
     * remote peer. This method is called in all cases (except when the
     * presence of the remote peer has changed). Implement this method to
     * schedule the next pulse event.
     *
     * @param ev <code>IPulseEvent</code> this handler is handling
     * @return true if the event should continue to be handled; false to
     *         terminate handling
     */
    public boolean schednextpulse_(T ev);
}
