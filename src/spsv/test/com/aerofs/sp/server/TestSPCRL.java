/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Common.Void;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
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
public class TestSPCRL extends AbstractSPCertificateBasedTest
{
    //
    // Utilities
    //

    /**
     * Utility to set up one device and thus create a single entry in the certificate table.
     */
    @Before
    public void setupTestDevice()
        throws Exception
    {
        byte[] csr = SecUtil.newCSR(_publicKey, _privateKey, TEST_1_USER, _did).getEncoded();

        String cert;
        cert = service.certifyDevice(_did.toPB(), ByteString.copyFrom(csr),
                false).get().getCert();

        assertTrue(cert.equals(RETURNED_CERT));
    }

    /**
     * Short-circuit the verkehr admin; make sure it always passes.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setupVerkehrToCommandSuccessfully()
    {
        when(_verkehrAdmin.updateCRL_(any(ImmutableList.class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));
    }

    //
    // Tests for revokeDeviceCertificate()
    //

    /**
     * Simple test to ensure that when we revoke an existing, valid device certificate,
     * it is indeed revoked successfully.
     */
    @Test
    public void shouldRevokeDeviceCertificateSuccessfully()
            throws Exception
    {
        // Follow a typical certify-revoke cycle. Revocation on the same device should not fail.
        // It doesn't do anything, but it still doesn't fail.
        service.revokeDeviceCertificate(_did.toPB());
        service.revokeDeviceCertificate(_did.toPB());

        // Verify that only one certificate has been revoked, as expected.
        GetCRLReply reply = service.getCRL().get();
        assertTrue(reply.getSerialList().size() == 1);
        assertTrue(reply.getSerialList().get(0) == getLastSerialNumber());
    }

    @Test(expected = ExNotFound.class)
    public void shouldNotRevokeDeviceCertificateWhenDeviceDoesNotExist()
            throws Exception
    {
        // Try to revoke the certificate without first certifying the device, and make sure to clean
        // up after uncommitted transactions.
        try {
            service.revokeDeviceCertificate(new DID(UniqueID.generate()).toPB());
        } catch (Exception e) {
            _transaction.handleException();
            throw e;
        }
    }

    @Test(expected = ExNoPerm.class)
    public void shouldNotRevokeDeviceCertificateWhenWrongOwner()
            throws Exception
    {
        // Switch to a different user and try to revoke the previous user's device.
        try {
            sessionUser.setID(TEST_2_USER);
            service.revokeDeviceCertificate(_did.toPB());
        } catch (Exception e) {
            _transaction.handleException();
            throw e;
        }
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

        service.revokeUserCertificates();

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