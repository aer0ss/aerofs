package com.aerofs.devman.server;

public abstract class ResourceConstants
{
    private ResourceConstants()
    {
        // private to enforce uninstantiability
    }

    public static final String LAST_SEEN_PATH = "last_seen";
    public static final String LAST_SEEN_DEVICE = "did";

    public static final String POLLING_INTERVAL_PATH = "polling_interval";
}