/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static com.aerofs.sp.server.lib.user.AuthorizationLevel.ADMIN;
import static com.aerofs.sp.server.lib.user.AuthorizationLevel.USER;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;

public class TestUser_SetOrganization extends AbstractBusinessObjectTest
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
        User admin = saveUserWithNewOrganization();
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
    public void shouldOnlySetLevelForSameTeam()
            throws Exception
    {
        User user = saveUser();
        // add another admin to the org so setLevel() wouldn't complain about no-admin orgs when
        // called by setOrganization().
        saveUser().setOrganization(user.getOrganization(), ADMIN);

        assertEquals(user.getLevel(), ADMIN);
        // Always bump TS ACL epoch to make sure it can auto-join root store of the newly
        // created user immediately
        assertEquals(ImmutableList.of(user.getOrganization().id().toTeamServerUserID()),
                user.setOrganization(user.getOrganization(), USER));
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
        User admin = saveUserWithNewOrganization();
        Organization orgNew = admin.getOrganization();
        User tsUserNew = orgNew.getTeamServerUser();

        Iterable<UserID> aclBump = user.setOrganization(orgNew, AuthorizationLevel.USER);

        assertThat(aclBump, containsInAnyOrder(user.id(), tsUserOld.id(), tsUserNew.id()));
        assertNull(sfRoot.getPermissionsNullable(tsUserOld));
        assertNull(sf1.getPermissionsNullable(tsUserOld));
        assertNull(sf2.getPermissionsNullable(tsUserOld));
        assertJoinedRole(sfRoot, tsUserNew, Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf1, tsUserNew, Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf2, tsUserNew, Permissions.allOf(Permission.WRITE));
    }
}
