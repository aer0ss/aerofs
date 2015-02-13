/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.User;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

public class TestSP_RegisterTeamServerDevice extends AbstractSPTest
{
    Device tsDevice = factDevice.create(new DID(UniqueID.generate()));

    @Captor ArgumentCaptor<UserID> capUserID;

    User user;
    User tsUser;

    @Before
    public void setup()
            throws Exception
    {
        setSession(USER_1);
        user = USER_1;

        sqlTrans.begin();
        tsUser = user.getOrganization().getTeamServerUser();
        sqlTrans.commit();
    }

    @Test
    public void shouldAddAndCertifyDevice()
            throws Exception
    {
        registerTeamServerDevice();

        verify(certgen).generateCertificate(eq(tsUser.id()), eq(tsDevice.id()),
                any(PKCS10CertificationRequest.class));

        // Can't conveniently use verify() since ddb.insertDevice() may be called many times during
        // test initialization.
        sqlTrans.begin();
        assertTrue(tsDevice.exists());
        sqlTrans.commit();
    }

    private void registerTeamServerDevice()
            throws Exception
    {
        mockCertificateGeneratorAndIncrementSerialNumber();

        service.registerTeamServerDevice(
                BaseUtil.toPB(tsDevice.id()), newCSR(tsUser, tsDevice), "", "", "", null);
    }
}
