package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;

/**
 * Implemented by classes that want to be notified when connection to devices are
 *  established.
 *
 * NOTE: This focus on *connection* (=channel and sockets) and
 *       not on *presence* (=potential availability)
 */
public interface IDeviceConnectionListener
{
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
