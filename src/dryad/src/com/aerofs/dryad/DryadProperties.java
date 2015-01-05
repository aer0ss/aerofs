/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

// the type is declared to work around Guice's type-based provision mechanism
public class DryadProperties extends Properties
{
    private static final long serialVersionUID = 0L;

    // stores String, the path to the storage directory for local file persistence.
    public static final String STORAGE_DIRECTORY = "dryad.storage.directory";

    // stores String, the hostname for the server to bind to
    public static final String SERVER_HOSTNAME = "dryad.server.hostname";

    // stores Integer, the port for the server to listen on
    public static final String SERVER_PORT = "dryad.server.port";

    // stores path to the blacklist file for users
    public static final String BLACKLIST_USERS = "dryad.blacklist.users";

    // stores path to the blacklist file for devices
    public static final String BLACKLIST_DEVICES = "dryad.blacklist.devices";

    // the directory name of the defects directory
    public static final String DIR_DEFECTS = "defects";

    // the directory name of the archived logs directory
    public static final String DIR_ARCHIVED = "archived";

    // the directory name of the health check logs directory
    public static final String DIR_HEALTHCHECK = ".healthcheck";

    public static DryadProperties loadFromFile(File source)
            throws IOException
    {
        FileInputStream is = null;
        try {
            is = new FileInputStream(source);

            DryadProperties properties = new DryadProperties();
            properties.load(is);
            return properties;
        } finally {
            if (is != null) { is.close(); }
        }
    }
}
