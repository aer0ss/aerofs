package com.aerofs.devman.server;

public abstract class ResourceConstants
{
    private ResourceConstants()
    {
        // private to enforce uninstantiability
    }

    public static final String DEVICES_PATH = "devices";
    public static final String DEVICES_SUBRESOURCE = "device"; // note no 's'.

    public static final String POLLING_INTERVAL_PATH = "polling_interval";
}