/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A class to test the get device info SP call.
 */
public class TestSP_GetDeviceInfo extends AbstractSPFolderTest
{
    private static final SID TEST_SID_1 = SID.generate();

    // Arbitrarily test with these devices.
    private Device _deviceB01;
    private Device _deviceC01;

    /**
     * Add a few devices to the device table.
     */
    @Before
    public void setupDevices()
        throws Exception
    {
        sqlTrans.begin();

        // User 1
        saveDevice(USER_1, "Device A01");
        saveDevice(USER_1, "Device A02");
        saveDevice(USER_1, "Device A03");

        // User 2
        saveDevice(USER_1, "Device A01");

        _deviceB01 = saveDevice(USER_2, "Device B01");
        saveDevice(USER_2, "Device B02");

        // User 3
        _deviceC01 = saveDevice(USER_3, "Device C01");
        saveDevice(USER_3, "Device C02");

        sqlTrans.commit();

        // User 1 shares with User 2, but not with User 3
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Permissions.allOf(Permission.WRITE));
    }

    private Device saveDevice(User owner, String name)
            throws Exception
    {
        return factDevice.create(new DID(UniqueID.generate())).save(owner, "", "", name);
    }

    /**
     * Verify that User 1 can resolve User 2's devices, i.e. you can resolve devices when you do
     * in fact share with another user.
     */
    @Test
    public void shouldSucceedWhenUsersShareFiles()
            throws Exception
    {
        GetDeviceInfoReply reply = service.getDeviceInfo(ImmutableList.of(BaseUtil.toPB(_deviceB01.id()))).get();

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
        assertEquals(deviceInfo.getOwner().getUserEmail(), USER_2.id().getString());
        assertEquals(deviceInfo.getOwner().getFirstName(), USER_2.id().getString());
        assertEquals(deviceInfo.getOwner().getLastName(), USER_2.id().getString());
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
        dids.add(BaseUtil.toPB(_deviceC01.id()));
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
        dids.add(BaseUtil.toPB(_deviceB01.id()));
        dids.add(BaseUtil.toPB(_deviceC01.id()));
        GetDeviceInfoReply reply = service.getDeviceInfo(dids).get();

        List<PBDeviceInfo> deviceInfoList = reply.getDeviceInfoList();
        assertEquals(deviceInfoList.size(), 2);

        verifyShouldSucceedWhenUsersShareFiles(deviceInfoList.get(0));
        verifyShouldNotSucceedWhenUsersDoNotShareFiles(deviceInfoList.get(1));
    }

    @Test
    public void shouldReturnSameNumberOfDevicesWithInvalidDids()
        throws Exception
    {
        LinkedList<ByteString> dids = new LinkedList<ByteString>();
        dids.add(BaseUtil.toPB(_deviceB01.id()));
        dids.add(BaseUtil.toPB(new DID(UniqueID.generate())));
        GetDeviceInfoReply reply = service.getDeviceInfo(dids).get();

        assertEquals(reply.getDeviceInfoCount(), 2);
    }


}
