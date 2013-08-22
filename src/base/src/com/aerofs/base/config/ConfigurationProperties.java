/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.params.IProperty;
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

    public static IProperty<InetSocketAddress> getAddressProperty(String key,
            InetSocketAddress defaultValue)
    {
        final InetSocketAddress returnValue = parseAddress(_properties.getProperty(key), defaultValue);
        return new IProperty<InetSocketAddress>()
        {
            @Override
            public InetSocketAddress get()
            {
                return returnValue;
            }
        };
    }

    public static IProperty<Boolean> getBooleanProperty(String key, Boolean defaultValue)
    {
        final Boolean returnValue = _propertiesHelper.getBooleanWithDefaultValueFromProperties(
                _properties, key, defaultValue);

        return new IProperty<Boolean>()
        {
            @Override
            public Boolean get()
            {
                return returnValue;
            }
        };
    }

    public static IProperty<Integer> getIntegerProperty(String key, Integer defaultValue)
    {
        final Integer returnValue = _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(
                _properties, key, defaultValue);

        return new IProperty<Integer>()
        {
            @Override
            public Integer get()
            {
                return returnValue;
            }
        };
    }

    public static IProperty<String> getStringProperty(String key, String defaultValue)
    {
        final String returnValue = _properties.getProperty(key, defaultValue);
        return new IProperty<String>()
        {
            @Override
            public String get()
            {
                return returnValue;
            }
        };
    }

    public static IProperty<Optional<String>> getOptionalStringProperty(String key)
    {
        final Optional<String> returnValue = Optional.fromNullable(_properties.getProperty(key));
        return new IProperty<Optional<String>>()
        {
            @Override
            public Optional<String> get()
            {
                return returnValue;
            }
        };
    }

    public static IProperty<URL> getUrlProperty(String key, String defaultValue)
    {
        final URL returnValue = parseUrl(_properties.getProperty(key, defaultValue));
        return new IProperty<URL>()
        {
            @Override
            public URL get()
            {
                return returnValue;
            }
        };
    }

    public static IProperty<X509Certificate> getCertificateProperty(String key,
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

        final X509Certificate returnValue = cacert;

        return new IProperty<X509Certificate>()
        {
            @Override
            public X509Certificate get()
            {
                return returnValue;
            }
        };
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
