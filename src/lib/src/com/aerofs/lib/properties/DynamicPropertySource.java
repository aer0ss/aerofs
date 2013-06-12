/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.properties;

import com.aerofs.base.params.IProperty;
import com.aerofs.base.params.IPropertySource;
import com.netflix.config.DynamicStringProperty;

import java.net.InetSocketAddress;
import java.net.URL;

/**
 * This class allows the code in the 'base' module to use the dynamic property system.
 * See comment in IPropertySource for more info as to why we need to do this.
 */
public class DynamicPropertySource implements IPropertySource
{
    @Override
    public IProperty<String> stringProperty(String key, String defaultValue)
    {
        return new StringProperty(key, defaultValue);
    }

    @Override
    public IProperty<InetSocketAddress> addressProperty(String key, InetSocketAddress defaultValue)
    {
        return new DynamicInetSocketAddress(key, defaultValue);
    }

    @Override
    public IProperty<URL> urlProperty(String key, String defaultValue)
    {
        return new DynamicUrlProperty(key, defaultValue);
    }

    /**
     * Wrapper class to allow DynamicStringProperty to implement IProperty<String>
     */
    private static class StringProperty extends DynamicStringProperty implements IProperty<String>
    {
        public StringProperty(String propName, String defaultValue)
        {
            super(propName, defaultValue);
        }
    }
}
