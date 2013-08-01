/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class MockRNSConfiguration extends RitualNotificationSystemConfiguration
{
    @Override
    public InetAddress getAddress()
    {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    public int getPort()
    {
        return 60000;
    }
}
