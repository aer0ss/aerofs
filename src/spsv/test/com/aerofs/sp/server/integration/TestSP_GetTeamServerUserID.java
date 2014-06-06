/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

public class TestSP_GetTeamServerUserID extends AbstractSPTest
{
    @Captor ArgumentCaptor<OrganizationID> capOrganizationID;

    User user;

    @Before
    public void setup()
            throws ExNotAuthenticated
    {
        setSession(USER_1);
        user = USER_1;
    }

    @Test (expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNonAdmin()
            throws Exception
    {
        // set USER_1 a non-admin
        sqlTrans.begin();
        USER_1.setOrganization(USER_2.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        getTeamServerUserID();
    }

    private UserID getTeamServerUserID()
            throws Exception
    {
        return UserID.fromInternal(service.getTeamServerUserID().get().getId());
    }
}
