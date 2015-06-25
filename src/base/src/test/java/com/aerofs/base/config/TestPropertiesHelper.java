/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import com.aerofs.base.ex.ExBadArgs;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestPropertiesHelper
{
    private PropertiesHelper _propertiesHelper = new PropertiesHelper();

    @Test
    public void testGetBooleanWithDefaultValueFromProperties()
    {
        Properties a = new Properties();

        a.setProperty("true", "true");
        a.setProperty("false", "false");
        a.setProperty("wat", "tralse");

        Boolean val = _propertiesHelper.getBooleanWithDefaultValueFromProperties(a, "true", false);
        assertTrue(val);

        val = _propertiesHelper.getBooleanWithDefaultValueFromProperties(a, "false", true);
        assertFalse(val);

        val = _propertiesHelper.getBooleanWithDefaultValueFromProperties(a, "wat", false);
        assertFalse(val);

        val = _propertiesHelper.getBooleanWithDefaultValueFromProperties(a, "nonexistant", true);
        assertTrue(val);
    }

    @Test
    public void testGetIntegerWithDefaultValueFromPropertiesObj()
    {
        Properties a = new Properties();

        a.setProperty("one", "1");
        a.setProperty("big", "123456789");
        a.setProperty("negative", "-100");
        a.setProperty("bad", "23yas");

        Integer val = _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(a, "one", 0);
        assertEquals((Integer) 1, val);

        val = _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(a, "big", 0);
        assertEquals((Integer) 123456789, val);

        val = _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(a, "negative", 0);
        assertEquals((Integer) (-100), val);

        val = _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(a, "nonexistant", 0);
        assertEquals((Integer) 0, val);

        try {
            val = _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(a, "bad", 0);
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // pass
        }
    }

    @Test
    public void testParseProperties()
            throws ExBadArgs
    {
        Properties a = new Properties();

        a.setProperty("copy3", "${three}");
        a.setProperty("one", "One");
        a.setProperty("three", "Three");
        a.setProperty("copy", "${one}");
        a.setProperty("composite", "${one} Two ${three}");

        Properties parsed = _propertiesHelper.parseProperties(a);

        String one = parsed.getProperty("one");
        String copy3 = parsed.getProperty("copy3");
        String copy = parsed.getProperty("copy");
        String composite = parsed.getProperty("composite");

        assertEquals("One", one);
        assertEquals("Three", copy3);
        assertEquals("One", copy);
        assertEquals("One Two Three", composite);
    }

    @Test
    public void testLocalProdProperties()
            throws ExBadArgs
    {
        Properties raw = new Properties();

        raw.setProperty("base.host.unified", "share.syncfs.com");
        raw.setProperty("base.sp.url", "https://${base.host.unified}:4433/sp");

        Properties parsed = _propertiesHelper.parseProperties(raw);

        assertEquals(parsed.get("base.sp.url"), "https://share.syncfs.com:4433/sp");
    }

    @Test
    public void testParsePropertiesFromInputStream()
            throws Exception
    {
        ByteArrayInputStream bais = new ByteArrayInputStream("one=${two}\ntwo=Two".getBytes("UTF-8"));
        Properties a = new Properties();
        a.load(bais);

        Properties parsed = _propertiesHelper.parseProperties(a);

        String one = parsed.getProperty("one");
        String two = parsed.getProperty("two");

        assertEquals("Two", one);
        assertEquals("Two", two);
    }
}
