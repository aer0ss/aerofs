/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.base.id.UniqueID;
import com.aerofs.sp.server.lib.device.Device;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * A class to test the business logic in the certify device sp.proto RPC.
 */
public class TestSP_RegisterDevice extends AbstractSPCertificateBasedTest
{
    /**
     * Test that the certificate is successfully created when all params supplied are correct.
     */
    @Test
    public void shouldAddDeviceAndCreateCertificate()
            throws Exception
    {
        String cert;
        cert = service.registerDevice(device.id().toPB(), newCSR(device), "", "", "").get().getCert();

        sqlTrans.begin();
        assertTrue(device.exists());
        sqlTrans.commit();

        verify(certgen).generateCertificate(eq(TEST_1_USER.id()), eq(device.id()),
                any(PKCS10CertificationRequest.class));

        // Make sure the cert is valid (what we expect).
        assertTrue(cert.equals(RETURNED_CERT));
    }

    /**
     * Test that the certificate is not created when the user field is incorrect and thus the
     * cname does not match what we expect.
     */
    @Test(expected = ExBadArgs.class)
    public void shouldNotCreateCertificateWhenCNameDoesNotMatch()
            throws Exception
    {
        // Provide the incorrect user, and clean up after the uncommitted transaction.
        ByteString csr = newCSR(factUser.createFromExternalID("garbage"), device);
        service.registerDevice(device.id().toPB(), csr, "", "", "").get().getCert();
    }

    @Test
    public void shouldCreateCertificateForTwoDevicesWithSameName()
        throws Exception
    {
        // Certify device1
        Device device1 = device;
        String cert1;
        cert1 = service.registerDevice(device1.id().toPB(), newCSR(device1), "", "", "")
                .get().getCert();
        assertTrue(cert1.equals(RETURNED_CERT));

        // Modify the certificate's serial number returned for device2.
        mockCertificateGeneratorAndIncrementSerialNumber();

        // Certify device2
        Device device2 = factDevice.create(getNextDID(Sets.<DID>newHashSet(device.id())));
        String cert2;
        cert2 = service.registerDevice(device2.id().toPB(), newCSR(device2), "", "", "")
                .get().getCert();
        assertTrue(cert2.equals(RETURNED_CERT));
    }

    @Test(expected = ExDeviceIDAlreadyExists.class)
    public void shouldThrowIfDeviceIDAlreadyExists()
            throws Exception
    {
        Device device = factDevice.create(new DID(UniqueID.generate()));
        service.registerDevice(device.id().toPB(), newCSR(device), "", "", "");
        service.registerDevice(device.id().toPB(), newCSR(device), "", "", "");
    }

    private ByteString newCSR(Device device)
            throws IOException, GeneralSecurityException
    {
        return newCSR(TEST_1_USER, device);
    }

    /**
     * @param dids Set of DIDs that we want to skip.
     * @return a new DID not part of the set {@code dids}
     */
    private DID getNextDID(Set<DID> dids)
    {
        DID did = new DID(UniqueID.generate());
        while (dids.contains(did)) {
            did = new DID(UniqueID.generate());
        }
        return did;
    }
}
