/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import java.util.Random;

public class MockRNSConfiguration extends RitualNotificationSystemConfiguration
{
    private final int port = 1024 + new Random().nextInt(10000);

    @Override
    public int port()
    {
        return port;
    }
}
