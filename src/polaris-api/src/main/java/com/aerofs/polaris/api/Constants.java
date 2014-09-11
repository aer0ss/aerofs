package com.aerofs.polaris.api;

public abstract class Constants {

    //
    // default file-property parameters
    //

    public static final String INVALID_HASH = null;
    public static final long INVALID_MODIFICATION_TIME = -1;
    public static final long INVALID_SIZE = -1;
    public static final long INITIAL_OBJECT_VERSION = 0;

    private Constants() {
        // to prevent instantiation by subclasses
    }
}
