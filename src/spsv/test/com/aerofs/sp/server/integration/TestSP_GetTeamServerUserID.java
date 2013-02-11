/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.organization.OrganizationID;
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
            throws ExNoPerm
    {
        setSessionUser(USER_1);
        user = sessionUser.get();
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserIsNonAdminInNonDefaultOrg()
            throws Exception
    {
        service.addOrganization("test", null, StripeCustomerID.TEST.getID());

        trans.begin();
        user.setLevel(AuthorizationLevel.USER);
        trans.commit();

        getTeamServerUserID();
    }

    private UserID getTeamServerUserID()
            throws Exception
    {
        return UserID.fromInternal(service.getTeamServerUserID().get().getId());
    }
}
