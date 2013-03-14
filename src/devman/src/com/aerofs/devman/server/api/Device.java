package com.aerofs.devman.server.api;

import java.net.InetAddress;

public class Device
{
    private final long _lastSeenTime;
    private final InetAddress _ipAddress;

    public Device(long lastSeenTime, InetAddress ipAddress)
    {
        _lastSeenTime = lastSeenTime;
        _ipAddress = ipAddress;
    }

    public long getLastSeenTime()
    {
        return _lastSeenTime;
    }

    // Capitalization must be this way so that dropwizard constructs the json object name as
    // "ip_address" not "ipaddress" (as you would get if you were to capitalize the 'p').
    public String getIpAddress()
    {
        return _ipAddress.getHostAddress();
    }
}