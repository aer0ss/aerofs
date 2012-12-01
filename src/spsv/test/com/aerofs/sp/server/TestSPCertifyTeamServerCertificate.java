/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.OrgID;
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

public class TestSPCertifyTeamServerCertificate extends AbstractSPServiceTest
{
    DID tsDID = new DID(UniqueID.generate());

    @Captor ArgumentCaptor<UserID> capUserID;

    User user;

    @Before
    public void setup()
            throws Exception
    {
        setSessionUser(TEST_USER_1);
        user = sessionUser.get();
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNotAdminOfDefaultOrg()
            throws Exception
    {
        // make sure the user is setup properly
        transaction.begin();
        assertTrue(user.getOrganization().isDefault());
        assertFalse(user.getLevel().covers(AuthorizationLevel.ADMIN));
        transaction.commit();

        certifyTeamServerDevice(OrgID.DEFAULT.toTeamServerUserID());
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNotAdminOfNonDefaultOrg()
            throws Exception
    {
        // this moves the user to a new organization
        UserID tsUserID = getTeamServerUserID();

        transaction.begin();
        assertFalse(user.getOrganization().isDefault());
        user.setLevel(AuthorizationLevel.USER);
        transaction.commit();

        certifyTeamServerDevice(tsUserID);
    }

    @Test
    public void shouldCreateTeamServerUser()
            throws Exception
    {
        UserID tsUserID = getTeamServerUserID();
        certifyTeamServerDevice(tsUserID);

        // Can't conveniently use verify() since udb.addUser() may be called many times during test
        // initialization.
        transaction.begin();
        assertTrue(udb.hasUser(tsUserID));
        transaction.commit();
    }

    @Test
    public void shouldAddAndCertifyDevice()
            throws Exception
    {
        UserID tsUserID = getTeamServerUserID();
        certifyTeamServerDevice(tsUserID);

        verify(certgen).createCertificate(eq(tsUserID), eq(tsDID), any(PKCS10.class));

        // Can't conveniently use verify() since ddb.addDevice() may be called many times during
        // test initialization.
        transaction.begin();
        assertTrue(ddb.hasDevice(tsDID));
        transaction.commit();
    }

    private void certifyTeamServerDevice(UserID userID)
            throws Exception
    {
        mockCertificateGeneratorAndIncrementSerialNumber();

        service.certifyTeamServerDevice(tsDID.toPB(), newCSR(userID, tsDID));
    }

    private UserID getTeamServerUserID()
            throws Exception
    {
        return UserID.fromInternal(service.getTeamServerUserID().get().getId());
    }
}
