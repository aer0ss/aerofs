package com.aerofs.base.config;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoader
{
    public Properties loadPropertiesFromStream(InputStream stream)
            throws Exception
    {
        Properties properties = new Properties();
        properties.load(stream);
        return properties;
    }

    public Properties loadPropertiesFromPwdOrClasspath(String filename)
            throws Exception
    {
        try (InputStream stream = openFileOrResourceStream(filename)) {
            return loadPropertiesFromStream(stream);
        }
    }

    private InputStream openFileOrResourceStream(String filename)
    {
        try {
            return new File(filename).toURI().toURL().openStream();
        } catch (Exception e) {
            return Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
        }
    }
}
