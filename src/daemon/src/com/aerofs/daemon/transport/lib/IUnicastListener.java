/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;

/**
 * Implemented by classes that want to be notified of
 * events from a transport unicast subsystem.
 *
 * FIXME: Two different use cases here that feel disjoint:
 *      unicast ready / unavailable
 *      device oneline / offline
 * Pull into two interfaces?
 */
public interface IUnicastListener
{
    /**
     * Triggered when the unicast subsystem
     * is ready to make one-to-one connections to
     * a remote device.
     */
    void onUnicastReady();

    /**
     * Triggered when the unicast subsystem
     * cannot make one-to-one connections to
     * a remote device.
     */
    void onUnicastUnavailable();

    /**
     * Triggered when a connection is made to
     * a remote device, <strong>or</strong> when a
     * remote device makes an incoming connection
     * to the local device.
     *
     * @param did device that became potentially available
     */
    void onDeviceConnected(DID did);

    /**
     * Triggered <strong>once</strong> when
     * both an incoming and outgoing connection
     * to/from a remote device are
     * disconnected (i.e. this is only triggered
     * when these connections once existed and
     * are taken down).
     *
     * @param did device that became unavailable
     */
    void onDeviceDisconnected(DID did);
}
