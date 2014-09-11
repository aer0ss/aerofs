package com.aerofs.polaris;

public abstract class Constants {

    // FIXME (AG): make this a configuration parameter
    public static final int MAX_RETURNED_TRANSFORMS = 100;

    //
    // default root parameters
    //

    public static final String NO_ROOT = "0000";

    //
    // other defaults
    //

    public static final long INITIAL_OBJECT_VERSION = 0;

    private Constants() {
        // to prevent instantiation by subclasses
    }
}
