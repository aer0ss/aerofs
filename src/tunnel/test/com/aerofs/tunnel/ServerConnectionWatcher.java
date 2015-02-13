/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;

/**
 * Helper class to capture incoming physical connections
 */
public class ServerConnectionWatcher extends ConnectionWatcher<TunnelHandler>
        implements ITunnelConnectionListener
{
    public final TunnelServer server;

    public ServerConnectionWatcher(Timer timer, IPrivateKeyProvider key, ICertificateProvider cacert)
    {
        server = new TunnelServer(new InetSocketAddress(0), key, cacert,
                UserID.DUMMY, new DID(UniqueID.ZERO), timer, this);
    }

    @Override
    public void tunnelOpen(TunnelAddress addr, TunnelHandler handler)
    {
        connected(handler);
    }

    @Override
    public void tunnelClosed(TunnelAddress addr, TunnelHandler handler)
    {
        disconnected(handler);
    }
}
