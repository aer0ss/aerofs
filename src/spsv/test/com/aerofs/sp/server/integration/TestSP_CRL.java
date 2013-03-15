/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

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

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;

/**
 * A class to test certificate revocation list operations on the SP (revocation of certificates,
 * access to the revocation list, etc.).
 */
public class TestSP_CRL extends AbstractSPCertificateBasedTest
{
    //
    // Utilities
    //

    /**
     * Utility to set up one device and thus create a single entry in the certificate table.
     */
    @Before
    public void setupTestSP_CRL()
        throws Exception
    {
        String cert = service.registerDevice(_did.toPB(), newCSR(TEST_1_USER, _did), false, "", "", "")
                .get().getCert();

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

    //
    // Tests for revokeDeviceCertificate()
    //

    @Test
    public void shouldUnlinkDeviceEvenWhenCertificateDoesNotExist()
            throws Exception
    {
        DID did = DID.generate();
        Device device = factDevice.create(did);

        // Create a device without a certificate.
        sqlTrans.begin();
        device.save(factUser.create(USER_1), "Linux", "Ubuntu", "Ubuntu Test Device");
        sqlTrans.commit();

        // Unlinking should still work.
        setSessionUser(USER_1);
        service.unlinkDevice(did.toPB(), false);
    }

    /**
     * Simple test to ensure that when we revoke an existing, valid device certificate,
     * it is indeed revoked successfully.
     */
    @Test
    public void shouldRevokeDeviceCertificateSuccessfully()
            throws Exception
    {
        // Follow a typical certify-revoke cycle.
        service.unlinkDevice(_did.toPB(), false);

        // Verify that only one certificate has been revoked, as expected.
        GetCRLReply reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUnlinkDeviceCertificateMoreThanOnce()
            throws Exception
    {
        service.unlinkDevice(_did.toPB(), false);
        service.unlinkDevice(_did.toPB(), false);
    }

    @Test(expected = ExNotFound.class)
    public void shouldNotRevokeDeviceCertificateWhenDeviceDoesNotExist()
            throws Exception
    {
        // Try to revoke the certificate without first certifying the device
        service.unlinkDevice(new DID(UniqueID.generate()).toPB(), false);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldNotRevokeDeviceCertificateWhenWrongOwner()
            throws Exception
    {
        // Switch to a different user and try to revoke the previous user's device.
        setSessionUser(TEST_2_USER);
        service.unlinkDevice(_did.toPB(), false);
    }

    //
    // Tests for getCRL()
    //

    @Test
    public void shouldGetFullCRLSuccessfullyAfterOneRevocation()
            throws Exception
    {
        GetCRLReply reply;

        // Initially the list is empty...
        reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 0);

        service.unlinkDevice(_did.toPB(), false);

        // And after one revocation, the list will be of length 1.
        reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
    }

    //
    // Tests for getUserCRL()
    //

    @Ignore
    @Test
    public void shouldGetUserCRLSuccessfully()
    {
        // TODO (MP) finish this test...
    }
}
