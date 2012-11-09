/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.sp.server.lib.SPDatabase.DeviceRow;
import org.junit.Test;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Sp.GetPreferencesReply;

import static org.junit.Assert.assertEquals;

/**
 * A class to test saving and fetching preferences
 */
public class TestSPPreferences extends AbstractSPUserBasedTest
{
    private final DID _did = new DID(UniqueID.generate());

    @Test
    public void shouldTrimUserAndDeviceNames() throws Exception
    {
        // Set the session user before we try to set preferences.
        sessionUser.setUser(TEST_USER_1_NAME);
        _transaction.begin();
        db.addDevice(new DeviceRow(_did, "name", TEST_USER_1_NAME));
        _transaction.commit();
        service.setPreferences("   first ", " last   ", _did.toPB(), "  device names  ").get();
        GetPreferencesReply reply = service.getPreferences(_did.toPB()).get();

        assertTrimmed(reply.getDeviceName());
        assertTrimmed(reply.getFirstName());
        assertTrimmed(reply.getLastName());
    }

    private void assertTrimmed(String str)
    {
        assertEquals(str.trim(), str);
    }
}