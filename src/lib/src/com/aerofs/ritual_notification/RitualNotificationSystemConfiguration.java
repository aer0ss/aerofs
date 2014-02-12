/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;

import java.net.InetSocketAddress;

public class RitualNotificationSystemConfiguration
{
    public InetSocketAddress getAddress()
    {
        return new InetSocketAddress(host(), port());
    }

    public String host()
    {
        return "127.0.0.1";
    }

    public int port()
    {
        return Cfg.port(PortType.RITUAL_NOTIFICATION);
    }
}
