/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import sun.security.pkcs.PKCS10;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

public class TestSP_CertifyTeamServerCertificate extends AbstractSPTest
{
    DID tsDID = new DID(UniqueID.generate());

    @Captor ArgumentCaptor<UserID> capUserID;

    User user;

    @Before
    public void setup()
            throws Exception
    {
        setSessionUser(USER_1);
        user = sessionUser.get();
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNotAdminOfDefaultOrg()
            throws Exception
    {
        // make sure the user is setup properly
        sqlTrans.begin();
        assertTrue(user.getOrganization().isDefault());
        assertFalse(user.getLevel().covers(AuthorizationLevel.ADMIN));
        sqlTrans.commit();

        certifyTeamServerDevice(OrganizationID.DEFAULT.toTeamServerUserID());
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNotAdminOfNonDefaultOrg()
            throws Exception
    {
        // this moves the user to a new organization
        UserID tsUserID = setupTeamServer();

        sqlTrans.begin();
        assertFalse(user.getOrganization().isDefault());
        user.setLevel(AuthorizationLevel.USER);
        sqlTrans.commit();

        certifyTeamServerDevice(tsUserID);
    }

    @Test
    public void shouldCreateTeamServerUser()
            throws Exception
    {
        UserID tsUserID = setupTeamServer();
        certifyTeamServerDevice(tsUserID);

        // Can't conveniently use verify() since udb.insertUser() may be called many times during test
        // initialization.
        sqlTrans.begin();
        assertTrue(udb.hasUser(tsUserID));
        sqlTrans.commit();
    }

    @Test
    public void shouldAddAndCertifyDevice()
            throws Exception
    {
        UserID tsUserID = setupTeamServer();
        certifyTeamServerDevice(tsUserID);

        verify(certgen).generateCertificate(eq(tsUserID), eq(tsDID), any(PKCS10.class));

        // Can't conveniently use verify() since ddb.insertDevice() may be called many times during
        // test initialization.
        sqlTrans.begin();
        assertTrue(ddb.hasDevice(tsDID));
        sqlTrans.commit();
    }

    private void certifyTeamServerDevice(UserID userID)
            throws Exception
    {
        mockCertificateGeneratorAndIncrementSerialNumber();

        service.certifyTeamServerDevice(tsDID.toPB(), newCSR(userID, tsDID));
    }

    private UserID setupTeamServer()
            throws Exception
    {
        service.addOrganization("An Awesome Team", null, StripeCustomerID.TEST.getID());
        return UserID.fromInternal(service.getTeamServerUserID().get().getId());
    }
}
