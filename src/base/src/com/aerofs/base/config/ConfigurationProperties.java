/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import com.aerofs.base.BaseSecUtil;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * Wrapper class around Properties. provides URL parsing, Address parsing, and cert reading.
 */
public class ConfigurationProperties
{
    private static Properties _properties;
    private static PropertiesHelper _propertiesHelper = new PropertiesHelper();

    /**
     * Creates a property source from properties.
     *
     * @param properties The properties to use.
     */
    public static void setProperties(Properties properties)
    {
        _properties = properties;
    }

    public static InetSocketAddress getAddressProperty(String key, InetSocketAddress defaultValue)
    {
        return parseAddress(_properties.getProperty(key), defaultValue);
    }

    public static Boolean getBooleanProperty(String key, Boolean defaultValue)
    {
        return _propertiesHelper.getBooleanWithDefaultValueFromProperties(_properties, key,
                defaultValue);
    }

    public static Integer getIntegerProperty(String key, Integer defaultValue)
    {
        return _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(_properties, key,
                defaultValue);
    }

    public static Long getLongProperty(String key, Long defaultValue)
    {
        return _propertiesHelper.getLongWithDefaultValueFromPropertiesObj(_properties, key,
                defaultValue);
    }

    public static String getStringProperty(String key, String defaultValue)
    {
        return _properties.getProperty(key, defaultValue);
    }

    public static Optional<String> getOptionalStringProperty(String key)
    {
        return Optional.fromNullable(_properties.getProperty(key));
    }

    public static Optional<Integer> getOptionalIntegerProperty(String key)
    {
        String value = _properties.getProperty(key);
        Integer intValue = (value != null) ? Integer.valueOf(value) : null ;
        return Optional.fromNullable(intValue);
    }

    public static URL getUrlProperty(String key, String defaultValue)
    {
        return parseUrl(_properties.getProperty(key, defaultValue));
    }

    public static X509Certificate getCertificateProperty(String key,
            X509Certificate defaultValue)
    {
        X509Certificate cacert = null;
        String cacertString = _properties.getProperty(key);

        if (cacertString == null) {
            cacert = defaultValue;
        } else {
            try {
                cacert = (X509Certificate) BaseSecUtil.newCertificateFromString(cacertString);
            } catch (CertificateException e) { // If certificate is garbage, bail.
                Throwables.propagate(e);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        return cacert;
    }

    private static InetSocketAddress parseAddress(@Nullable String address, InetSocketAddress defaultValue)
    {
        if (address == null) return defaultValue;
        HostAndPort hostAndPort = HostAndPort.fromString(address);
        return InetSocketAddress.createUnresolved(hostAndPort.getHostText(), hostAndPort.getPort());
    }

    private static URL parseUrl(String url)
    {
        try {
            return new URL(url);
        } catch (final MalformedURLException e) {
            throw Throwables.propagate(e);
        }
    }
}
