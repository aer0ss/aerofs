/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.sp.server.lib.SPDatabase.UserDevice;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * A class to test whether the get interested devices call works as expected.
 */
public class TestSP_GetInterestedDevices extends AbstractSPFolderPermissionTest
{
    private static final SID TEST_SID_1 = SID.generate();

    // Devices that we will test with.
    private static final DID _deviceA1 = new DID(UniqueID.generate());
    private static final DID _deviceA2 = new DID(UniqueID.generate());

    private static final DID _deviceB1 = new DID(UniqueID.generate());

    private static final DID _deviceC1 = new DID(UniqueID.generate());

    /**
     * Add a few devices to the device table. Similar to the setup for the get device info test.
     */
    @Before
    public void setupDevices()
        throws Exception
    {
        // Before we proceed make sure verkehr is set up to publish successfully (for ACLs).
        mockAndCaptureVerkehrPublish();

        trans.begin();

        // User 1
        ddb.insertDevice(_deviceA1, USER_1, "Device A1");
        ddb.insertDevice(_deviceA2, USER_1, "Device A2");

        // User 2
        ddb.insertDevice(_deviceB1, USER_2, "Device B1");

        // User 3
        ddb.insertDevice(_deviceC1, USER_3, "Device C2");

        trans.commit();

        setSessionUser(USER_1);
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
    }

    /**
     * Simple test to verify that the get interested devices call is working.
     */
    @Test
    public void shouldGetCorrectSetOfInterestedDevices()
        throws Exception
    {
        trans.begin();
        Set<UserDevice> interested = db.getInterestedDevicesSet(TEST_SID_1, sessionUser.get().id());
        trans.commit();

        // The size is correct (only the correct devices were returned).
        assertEquals(3, interested.size());
    }
}
