/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;

/**
 * Wrapper class around Properties. provides URL parsing, Address parsing, and cert reading.
 */
public class ConfigurationProperties
{
    private static BaseProperties _properties;

    public static void setProperties(Properties properties)
    {
        _properties = new BaseProperties(properties);
    }

    public static InetSocketAddress getAddressProperty(String key, InetSocketAddress defaultValue)
    {
        return _properties.getAddressProperty(key, defaultValue);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue)
    {
        return _properties.getBooleanProperty(key, defaultValue);
    }

    public static int getIntegerProperty(String key, int defaultValue)
    {
        return _properties.getIntProperty(key, defaultValue);
    }

    public static String getStringProperty(String key, String defaultValue)
    {
        return _properties.getStringProperty(key, defaultValue);
    }

    public static String getNonEmptyStringProperty(String key, String defaultValue)
    {
        return _properties.getStringPropertyNonEmpty(key, defaultValue);
    }

    public static Optional<String> getOptionalStringProperty(String key)
    {
        return _properties.getOptionalStringProperty(key);
    }

    public static URL getUrlProperty(String key, String defaultValue)
    {
        return _properties.getUrlProperty(key, defaultValue);
    }
}
