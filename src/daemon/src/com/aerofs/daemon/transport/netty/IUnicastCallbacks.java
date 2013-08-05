/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.netty;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.netty.ClientHandler;
import com.aerofs.lib.ex.ExDeviceOffline;

import java.net.SocketAddress;

public interface IUnicastCallbacks
{
    public SocketAddress resolve(DID did) throws ExDeviceOffline;
    public void closePeerStreams(DID did, boolean outbound, boolean inbound);
    public void onClientCreated(ClientHandler client);
}
