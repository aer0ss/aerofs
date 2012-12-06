/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.ex.ExNotFound;
import org.junit.Before;
import org.junit.Test;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Sp.GetPreferencesReply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A class to test saving and fetching preferences
 */
public class TestSP_Preferences extends AbstractSPTest
{
    private final DID _did = new DID(UniqueID.generate());

    @Before
    public void setup()
    {
        setSessionUser(TEST_USER_1);
    }

    @Test
    public void shouldTrimUserAndDeviceNames() throws Exception
    {
        trans.begin();
        ddb.addDevice(_did, TEST_USER_1, "name");
        trans.commit();

        service.setPreferences("   first ", " last   ", _did.toPB(), "  device names  ").get();
        GetPreferencesReply reply = service.getPreferences(_did.toPB()).get();

        assertTrimmed(reply.getDeviceName());
        assertTrimmed(reply.getFirstName());
        assertTrimmed(reply.getLastName());
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfDeviceDoesntExistWhenSettingName()
            throws Exception
    {
        service.setPreferences("first", "last", _did.toPB(), "device").get();
    }

    @Test
    public void shouldReturnEmptyNameIfDeviceDoesntExistWhenGettingPref()
            throws Exception
    {
        GetPreferencesReply reply = service.getPreferences(_did.toPB()).get();
        assertTrue(reply.getDeviceName().isEmpty());
    }

    private void assertTrimmed(String str)
    {
        assertEquals(str.trim(), str);
    }
}