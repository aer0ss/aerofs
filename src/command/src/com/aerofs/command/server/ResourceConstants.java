package com.aerofs.command.server;

public abstract class ResourceConstants
{
    private ResourceConstants()
    {
        // private to enforce uninstantiability
    }

    public static final String DEVICES_PATH = "devices";
    public static final String DEVICES_SUBRESOURCE = "device"; // note no 's'.

    public static final String ENQUEUES_PATH = "enqueues";
    public static final String ENQUEUES_SUBRESOURCE = "enqueue";

    public static final String COMMAND_TYPES_PATH = "command_types";
}