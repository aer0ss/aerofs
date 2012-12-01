/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;

public class TestSPGetTeamServerUserID extends AbstractSPServiceTest
{
    @Captor ArgumentCaptor<OrgID> capOrgID;

    User user;

    @Before
    public void setup()
            throws ExNoPerm
    {
        setSessionUser(TEST_USER_1);
        user = sessionUser.get();
    }

    @Test
    public void shouldCreateOrgIfUserIsInDefaultOrg()
            throws Exception
    {
        // make sure the user is set up properly
        transaction.begin();
        assertTrue(user.getOrganization().isDefault());
        transaction.commit();

        getTeamServerUserID();
        verify(odb).addOrganization(any(OrgID.class), anyString());
    }

    @Test
    public void shouldCreateUserIDThatIsConsistentWithOrgID()
            throws Exception
    {
        UserID tsUserID = getTeamServerUserID();
        verify(odb).addOrganization(capOrgID.capture(), anyString());
        assertEquals(capOrgID.getValue().toTeamServerUserID(), tsUserID);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNonAdminInNonDefaultOrg()
            throws Exception
    {
        service.addOrganization("test");

        transaction.begin();
        user.setLevel(AuthorizationLevel.USER);
        transaction.commit();

        getTeamServerUserID();
    }

    private UserID getTeamServerUserID()
            throws Exception
    {
        return UserID.fromInternal(service.getTeamServerUserID().get().getId());
    }
}
