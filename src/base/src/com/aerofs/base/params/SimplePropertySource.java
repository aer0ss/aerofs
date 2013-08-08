/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.params;

import com.aerofs.base.BaseSecUtil;
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
public class SimplePropertySource implements IPropertySource
{
    private Properties _properties;

    /**
     * Creates a property source from properties.
     *
     * @param properties The properties to use.
     */
    public SimplePropertySource(Properties properties)
    {
        _properties = properties;
    }

    @Override
    public IProperty<InetSocketAddress> addressProperty(String key, InetSocketAddress defaultValue)
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

    @Override
    public IProperty<String> stringProperty(String key, String defaultValue)
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

    @Override
    public IProperty<URL> urlProperty(String key, String defaultValue)
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

    @Override
    public IProperty<X509Certificate> certificateProperty(String key, X509Certificate defaultValue)
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

    private InetSocketAddress parseAddress(@Nullable String address, InetSocketAddress defaultValue)
    {
        if (address == null) return defaultValue;
        HostAndPort hostAndPort = HostAndPort.fromString(address);
        return InetSocketAddress.createUnresolved(hostAndPort.getHostText(), hostAndPort.getPort());
    }

    private URL parseUrl(String url)
    {
        try {
            return new URL(url);
        } catch (final MalformedURLException e) {
            throw Throwables.propagate(e);
        }
    }
}
