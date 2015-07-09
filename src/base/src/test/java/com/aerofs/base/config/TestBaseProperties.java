package com.aerofs.base.config;

import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Properties;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.*;

public class TestBaseProperties
{
    private BaseProperties _properties;

    @Before
    public void setup()
    {
        Properties properties = new Properties();
        properties.setProperty("string",    "value");
        properties.setProperty("empty",     "");
        properties.setProperty("int",       "42");
        properties.setProperty("true1",     "true");
        properties.setProperty("true2",     "True");
        properties.setProperty("true3",     "tRUE");
        properties.setProperty("false",     "yes");
        properties.setProperty("inet1",     "4.2.2.2:80");
        properties.setProperty("inet2",     "localhost:80");
        properties.setProperty("inet3",     "example.test.com:80");
        properties.setProperty("url1",      "http://example.test.com");
        properties.setProperty("url2",      "http://example.test.com/path/to/resource");
        properties.setProperty("url3",      "http://example.test.com:80/path/to/resource");
        _properties = new BaseProperties(properties);
    }

    @Test
    public void shouldHandleStringProperties()
    {
        // covers getOptionalStringProperty
        assertEquals(Optional.of("value"),  _properties.getOptionalStringProperty("string"));
        assertEquals(Optional.of(""),       _properties.getOptionalStringProperty("empty"));
        assertEquals(Optional.empty(),      _properties.getOptionalStringProperty("null"));

        // covers getStringProperty
        assertEquals("value",               _properties.getStringProperty("string", "default"));
        assertEquals("",                    _properties.getStringProperty("empty", "default"));
        assertEquals("default",             _properties.getStringProperty("null", "default"));

        // covers getStringPropertyNonEmpty
        assertEquals("value",               _properties.getStringPropertyNonEmpty("string", "default"));
        assertEquals("default",             _properties.getStringPropertyNonEmpty("empty", "default"));
        assertEquals("default",             _properties.getStringPropertyNonEmpty("null", "default"));

        // defaults of "" and null are not recommended, but permitted.
        assertEquals("",                    _properties.getStringProperty("null", ""));
        assertEquals("",                    _properties.getStringPropertyNonEmpty("null", ""));
        assertEquals(null,                  _properties.getStringProperty("null", null));
        assertEquals(null,                  _properties.getStringPropertyNonEmpty("null", null));
    }

    @Test
    public void shouldHandleIntProperties()
    {
        assertEquals(42,                    _properties.getIntProperty("int", 314));
        assertEquals(314,                   _properties.getIntProperty("null", 314));
        assertEquals(314,                   _properties.getIntProperty("empty", 314));
    }

    @Test
    public void shouldThrowOnInvalidIntProperties()
    {
        try {
            _properties.getIntProperty("string", 3);
            fail();
        } catch (NumberFormatException ignored) {
            // expected
        }
    }

    @Test
    public void shouldHandleBooleanProperties()
    {
        assertTrue(     _properties.getBooleanProperty("true1", false));
        assertTrue(     _properties.getBooleanProperty("true2", false));
        assertTrue(     _properties.getBooleanProperty("true3", false));
        assertFalse(    _properties.getBooleanProperty("false", true));
        // since we only have two outputs, to ensure default behaviour is taken, we need to cover
        //   both cases with different default values.
        assertTrue(     _properties.getBooleanProperty("empty", true));
        assertFalse(    _properties.getBooleanProperty("empty", false));
        assertTrue(     _properties.getBooleanProperty("null", true));
        assertFalse(    _properties.getBooleanProperty("null", false));
    }

    @Test
    public void shouldHandleAddressProperties()
    {
        InetSocketAddress inet1 = InetSocketAddress.createUnresolved("4.2.2.2", 80);
        InetSocketAddress inet2 = InetSocketAddress.createUnresolved("localhost", 80);
        InetSocketAddress inet3 = InetSocketAddress.createUnresolved("example.test.com", 80);

        assertEquals(inet1, _properties.getAddressProperty("inet1", inet2));
        assertEquals(inet2, _properties.getAddressProperty("inet2", inet1));
        assertEquals(inet3, _properties.getAddressProperty("inet3", inet1));
        // falls back to default if the property is not specified
        assertEquals(inet1, _properties.getAddressProperty("null", inet1));
        assertEquals(inet2, _properties.getAddressProperty("null", inet2));
    }

    @Test
    public void shouldThrowOnInvalidAddress()
    {
        InetSocketAddress addr = InetSocketAddress.createUnresolved("4.2.2.2", 80);

        // url1 - no port.
        // url2 - we cannot handle an url containing the protocol.
        for (String key : newArrayList("url1", "url2", "empty")) {
            try {
                _properties.getAddressProperty(key, addr);
                fail();
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // expected
                // note that failure to parse the property value could've resulted in
                // either IllegalArgumentException (failed to parse port) or
                // IllegalStateException (no ports provided).
            }
        }
    }
}
