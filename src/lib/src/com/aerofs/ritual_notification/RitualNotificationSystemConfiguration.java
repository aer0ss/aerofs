/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;

import java.net.InetAddress;

public class RitualNotificationSystemConfiguration
{
    public InetAddress getAddress()
    {
        return LibParam.LOCALHOST_ADDR;
    }

    public int getPort()
    {
        return Cfg.port(PortType.RITUAL_NOTIFICATION);
    }
}
