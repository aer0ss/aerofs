/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.params;

import com.google.common.base.Throwables;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;

/**
 * "Dummy" property source that always return the default value
 */
public class DummyPropertySource implements IPropertySource
{
    @Override
    public IProperty<String> stringProperty(String key, final String defaultValue)
    {
        return new IProperty<String>()
        {
            @Override
            public String get()
            {
                return defaultValue;
            }
        };
    }

    @Override
    public IProperty<InetSocketAddress> addressProperty(String key, final InetSocketAddress defaultValue)
    {
        return new IProperty<InetSocketAddress>()
        {
            @Override
            public InetSocketAddress get()
            {
                return defaultValue;
            }
        };
    }

    @Override
    public IProperty<URL> urlProperty(String key, String defaultValue)
    {
        final URL url = parseUrl(defaultValue);

        return new IProperty<URL>()
        {
            @Override
            public URL get()
            {
                return url;
            }
        };
    }

    @Override
    public IProperty<X509Certificate> certificateProperty(String key, final X509Certificate defaultValue)
    {
        return new IProperty<X509Certificate>()
        {
            @Override
            public X509Certificate get()
            {
                return defaultValue;
            }
        };
    }

    private URL parseUrl(final String url)
    {
        try {
            return new URL(url);
        } catch (final MalformedURLException e) {
            throw Throwables.propagate(e);
        }
    }
}
