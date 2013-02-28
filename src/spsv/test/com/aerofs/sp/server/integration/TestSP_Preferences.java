/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import org.junit.Before;
import org.junit.Test;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;

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
        mockAndCaptureVerkehrDeliverPayload();
        setSessionUser(USER_1);
    }

    @Test
    public void shouldTrimUserAndDeviceNames() throws Exception
    {
        sqlTrans.begin();
        ddb.insertDevice(_did, USER_1, "", "", "name");
        sqlTrans.commit();

        service.setUserPreferences(sessionUser.get().id().getString(),
                "   first ", " last   ", _did.toPB(), "  device names  ").get();
        GetUserPreferencesReply reply = service.getUserPreferences(_did.toPB()).get();

        assertTrimmed(reply.getDeviceName());
        assertTrimmed(reply.getFirstName());
        assertTrimmed(reply.getLastName());
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfDeviceDoesntExistWhenSettingName()
            throws Exception
    {
        service.setUserPreferences(sessionUser.get().id().getString(),
                "first", "last", _did.toPB(), "device").get();
    }

    @Test
    public void shouldReturnEmptyNameIfDeviceDoesntExistWhenGettingPref()
            throws Exception
    {
        GetUserPreferencesReply reply = service.getUserPreferences(_did.toPB()).get();
        assertTrue(reply.getDeviceName().isEmpty());
    }

    private void assertTrimmed(String str)
    {
        assertEquals(str.trim(), str);
    }
}