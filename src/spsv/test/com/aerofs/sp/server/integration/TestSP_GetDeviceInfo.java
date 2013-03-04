/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A class to test the get device info SP call.
 */
public class TestSP_GetDeviceInfo extends AbstractSPFolderPermissionTest
{
    private static final SID TEST_SID_1 = SID.generate();

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
        mockAndCaptureVerkehrPublish();
        mockAndCaptureVerkehrDeliverPayload();

        sqlTrans.begin();

        // User 1
        ddb.insertDevice(new DID(UniqueID.generate()), USER_1, "", "", "Device A01");
        ddb.insertDevice(new DID(UniqueID.generate()), USER_1, "", "", "Device A02");
        ddb.insertDevice(new DID(UniqueID.generate()), USER_1, "", "", "Device A03");

        // User 2
        _deviceB01 = new DID(UniqueID.generate());
        ddb.insertDevice(_deviceB01, USER_2, "", "", "Device B01");
        ddb.insertDevice(new DID(UniqueID.generate()), USER_2, "", "", "Device B02");

        // User 3
        _deviceC01 = new DID(UniqueID.generate());
        ddb.insertDevice(_deviceC01, USER_3, "", "", "Device C01");
        ddb.insertDevice(new DID(UniqueID.generate()), USER_3, "", "", "Device C02");

        sqlTrans.commit();

        // User 1 shares with User 2, but not with User 3
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
    }

    /**
     * Verify that User 1 can resolve User 2's devices, i.e. you can resolve devices when you do
     * in fact share with another user.
     */
    @Test
    public void shouldSucceedWhenUsersShareFiles()
            throws Exception
    {
        GetDeviceInfoReply reply = service.getDeviceInfo(ImmutableList.of(_deviceB01.toPB())).get();

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
        assertEquals(deviceInfo.getOwner().getUserEmail(), USER_2.getString());
        assertEquals(deviceInfo.getOwner().getFirstName(), USER_2.getString());
        assertEquals(deviceInfo.getOwner().getLastName(), USER_2.getString());
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
