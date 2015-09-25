package com.aerofs.trifrost.api;

import com.aerofs.trifrost.base.UniqueID;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class TestUniqueID extends TestCase {

    @Test
    public void testGenerateUuid() throws Exception {
        char[] char1 = UniqueID.generateUUID();
        char[] char2 = UniqueID.generateUUID();
        Assert.assertNotEquals(new String(char1), "");
        Assert.assertNotEquals(new String(char1), new String(char2));
    }

    @Test
    public void testGenerateDecimalString() throws Exception {
        char[] char1 = new UniqueID().generateDeviceString();
        char[] char2 = new UniqueID().generateDeviceString();
        Assert.assertEquals(char1.length, 10);
        Assert.assertNotEquals(Long.parseLong(new String(char1)), 0L);
        // so ... one time out of a million this will spurious fail.
        Assert.assertNotEquals(Long.parseLong(new String(char1)), Long.parseLong(new String(char2)));
    }
}