/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * A class to test the business logic in the device cert refresh SP call
 */
public class TestSP_RefreshCertificate extends AbstractSPCertificateBasedTest
{
    @Test
    public void shouldRecertifyClient() throws Exception
    {
        sqlTrans.begin();
        Device dev = saveDevice(TEST_1_USER);
        sqlTrans.commit();

        mockCertificateAuthenticatorSetAuthenticatedState(TEST_1_USER, dev);

        String cert = service.recertifyDevice(BaseUtil.toPB(dev.id()), newCSR(TEST_1_USER, dev))
                .get()
                .getCert();

        verify(certgen).generateCertificate(eq(TEST_1_USER.id()), eq(dev.id()),
                any(PKCS10CertificationRequest.class));

        // Make sure the cert is valid (what we expect).
        assertTrue(cert.equals(RETURNED_CERT));
    }

    @Test
    public void shouldRecertifyTeamServer() throws Exception
    {
        sqlTrans.begin();
        User tsUser = TEST_1_USER.getOrganization().getTeamServerUser();
        tsUser.setLevel(AuthorizationLevel.ADMIN);
        Device dev = saveDevice(tsUser);
        sqlTrans.commit();

        mockCertificateAuthenticatorSetAuthenticatedState(tsUser, dev);

        String cert = service.recertifyTeamServerDevice(BaseUtil.toPB(dev.id()), newCSR(tsUser, dev))
                .get()
                .getCert();

        verify(certgen).generateCertificate(eq(tsUser.id()), eq(dev.id()),
                any(PKCS10CertificationRequest.class));

        // Make sure the cert is valid (what we expect).
        assertTrue(cert.equals(RETURNED_CERT));
    }

    /**
     * Test that the certificate is not created when the user field is incorrect and thus the
     * cname does not match what we expect.
     */
    @Test(expected = ExBadArgs.class)
    public void shouldNotCreateCertificateWhenDeviceDoesNotMatch() throws Exception
    {
        sqlTrans.begin();
        Device dev = saveDevice(TEST_1_USER);
        Device dev2 = saveDevice(TEST_1_USER);
        sqlTrans.commit();

        mockCertificateAuthenticatorSetAuthenticatedState(TEST_1_USER, dev);

        service.recertifyDevice(BaseUtil.toPB(dev2.id()), newCSR(TEST_1_USER, dev));
    }
}
