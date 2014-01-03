/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;

import java.net.InetSocketAddress;

public class RitualNotificationSystemConfiguration
{
    public InetSocketAddress getAddress()
    {
        return new InetSocketAddress(LibParam.LOCALHOST_ADDR,
                Cfg.port(PortType.RITUAL_NOTIFICATION));
    }
}
