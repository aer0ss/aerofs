/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;
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
        setSession(USER_1);
    }

    @Test
    public void shouldTrimUserAndDeviceNames() throws Exception
    {
        sqlTrans.begin();
        Device device = saveDevice(USER_1);
        sqlTrans.commit();

        service.setUserPreferences(session
                        .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                        .id()
                        .getString(),
                "   first ", " last   ", BaseUtil.toPB(device.id()), "  device names  ").get();
        GetUserPreferencesReply reply = service.getUserPreferences(BaseUtil.toPB(device.id())).get();

        assertTrimmed(reply.getDeviceName());
        assertTrimmed(reply.getFirstName());
        assertTrimmed(reply.getLastName());
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfDeviceDoesntExistWhenSettingName()
            throws Exception
    {
        service.setUserPreferences(session
                        .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                        .id()
                        .getString(),
                "first", "last", BaseUtil.toPB(new DID(UniqueID.generate())), "device").get();
    }

    @Test
    public void shouldReturnEmptyNameIfDeviceDoesntExistWhenGettingPref()
            throws Exception
    {
        GetUserPreferencesReply reply = service.getUserPreferences(
                BaseUtil.toPB(new DID(UniqueID.generate()))).get();
        assertTrue(reply.getDeviceName().isEmpty());
    }

    private void assertTrimmed(String str)
    {
        assertEquals(str.trim(), str);
    }
}
