package com.aerofs.polaris;

public abstract class Constants {

    public static final int MAX_RETURNED_TRANSFORMS = 100;

    public static final String DEPLOYMENT_SECRET_INJECTION_KEY = "DEPLOYMENT_SECRET";

    public static final String DEPLOYMENT_SECRET_ABSOLUTE_PATH = "/data/deployment_secret";

    public static final int NUM_NOTIFICATION_DATABASE_LOOKUP_THREADS = 10;

    //
    // default parameters
    //


    //
    // other defaults
    //

    public static final long INITIAL_OBJECT_VERSION = 0;

    public static final long MIGRATION_OPERATION_BATCH_SIZE = 100;

    public static final String SFNOTIF_PREFIX = "sf";

    // keep these messages in sync with the Enum defined in ThreadLocalSFNotifications.java
    public static final String SFNOTIF_JOIN = "j";
    public static final String SFNOTIF_LEAVE = "l";
    public static final String SFNOTIF_CHANGE = "c";

    private Constants() {
        // to prevent instantiation by subclasses
    }
}
