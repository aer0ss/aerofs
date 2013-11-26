/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.SID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;

import static com.aerofs.sp.server.lib.user.AuthorizationLevel.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestUser_setOrganization extends AbstractBusinessObjectTest
{
    @Test
    public void shouldSetAuthLevel()
            throws Exception
    {
        User user = saveUser();
        Organization org = saveOrganization();
        user.setOrganization(org, ADMIN);
        assertEquals(user.getLevel(), ADMIN);

        // Create a new org with an admin otherwise setOrganization would fail with ExNoAdmin.
        User admin = saveUser();
        user.setOrganization(admin.getOrganization(), USER);
        assertEquals(user.getLevel(), USER);
    }

    @Test
    public void shouldThrowIfNoAdminForNewTeam()
            throws Exception
    {
        User user = saveUser();
        Organization org = saveOrganization();

        try {
            user.setOrganization(org, USER);
            fail();
        } catch (ExNoAdminOrOwner e) {}
    }

    @Test
    public void shouldThrowIfNoAdminForPreviousTeam()
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
        } catch (ExNoAdminOrOwner e) {
        }
    }

    @Test
    public void shouldDeleteOrganizationInvite()
            throws Exception
    {
        User user = saveUser();
        Organization org = saveUser().getOrganization();

        OrganizationInvitation oi = factOrgInvite.save(saveUser(), user, org, null);
        assertTrue(oi.exists());
        user.setOrganization(org, USER);
        assertFalse(oi.exists());
    }

    @Test
    public void shouldOnlySetLevelForSameTeam()
            throws Exception
    {
        User user = saveUser();
        // add another admin to the org so setLevel() wouldn't complain about no-admin orgs when
        // called by setOrganization().
        saveUser().setOrganization(user.getOrganization(), ADMIN);

        assertEquals(user.getLevel(), ADMIN);
        // It should not touch ACLs. This optimization is important for private deployments, where
        // everyone automatically becomes a member of the same private org during signup, and
        // therefore all calls to setOrganization are unuseful.
        assertEquals(user.setOrganization(user.getOrganization(), USER).size(), 0);
        assertEquals(user.getLevel(), USER);
    }

    @Test
    public void shouldThrowIfNoAdminForSameTeam()
            throws Exception
    {
        User user = saveUser();

        try {
            user.setOrganization(user.getOrganization(), USER);
            fail();
        } catch (ExNoAdminOrOwner e) {}
    }

    @Test
    public void shouldUpdateTeamServerACLs()
            throws Exception
    {
        User user = saveUser();
        User tsUserOld = user.getOrganization().getTeamServerUser();

        SharedFolder sfRoot = factSharedFolder.create(SID.rootSID(user.id()));
        SharedFolder sf1 = factSharedFolder.create(SID.generate());
        SharedFolder sf2 = factSharedFolder.create(SID.generate());
        sf1.save("haha", user);
        sf2.save("haha", user);

        assertJoinedRole(sfRoot, tsUserOld, Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf1, tsUserOld, Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf2, tsUserOld, Permissions.allOf(Permission.WRITE));

        // Create a new org with an admin otherwise setOrganization would fail with ExNoAdmin.
        User admin = saveUser();
        Organization orgNew = admin.getOrganization();
        User tsUserNew = orgNew.getTeamServerUser();

        user.setOrganization(orgNew, AuthorizationLevel.USER);

        assertNull(sfRoot.getPermissionsNullable(tsUserOld));
        assertNull(sf1.getPermissionsNullable(tsUserOld));
        assertNull(sf2.getPermissionsNullable(tsUserOld));
        assertJoinedRole(sfRoot, tsUserNew, Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf1, tsUserNew, Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf2, tsUserNew, Permissions.allOf(Permission.WRITE));
    }
}
