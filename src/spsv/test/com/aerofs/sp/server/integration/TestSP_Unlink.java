/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.base.id.DID;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Common.Void;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.testng.Assert;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;

/**
 * A class to test unlinking operations on SP.
 */
public class TestSP_Unlink extends AbstractSPCertificateBasedTest
{
    @Before
    public void setupTestSP_Unlink()
        throws Exception
    {
        String cert = service.registerDevice(device.id().toPB(), newCSR(TEST_1_USER, device),
                false, "", "", "").get().getCert();

        assertTrue(cert.equals(RETURNED_CERT));

        mockAndCaptureVerkehrDeliverPayload();
    }

    /**
     * Short-circuit the verkehr admin; make sure it always passes.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setupVerkehrToCommandSuccessfully()
    {
        when(verkehrAdmin.updateCRL(any(ImmutableList.class)))
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
        setSessionUser(USER_1);
        service.unlinkDevice(did.toPB(), false);
    }

    @Test
    public void shouldUnlinkSuccessfully()
            throws Exception
    {
        // Follow a typical certify-revoke cycle.
        service.unlinkDevice(device.id().toPB(), false);

        // Verify that only one certificate has been revoked, as expected.
        GetCRLReply reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
    }

    @Test
    public void shouldNotThrowIfUnlinkDeviceCertificateMoreThanOnce()
            throws Exception
    {
        service.unlinkDevice(device.id().toPB(), false);
        service.unlinkDevice(device.id().toPB(), false);
    }

    @Test(expected = ExNotFound.class)
    public void shouldNotUnlinkWhenDeviceDoesNotExist()
            throws Exception
    {
        // Try to revoke the certificate without first certifying the device
        service.unlinkDevice(new DID(UniqueID.generate()).toPB(), false);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldNotUnlinkWhenWrongOwner()
            throws Exception
    {
        // Switch to a different user and try to revoke the previous user's device.
        setSessionUser(TEST_2_USER);
        service.unlinkDevice(device.id().toPB(), false);
    }

    @Test
    public void shouldStillGetDeviceInfoAfterUnlinking()
            throws Exception
    {
        // Verify we can get device info before unlinking.
        GetDeviceInfoReply reply =
                service.getDeviceInfo(Collections.nCopies(1, device.id().toPB())).get();
        Assert.assertEquals(1, reply.getDeviceInfoCount());
        Assert.assertEquals(true, reply.getDeviceInfoList().get(0).hasDeviceName());

        service.unlinkDevice(device.id().toPB(), false);

        // Verify we can get device info after unlinking.
        reply = service.getDeviceInfo(Collections.nCopies(1, device.id().toPB())).get();
        Assert.assertEquals(1, reply.getDeviceInfoCount());
        Assert.assertEquals(true, reply.getDeviceInfoList().get(0).hasDeviceName());
    }

    @Test
    public void shouldGetFullCRLSuccessfullyAfterOneRevocation()
            throws Exception
    {
        GetCRLReply reply;

        // Initially the list is empty...
        reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 0);

        service.unlinkDevice(device.id().toPB(), false);

        // And after one revocation, the list will be of length 1.
        reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
    }
}
