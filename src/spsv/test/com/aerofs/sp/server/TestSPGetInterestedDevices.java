/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.sp.server.lib.SPDatabase.UserDevice;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * A class to test whether the get interested devices call works as expected.
 */
public class TestSPGetInterestedDevices extends AbstractSPServiceTest
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

        transaction.begin();

        // User 1
        ddb.addDevice(_deviceA1, TEST_USER_1, "Device A1");
        ddb.addDevice(_deviceA2, TEST_USER_1, "Device A2");

        // User 2
        ddb.addDevice(_deviceB1, TEST_USER_2, "Device B1");

        // User 3
        ddb.addDevice(_deviceC1, TEST_USER_3, "Device C2");

        transaction.commit();

        // User 1 shares with User 2, but not with User 3
        ArrayList<PBSubjectRolePair> pair = new ArrayList<PBSubjectRolePair>();

        setSessionUser(TEST_USER_1);
        pair.add(new SubjectRolePair(TEST_USER_1, Role.OWNER).toPB());
        pair.add(new SubjectRolePair(TEST_USER_2, Role.EDITOR).toPB());

        service.shareFolder(TEST_SID_1.toString(), TEST_SID_1.toPB(), pair, "").get();
    }

    /**
     * Simple test to verify that the get interested devices call is working.
     */
    @Test
    public void shouldGetCorrectSetOfInterestedDevices()
        throws Exception
    {
        transaction.begin();
        Set<UserDevice> interested = db.getInterestedDevicesSet(TEST_SID_1.getBytes(),
                sessionUser.getID());
        transaction.commit();

        // The size is correct (only the correct devices were returned).
        assertEquals(3, interested.size());
    }
}
