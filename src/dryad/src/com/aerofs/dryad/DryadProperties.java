/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.aerofs.base.config.ConfigurationProperties;

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

    public DryadProperties(File source)
            throws IOException
    {
        FileInputStream is = null;
        try {
            is = new FileInputStream(source);
            load(is);
        } finally {
            if (is != null) { is.close(); }
        }

        ConfigurationProperties.setProperties(this);
    }
}
