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
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.sp.server.sp.SPDatabase.DeviceRow;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A class to test the get device info SP call.
 */
public class TestSPGetDeviceInfo extends AbstractSPUserBasedTest
{
    private static final SID TEST_SID_1 = new SID(UniqueID.generate());

    // Arbitrarily test with these devices.
    private DID _deviceB01;
    private DID _deviceC01;

    /**
     * Add a few devices to the device table.
     */
    @Before
    public void setupDevices()
        throws Exception
    {
        // Before we proceed make sure verkehr is set up to publish successfully (for ACLs).
        setupMockVerkehrToSuccessfullyPublish();

        // User 1
        db.addDevice(new DeviceRow(new DID(UniqueID.generate()), "Device A01", TEST_USER_1_NAME));
        db.addDevice(new DeviceRow(new DID(UniqueID.generate()), "Device A02", TEST_USER_1_NAME));
        db.addDevice(new DeviceRow(new DID(UniqueID.generate()), "Device A03", TEST_USER_1_NAME));

        // User 2
        _deviceB01 = new DID(UniqueID.generate());
        db.addDevice(new DeviceRow(_deviceB01, "Device B01", TEST_USER_2_NAME));
        db.addDevice(new DeviceRow(new DID(UniqueID.generate()), "Device B02", TEST_USER_2_NAME));

        // User 3
        _deviceC01 = new DID(UniqueID.generate());
        db.addDevice(new DeviceRow(_deviceC01, "Device C01", TEST_USER_3_NAME));
        db.addDevice(new DeviceRow(new DID(UniqueID.generate()), "Device C02", TEST_USER_3_NAME));

        // User 1 shares with User 2, but not with User 3
        ArrayList<PBSubjectRolePair> pair = new ArrayList<PBSubjectRolePair>();

        sessionUser.setUser(TEST_USER_1_NAME);
        pair.add(new SubjectRolePair(TEST_USER_1_NAME, Role.OWNER).toPB());
        pair.add(new SubjectRolePair(TEST_USER_2_NAME, Role.EDITOR).toPB());

        service.setACL(TEST_SID_1.toPB(), pair).get();
    }

    /**
     * Verify that User 1 can resolve User 2's devices, i.e. you can resolve devices when you do
     * in fact share with another user.
     */
    @Test
    public void shouldSucceedWhenUsersShareFiles()
            throws Exception
    {
        // Verify that User 1 can resolve User 2's devices.
        LinkedList<ByteString> dids = new LinkedList<ByteString>();
        dids.add(_deviceB01.toPB());
        GetDeviceInfoReply reply = service.getDeviceInfo(dids).get();

        List<PBDeviceInfo> deviceInfoList = reply.getDeviceInfoList();
        assertEquals(deviceInfoList.size(), 1);

        PBDeviceInfo deviceInfo = deviceInfoList.get(0);
        verifyShouldSucceedWhenUsersShareFiles(deviceInfo);
    }

    private void verifyShouldSucceedWhenUsersShareFiles(PBDeviceInfo deviceInfo)
            throws Exception
    {
        assertTrue(deviceInfo.hasDeviceName());
        assertEquals(deviceInfo.getDeviceName(), "Device B01");
        assertTrue(deviceInfo.hasOwner());

        // The parent sets the first name and the last name to just be the test user name.
        assertEquals(deviceInfo.getOwner().getUserEmail(), TEST_USER_2_NAME);
        assertEquals(deviceInfo.getOwner().getFirstName(), TEST_USER_2_NAME);
        assertEquals(deviceInfo.getOwner().getLastName(), TEST_USER_2_NAME);
    }

    /**
     * Verify that User 1 can't resolve User 3's devices, i.e. you cannot resolve devices when you
     * not share with that user.
     */
    @Test
    public void shouldNotSucceedWhenUsersDoNotShareFiles()
            throws Exception
    {
        LinkedList<ByteString> dids = new LinkedList<ByteString>();
        dids.add(_deviceC01.toPB());
        GetDeviceInfoReply reply = service.getDeviceInfo(dids).get();

        List<PBDeviceInfo> deviceInfoList = reply.getDeviceInfoList();
        assertEquals(deviceInfoList.size(), 1);

        PBDeviceInfo deviceInfo = deviceInfoList.get(0);
        verifyShouldNotSucceedWhenUsersDoNotShareFiles(deviceInfo);
    }

    private void verifyShouldNotSucceedWhenUsersDoNotShareFiles(PBDeviceInfo deviceInfo)
            throws Exception
    {
        assertFalse(deviceInfo.hasOwner());
        assertFalse(deviceInfo.hasDeviceName());
    }

    /**
     * Pretty much the same as the above tests, except make sure we can handle a list of dids.
     */
    @Test
    public void shouldHandleRepeatedDidBytesInOneCall()
        throws Exception
    {
        LinkedList<ByteString> dids = new LinkedList<ByteString>();
        dids.add(_deviceB01.toPB());
        dids.add(_deviceC01.toPB());
        GetDeviceInfoReply reply = service.getDeviceInfo(dids).get();

        List<PBDeviceInfo> deviceInfoList = reply.getDeviceInfoList();
        assertEquals(deviceInfoList.size(), 2);

        verifyShouldSucceedWhenUsersShareFiles(deviceInfoList.get(0));
        verifyShouldNotSucceedWhenUsersDoNotShareFiles(deviceInfoList.get(1));
    }
}