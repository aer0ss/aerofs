/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.sp.server.lib.device.Device;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A class to test unlinking operations on SP.
 */
public class TestSP_Unlink extends AbstractSPCertificateBasedTest
{
    @Before
    public void setupTestSP_Unlink()
        throws Exception
    {
        String cert = service.registerDevice(BaseUtil.toPB(device.id()), newCSR(TEST_1_USER, device),
                "", "", "", null).get().getCert();

        assertTrue(cert.equals(RETURNED_CERT));
    }

    @Test
    public void shouldUnlinkDeviceEvenWhenCertificateDoesNotExist()
            throws Exception
    {
        DID did = DID.generate();
        Device device = factDevice.create(did);

        // Create a device without a certificate.
        sqlTrans.begin();
        device.save(USER_1, "Linux", "Ubuntu", "Ubuntu Test Device");
        sqlTrans.commit();

        // Unlinking should still work.
        setSession(USER_1);
        service.unlinkDevice(BaseUtil.toPB(did), false);
    }

    @Test
    public void shouldUnlinkSuccessfully()
            throws Exception
    {
        // Follow a typical certify-revoke cycle.
        service.unlinkDevice(BaseUtil.toPB(device.id()), false);

        // TODO: verify device revocation, once enforced
    }

    @Test
    public void shouldNotThrowIfUnlinkDeviceCertificateMoreThanOnce()
            throws Exception
    {
        service.unlinkDevice(BaseUtil.toPB(device.id()), false);
        service.unlinkDevice(BaseUtil.toPB(device.id()), false);
    }

    @Test(expected = ExNotFound.class)
    public void shouldNotUnlinkWhenDeviceDoesNotExist()
            throws Exception
    {
        // Try to revoke the certificate without first certifying the device
        service.unlinkDevice(BaseUtil.toPB(new DID(UniqueID.generate())), false);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldNotUnlinkWhenWrongOwner()
            throws Exception
    {
        // Switch to a different user and try to revoke the previous user's device.
        setSession(TEST_2_USER);
        service.unlinkDevice(BaseUtil.toPB(device.id()), false);
    }

    @Test
    public void shouldStillGetDeviceInfoAfterUnlinking()
            throws Exception
    {
        // Verify we can get device info before unlinking.
        GetDeviceInfoReply reply =
                service.getDeviceInfo(Collections.nCopies(1, BaseUtil.toPB(device.id()))).get();
        assertEquals(1, reply.getDeviceInfoCount());
        assertEquals(true, reply.getDeviceInfoList().get(0).hasDeviceName());

        service.unlinkDevice(BaseUtil.toPB(device.id()), false);

        // Verify we can get device info after unlinking.
        reply = service.getDeviceInfo(Collections.nCopies(1, BaseUtil.toPB(device.id()))).get();
        assertEquals(1, reply.getDeviceInfoCount());
        assertEquals(true, reply.getDeviceInfoList().get(0).hasDeviceName());
    }

    @Test
    public void shouldGetFullCRLSuccessfullyAfterOneRevocation()
            throws Exception
    {
        // TODO: verify device revocation, once implemented

        service.unlinkDevice(BaseUtil.toPB(device.id()), false);
    }

    @Test
    public void getPeerDevicesShouldNotReturnUnlinkedDevice()
            throws Exception
    {
        Collection<Device> devices;

        sqlTrans.begin();
        devices = TEST_1_USER.getPeerDevices();
        sqlTrans.commit();
        assertEquals(1, devices.size());

        service.unlinkDevice(BaseUtil.toPB(device.id()), false);

        sqlTrans.begin();
        devices = TEST_1_USER.getPeerDevices();
        sqlTrans.commit();
        assertEquals(0, devices.size());
    }

    @Test
    public void getDevicesShouldNotReturnUnlinkedDevice()
            throws Exception
    {
        Collection<Device> devices;

        sqlTrans.begin();
        devices = TEST_1_USER.getDevices();
        sqlTrans.commit();
        assertEquals(1, devices.size());

        service.unlinkDevice(BaseUtil.toPB(device.id()), false);

        sqlTrans.begin();
        devices = TEST_1_USER.getDevices();
        sqlTrans.commit();
        assertEquals(0, devices.size());
    }
}
