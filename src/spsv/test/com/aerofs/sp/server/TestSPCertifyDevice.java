/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import static org.junit.Assert.assertTrue;

/**
 * A class to test the business logic in the certify device sp.proto RPC.
 */
public class TestSPCertifyDevice extends AbstractSPCertificateBasedTest
{
    /**
     * Test that the certificate is successfully created when all params supplied are correct.
     */
    @Test
    public void shouldCreateCertificateWhenAllParamsAreGood()
            throws Exception
    {
        // All params are correct (i.e. user/did match what is in the cname).
        byte[] csr = SecUtil.newCSR(_publicKey, _privateKey, TEST_1_USER, _did).getEncoded();

        String cert;
        cert = service.certifyDevice(_did.toPB(), ByteString.copyFrom(csr),false).get().getCert();

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
        byte[] csr = SecUtil.newCSR(_publicKey, _privateKey, UserID.fromInternal("garbage"), _did)
                .getEncoded();
        service.certifyDevice(_did.toPB(), ByteString.copyFrom(csr), false).get().getCert();
    }

    /**
     * Test that the certificate is not created when we try to recertify a device that doesn't
     * exist.
     */
    @Test(expected = ExNotFound.class)
    public void shouldNotCreateCertificateWhenRecertifyNonExistingDevice()
        throws Exception
    {
        byte[] csr = SecUtil.newCSR(_publicKey, _privateKey, TEST_1_USER, _did).getEncoded();
        service.certifyDevice(_did.toPB(), ByteString.copyFrom(csr), true).get().getCert();
    }

    /**
     * Test that the certificate is not created when we try to recertify a device that belongs to
     * someone else.
     */
    @Test(expected = ExNoPerm.class)
    public void shouldNotCreateCertificateWhenRecertifyByDifferentOwner()
        throws Exception
    {
        boolean noExceptionCaught = true;
        String cert = null;
        ByteString csr = newCSR(_did);

        // Need a try catch here, because without one if the first certifyDevice call fails with
        // ExNoPerm the test would still pass, which would be incorrect.
        try {
            // Create a cert and expect the creation to succeed.
            cert = service.certifyDevice(_did.toPB(), csr, false).get().getCert();
        }
        catch (ExNoPerm e) {
            noExceptionCaught = false;
        }

        assertTrue(noExceptionCaught);
        assertTrue(cert.equals(RETURNED_CERT));

        // Try to recertify using the wrong session user.
        setSessionUser(TEST_2_USER);
        service.certifyDevice(_did.toPB(), csr, true).get().getCert();
    }

    @Test
    public void shouldCreateCertificateForTwoDevicesWithSameName()
        throws Exception
    {
        // Certify device1
        DID did1 = _did;
        String cert1;
        cert1 = service.certifyDevice(did1.toPB(), newCSR(did1), false).get().getCert();
        assertTrue(cert1.equals(RETURNED_CERT));

        // Modify the certificate's serial number returned for device2.
        mockCertificateGeneratorAndIncrementSerialNumber();

        // Certify device2
        DID did2 = getNextDID(Sets.newHashSet(_did));
        String cert2;
        cert2 = service.certifyDevice(did2.toPB(), newCSR(did2),false).get().getCert();
        assertTrue(cert2.equals(RETURNED_CERT));
    }

    @Test(expected = ExDeviceIDAlreadyExists.class)
    public void shouldThrowIfDeviceIDAlreadyExists()
            throws Exception
    {
        DID did = new DID(UniqueID.generate());
        service.certifyDevice(did.toPB(), newCSR(did), false);
        service.certifyDevice(did.toPB(), newCSR(did), false);
    }

    private ByteString newCSR(DID did)
            throws IOException, GeneralSecurityException
    {
        return ByteString.copyFrom(
                SecUtil.newCSR(_publicKey, _privateKey, TEST_1_USER, did).getEncoded());
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
