/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class TestPropertiesRenderer
{
    private PropertiesRenderer _propertiesRenderer = new PropertiesRenderer();

    @Test
    public void testRenderProperties()
            throws Exception
    {
        String testData = "foo=Foo\n" +
                "bar=Bar\n" +
                "foo2=${foo}\n" +
                "foobar=${foo}${bar}\n" +
                "foo_at_bar=${foo} at ${bar}\n" +
                "base.host.unified=share.syncfs.com\n" +
                "base.sp.url=https://${base.host.unified}:4433/sp";

        Properties properties = new Properties();

        try (InputStream stream = new ByteArrayInputStream(testData.getBytes("UTF-8"))) {
            properties.load(stream);
        }

        Properties rendered = _propertiesRenderer.renderProperties(properties);

        assertEquals("Foo", rendered.getProperty("foo"));
        assertEquals("Foo", rendered.getProperty("foo2"));
        assertEquals("FooBar", rendered.getProperty("foobar"));
        assertEquals("Foo at Bar", rendered.getProperty("foo_at_bar"));
        assertEquals("https://share.syncfs.com:4433/sp", rendered.getProperty("base.sp.url"));
    }
}
