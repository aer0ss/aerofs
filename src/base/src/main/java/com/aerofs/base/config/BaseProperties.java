package com.aerofs.base.config;

import com.google.common.net.HostAndPort;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

public class BaseProperties
{
    private final Properties _properties;

    public BaseProperties(Properties properties)
    {
        _properties = checkNotNull(properties);
    }

    public Optional<String> getOptionalStringProperty(String key)
    {
        return Optional.ofNullable(_properties.getProperty(key));
    }

    // returns defaultValue if the property is not specified
    public String getStringProperty(String key, String defaultValue)
    {
        return _properties.getProperty(key, defaultValue);
    }

    // necessary when you want to get a string property with a default value, using getStringProperty
    // will return an empty string for any values in *.tmplt that do not have a value set in
    // external.properties
    // e.g. use this if you add a configurable value to server.tmplt and you want to get a default
    // value back before the user has a chance to configure it themselves
    //
    // returns defaultValue if the property is null or an empty string
    public String getStringPropertyNonEmpty(String key, String defaultValue)
    {
        String value = _properties.getProperty(key);
        return isNullOrEmpty(value) ? defaultValue : value;
    }

    // returns defaultValue if the property is null or an empty string
    public int getIntProperty(String key, int defaultValue)
    {
        String value = _properties.getProperty(key);
        return isNullOrEmpty(value) ? defaultValue : Integer.parseInt(value);
    }

    // returns defaultValue if the property is null or an empty string
    public boolean getBooleanProperty(String key, boolean defaultValue)
    {
        String value = _properties.getProperty(key);
        return isNullOrEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public InetSocketAddress getAddressProperty(String key, InetSocketAddress defaultValue)
    {
        String value = _properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        HostAndPort addr = HostAndPort.fromString(value);
        return InetSocketAddress.createUnresolved(addr.getHostText(), addr.getPort());
    }
}
