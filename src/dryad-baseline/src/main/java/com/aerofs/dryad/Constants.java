package com.aerofs.dryad;

import java.util.regex.Pattern;

public abstract class Constants {

    public static final String DEFAULT_STORAGE_DIRECTORY = "/data";
    public static final String DEFECTS_DIRECTORY = "defects";
    public static final String ARCHIVED_LOGS_DIRECTORY = "archived";
    public static final String[] STORAGE_SUB_DIRECTORIES = { DEFECTS_DIRECTORY, ARCHIVED_LOGS_DIRECTORY };

    public static final Pattern ID_FORMAT = Pattern.compile("^[0-9a-fA-F]{32}$");

    private Constants() {
        // to prevent instantiation by subclasses
    }
}
