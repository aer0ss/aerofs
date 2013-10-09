/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

public interface ITunnelConnectionListener
{
    void tunnelOpen(TunnelAddress addr, TunnelHandler handler);
    void tunnelClosed(TunnelAddress addr, TunnelHandler handler);
}
