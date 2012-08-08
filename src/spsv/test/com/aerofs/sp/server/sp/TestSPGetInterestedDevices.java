/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.lib.Role;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.servletlib.sp.SPDatabase.DeviceRow;
import com.aerofs.servletlib.sp.SPDatabase.UserDevice;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

/**
 * A class to test whether the get interested devices call works as expected.
 */
public class TestSPGetInterestedDevices extends AbstractSPUserBasedTest
{
    private static final SID TEST_SID_1 = new SID(UniqueID.generate());

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
        setupMockVerkehrToSuccessfullyPublish();

        // User 1
        db.addDevice(new DeviceRow(_deviceA1, "Device A1", TEST_USER_1_NAME));
        db.addDevice(new DeviceRow(_deviceA2, "Device A2", TEST_USER_1_NAME));

        // User 2
        db.addDevice(new DeviceRow(_deviceB1, "Device B1", TEST_USER_2_NAME));

        // User 3
        db.addDevice(new DeviceRow(_deviceC1, "Device C2", TEST_USER_3_NAME));

        // User 1 shares with User 2, but not with User 3
        ArrayList<PBSubjectRolePair> pair = new ArrayList<PBSubjectRolePair>();

        sessionUser.setUser(TEST_USER_1_NAME);
        pair.add(new SubjectRolePair(TEST_USER_1_NAME, Role.OWNER).toPB());
        pair.add(new SubjectRolePair(TEST_USER_2_NAME, Role.EDITOR).toPB());

        service.setACL(TEST_SID_1.toPB(), pair).get();
    }

    /**
     * Simple test to verify that the get interested devices call is working.
     */
    @Test
    public void shouldGetCorrectSetOfInterestedDevices()
        throws Exception
    {
        HashSet<UserDevice> interested = db.getInterestedDevicesSet(TEST_SID_1,
                sessionUser.getUser());

        // The size is correct (only the correct devices were returned).
        assertEquals(interested.size(), 3);
    }
}
