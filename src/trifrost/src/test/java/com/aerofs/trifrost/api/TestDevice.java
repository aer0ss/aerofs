package com.aerofs.trifrost.api;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestDevice {
    @Test
    public void testInstantiateWithoutPush() throws Exception {
        Device device;

        device = new Device("test name", "test family");
        assertEquals(device.getName(), "test name");
        assertEquals(device.getFamily(), "test family");
        assertNull(device.getPushType());
        assertNull(device.getPushToken());
    }

    @Test
    public void testInstantiateWithPush() throws Exception {
        Device device;

        device = new Device("test name", "test family", null, null);
        assertEquals(device.getName(), "test name");
        assertEquals(device.getFamily(), "test family");
        assertNull(device.getPushType());
        assertNull(device.getPushToken());

        device = new Device("test name", "test family", Device.PushType.NONE, null);
        assertEquals(device.getName(), "test name");
        assertEquals(device.getFamily(), "test family");
        assertEquals(device.getPushType(), Device.PushType.NONE);
        assertNull(device.getPushToken());

        device = new Device("test name", "test family", Device.PushType.GCM, "abc123");
        assertEquals(device.getName(), "test name");
        assertEquals(device.getFamily(), "test family");
        assertEquals(device.getPushType(), Device.PushType.GCM);
        assertEquals(device.getPushToken(), "abc123");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidInstantiateWithPush() throws Exception {
        Device device = new Device("test name", "test family", Device.PushType.GCM, null);
    }

    @Test
    public void testIsValidPushCombination() throws Exception {
        assertTrue(Device.isValidPushCombination(null, null));
        assertTrue(Device.isValidPushCombination(Device.PushType.NONE, null));
        assertTrue(Device.isValidPushCombination(Device.PushType.GCM, "test token"));
        assertFalse(Device.isValidPushCombination(Device.PushType.NONE, "test token"));
        assertFalse(Device.isValidPushCombination(Device.PushType.GCM, null));
        assertFalse(Device.isValidPushCombination(Device.PushType.GCM, ""));
    }

    @Test
    public void testParsePushToken() throws Exception {
        assertEquals(Device.parsePushToken(""), "");
        assertEquals(Device.parsePushToken("abc"), "abc");
        assertEquals(Device.parsePushToken("ABC"), "abc");
        assertEquals(Device.parsePushToken("abc123"), "abc123");
        assertEquals(Device.parsePushToken("abcxyz"), "abc");
        assertEquals(Device.parsePushToken("abcxyz123"), "abc123");
        assertEquals(Device.parsePushToken("abc !@# 123 $%^"), "abc123");
        assertEquals(Device.parsePushToken("<2ed202ac 08ea9033 665e853a 3dc8bc4c 5e78f7a6 cf8d5591 0df23056 7037dcc4>"), "2ed202ac08ea9033665e853a3dc8bc4c5e78f7a6cf8d55910df230567037dcc4");
    }
}