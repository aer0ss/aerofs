/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import org.junit.Test;
import sun.security.pkcs.PKCS10;

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
        cert = service.registerDevice(_did.toPB(), newCSR(_did), false, "", "", "").get().getCert();

        sqlTrans.begin();
        assertTrue(ddb.hasDevice(_did));
        sqlTrans.commit();

        verify(certgen).generateCertificate(eq(TEST_1_USER), eq(_did), any(PKCS10.class));

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
        ByteString csr = newCSR(UserID.fromInternal("garbage"), _did);
        service.registerDevice(_did.toPB(), csr, false, "", "", "").get().getCert();
    }

    @Test
    public void shouldCreateCertificateForTwoDevicesWithSameName()
        throws Exception
    {
        // Certify device1
        DID did1 = _did;
        String cert1;
        cert1 = service.registerDevice(did1.toPB(), newCSR(did1), false, "", "", "").get().getCert();
        assertTrue(cert1.equals(RETURNED_CERT));

        // Modify the certificate's serial number returned for device2.
        mockCertificateGeneratorAndIncrementSerialNumber();

        // Certify device2
        DID did2 = getNextDID(Sets.newHashSet(_did));
        String cert2;
        cert2 = service.registerDevice(did2.toPB(), newCSR(did2),false, "", "", "").get().getCert();
        assertTrue(cert2.equals(RETURNED_CERT));
    }

    @Test(expected = ExDeviceIDAlreadyExists.class)
    public void shouldThrowIfDeviceIDAlreadyExists()
            throws Exception
    {
        DID did = new DID(UniqueID.generate());
        service.registerDevice(did.toPB(), newCSR(did), false, "", "", "");
        service.registerDevice(did.toPB(), newCSR(did), false, "", "", "");
    }

    private ByteString newCSR(DID did)
            throws IOException, GeneralSecurityException
    {
        return newCSR(TEST_1_USER, did);
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
