/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;

/**
 * Implemented by classes that want to be notified of device-presence 'edges'.
 * These edges occur whenever a remote device transitions from the 'potentially-available'
 * to 'unavailable' stage, and vice versa.
 * <p/>
 * For more information, please see transport_presence_design_document.md for more
 * detail, and exact definitions of 'potentially-available' and 'unavailable'.
 */
public interface IDevicePresenceListener
{
    /**
     * Callback function that is triggered on every device-presence edge
     *
     * @param did {@link DID} of the remote device whose presence has changed
     * @param isPotentiallyAvailable true if a connection may be established to this device, false otherwise
     */
    void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable);
}
