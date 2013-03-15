/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNoAdminForNonEmptyTeam;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;

import static com.aerofs.sp.server.lib.user.AuthorizationLevel.*;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;

public class TestUser_setOrganization extends AbstractBusinessObjectTest
{
    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserNoPermissionOnAddAndMoveToOrg()
            throws Exception
    {
        Organization org = saveOrganization();
        User user = newUser();
        saveUser(user, org);
        user.addAndMoveToOrganization("test", null, StripeCustomerID.TEST);
    }

    @Test
    public void shouldSetUserAsAdminOnAddAndMoveToNewOrganization()
            throws Exception
    {
        User user = newUser();
        saveUser(user, factOrg.getDefault());
        user.addAndMoveToOrganization("test", null, StripeCustomerID.TEST);

        assertFalse(user.getOrganization().isDefault());
        assertEquals(user.getLevel(), ADMIN);
    }

    @Test
    public void shouldSetAuthLevel()
            throws Exception
    {
        User user = newUser();
        saveUser(user, factOrg.getDefault());
        Organization org = saveOrganization();
        user.setOrganization(org, ADMIN);
        assertEquals(user.getLevel(), ADMIN);

        org = saveOrganization();
        user.setOrganization(org, USER);
        assertEquals(user.getLevel(), USER);
    }

    @Test
    public void shouldThrowIfNoAdminForNonEmptyTeam()
            throws Exception
    {
        User user1 = saveUser();
        user1.setLevel(ADMIN);

        // user2 joins user1's org as a non-admin
        User user2 = saveUser();
        user2.setOrganization(user1.getOrganization(), USER);

        // now move user1 away
        Organization org = saveOrganization();

        try {
            user1.setOrganization(org, ADMIN);
            fail();
        } catch (ExNoAdminForNonEmptyTeam e) {
        }
    }
}
