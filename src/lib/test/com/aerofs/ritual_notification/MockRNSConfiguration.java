/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

public class MockRNSConfiguration extends RitualNotificationSystemConfiguration
{
    private final int port = 1024 + new Random().nextInt(10000);

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
        return port;
    }
}
