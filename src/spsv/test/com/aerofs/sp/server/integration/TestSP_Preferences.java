/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.sp.server.lib.device.Device;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A class to test saving and fetching preferences
 */
public class TestSP_Preferences extends AbstractSPTest
{
    @Before
    public void setup()
            throws Exception
    {
        mockAndCaptureVerkehrDeliverPayload();
        setSession(USER_1);
    }

    @Test
    public void shouldTrimUserAndDeviceNames() throws Exception
    {
        sqlTrans.begin();
        Device device = saveDevice(USER_1);
        sqlTrans.commit();

        service.setUserPreferences(session.getAuthenticatedUserLegacyProvenance().id().getString(),
                "   first ", " last   ", device.id().toPB(), "  device names  ").get();
        GetUserPreferencesReply reply = service.getUserPreferences(device.id().toPB()).get();

        assertTrimmed(reply.getDeviceName());
        assertTrimmed(reply.getFirstName());
        assertTrimmed(reply.getLastName());
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfDeviceDoesntExistWhenSettingName()
            throws Exception
    {
        service.setUserPreferences(session.getAuthenticatedUserLegacyProvenance().id().getString(),
                "first", "last", new DID(UniqueID.generate()).toPB(), "device").get();
    }

    @Test
    public void shouldReturnEmptyNameIfDeviceDoesntExistWhenGettingPref()
            throws Exception
    {
        GetUserPreferencesReply reply = service.getUserPreferences(
                new DID(UniqueID.generate()).toPB()).get();
        assertTrue(reply.getDeviceName().isEmpty());
    }

    private void assertTrimmed(String str)
    {
        assertEquals(str.trim(), str);
    }
}
