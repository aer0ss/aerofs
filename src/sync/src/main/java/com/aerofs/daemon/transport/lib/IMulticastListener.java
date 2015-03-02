/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;

/**
 * Implemented by classes that want to be notified of
 * events from a transport multicast subsystem.
 */
public interface IMulticastListener
{
    /**
     * Triggered when the entire multicast subsystem is active.
     */
    void onMulticastReady();

    /**
     * Triggered when the entire multicast subsystem becomes inactive and
     * cannot deliver presence information.
     */
    void onMulticastUnavailable();

    /**
     * Triggered <strong>the first time</strong> the multicast
     * subsystem receives presence information about a remote device, i.e.
     * the first time transitions from unavailable to
     * potentially available.
     *
     * @param did device that became potentially available
     */
    void onDeviceReachable(DID did);

    /**
     * Triggered <strong>once</strong> when the multicast
     * subsystem believes that the device transitioned from
     * potentially-available to unavilable.
     *
     * @param did device that became unavailable
     */
    void onDeviceUnreachable(DID did);
}
