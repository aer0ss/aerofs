package com.aerofs.polaris;

public abstract class Constants {

    public static final int MAX_RETURNED_TRANSFORMS = 100;

    public static final String DEPLOYMENT_SECRET_INJECTION_KEY = "DEPLOYMENT_SECRET";

    public static final String DEPLOYMENT_SECRET_ABSOLUTE_PATH = "/data/deployment_secret";

    //
    // default parameters
    //


    //
    // other defaults
    //

    public static final long INITIAL_OBJECT_VERSION = 0;

    private Constants() {
        // to prevent instantiation by subclasses
    }
}
