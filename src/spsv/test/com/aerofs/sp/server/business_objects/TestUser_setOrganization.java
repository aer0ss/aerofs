/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.SID;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoAdmin;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;

import static com.aerofs.sp.server.lib.user.AuthorizationLevel.*;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;

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
        } catch (ExNoAdmin e) {}
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
        } catch (ExNoAdmin e) {
        }
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

        assertEquals(sfRoot.getMemberRoleNullable(tsUserOld), Role.EDITOR);
        assertEquals(sf1.getMemberRoleNullable(tsUserOld), Role.EDITOR);
        assertEquals(sf2.getMemberRoleNullable(tsUserOld), Role.EDITOR);

        // Create a new org with an admin otherwise setOrganization would fail with ExNoAdmin.
        User admin = saveUser();
        Organization orgNew = admin.getOrganization();
        User tsUserNew = orgNew.getTeamServerUser();

        user.setOrganization(orgNew, AuthorizationLevel.USER);

        assertNull(sfRoot.getMemberRoleNullable(tsUserOld));
        assertNull(sf1.getMemberRoleNullable(tsUserOld));
        assertNull(sf2.getMemberRoleNullable(tsUserOld));
        assertEquals(sfRoot.getMemberRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf1.getMemberRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf2.getMemberRoleNullable(tsUserNew), Role.EDITOR);
    }
}
