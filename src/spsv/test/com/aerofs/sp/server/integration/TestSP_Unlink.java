/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.sp.server.lib.device.Device;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

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

    /**
     * Short-circuit the verkehr admin; make sure it always passes.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setupVerkehrToCommandSuccessfully()
    {
        when(verkehrClient.revokeSerials(any(ImmutableList.class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));
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

        // Verify that only one certificate has been revoked, as expected.
        GetCRLReply reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
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
        GetCRLReply reply;

        // Initially the list is empty...
        reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 0);

        service.unlinkDevice(BaseUtil.toPB(device.id()), false);

        // And after one revocation, the list will be of length 1.
        reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
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
