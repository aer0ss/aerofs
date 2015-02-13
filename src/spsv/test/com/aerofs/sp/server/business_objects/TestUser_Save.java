/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static com.aerofs.lib.LibParam.PrivateDeploymentConfig.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestUser_Save extends AbstractBusinessObjectTest
{
    @Test
    public void shouldThrowIfCreatingDuplicate()
            throws Exception
    {
        User user = newUser();
        saveUser(user);
        try {
            saveUser(user);
            fail();
        } catch (ExAlreadyExist e) {}
    }

    // see User.addRootStoreAndCheckForCollision for detail
    @Test
    public void shouldCorrectRootStoreCollision()
            throws Exception
    {
        // create the players
        User attacker = newUser("attacker");
        User attacker2 = newUser("attacker2");
        saveUser(attacker);
        saveUser(attacker2);

        User user = newUser();

        // insert the colliding root store
        SharedFolder sf = factSharedFolder.create(SID.rootSID(user.id()));
        sf.save("haha", attacker);
        sf.addJoinedUser(attacker2, Permissions.allOf(Permission.WRITE));
        assertEquals(sf.getPermissionsNullable(attacker), Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        assertEquals(sf.getPermissionsNullable(attacker2), Permissions.allOf(Permission.WRITE));

        // create the ligitimate user
        saveUser(user);

        // the collision should have been corrected
        assertNull(sf.getPermissionsNullable(attacker));
        assertNull(sf.getPermissionsNullable(attacker2));
    }

    @Test
    public void shouldCreateNewOrgIfPublicDeployment() throws Exception
    {
        User user = saveUser();

        // Check that the user is *not* in the private org and that he's an admin
        assertFalse(user.getOrganization().id().equals(OrganizationID.PRIVATE_ORGANIZATION));
        assertEquals(AuthorizationLevel.ADMIN, user.getLevel());
    }

    @Test
    public void shouldSaveToPrivateOrgIfPrivateDeployment() throws Exception
    {
        IS_PRIVATE_DEPLOYMENT = true;
        try {
            Organization org = factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

            // Check that the private organization doesn't exist yet.
            // This is important because we want to test that the first user is created with admin
            // privileges
            // Note: this test may fail if some earlier test create the private org. In this case,
            // we either have to get the ordering right, or provide a method for deleting an org.
            assertFalse(org.exists());

            // Create a new user
            User user = saveUser();

            assertTrue(org.exists());

            // Check that the user *is* in the private org and that he's an admin
            assertEquals(OrganizationID.PRIVATE_ORGANIZATION, user.getOrganization().id());
            assertEquals(AuthorizationLevel.ADMIN, user.getLevel());

            // Now create an additional user; check he's also in private org but as a regular user
            user = saveUser();
            assertEquals(OrganizationID.PRIVATE_ORGANIZATION, user.getOrganization().id());
            assertEquals(AuthorizationLevel.USER, user.getLevel());
        } finally {
            IS_PRIVATE_DEPLOYMENT = false;
        }
    }

    @Test
    public void shouldRemoveOrgInviteInPrivateDeployment()
            throws Exception
    {
        IS_PRIVATE_DEPLOYMENT = true;
        try {
            Organization org = factOrg.save(OrganizationID.PRIVATE_ORGANIZATION);
            User user = newUser();
            OrganizationInvitation oi = factOrgInvite.save(newUser(), user, org, null);
            assertTrue(oi.exists());
            saveUser(user);
            assertFalse(oi.exists());
        } finally {
            IS_PRIVATE_DEPLOYMENT = false;
        }
    }
}
