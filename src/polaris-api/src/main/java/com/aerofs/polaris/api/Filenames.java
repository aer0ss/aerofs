package com.aerofs.polaris.api;

import com.google.common.base.Charsets;

import javax.annotation.Nullable;

/**
 * Utility methods for serializing and deserializing filenames
 */
public abstract class Filenames {

    public static byte[] toBytes(String filename) {
        return filename.getBytes(Charsets.UTF_8);
    }

    public static String fromBytes(@Nullable byte[] filename) {
        if (filename == null) {
            return null;
        } else {
            return new String(filename, Charsets.UTF_8);
        }
    }

    private Filenames() {
        // to prevent instantiation by subclasses
    }
}
