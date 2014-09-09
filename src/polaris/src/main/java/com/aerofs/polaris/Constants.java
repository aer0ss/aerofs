package com.aerofs.polaris;

public abstract class Constants {

    public static final int MAX_RETURNED_TRANSFORMS = 100;

    private Constants() {
        // to prevent instantiation by subclasses
    }

    // FIXME (AG): remove this from here!
    public static boolean isSharedFolder(String oid) {
        return oid.startsWith("SF");
    }
}
