/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;

import java.net.SocketAddress;

// FIXME (AG): this interface is deprecated and should not be used anymore
public interface IUnicastCallbacks
{
    public SocketAddress resolve(DID did) throws ExDeviceUnavailable;
    public void onClientCreated(ClientHandler client);
}
