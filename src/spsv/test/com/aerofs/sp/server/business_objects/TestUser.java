/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.FullName;
import com.aerofs.base.acl.Role;
import com.aerofs.lib.LibParam.EnterpriseConfig;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import org.junit.Assert;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestUser extends AbstractBusinessObjectTest
{
    @Test(expected = ExNotFound.class)
    public void getOrganization_shouldThrowIfUserNotFound()
            throws ExNotFound, SQLException
    {
        newUser().getOrganization();
    }

    @Test(expected = ExNotFound.class)
    public void getFullName_shouldThrowIfUserNotFound()
            throws ExNotFound, SQLException
    {
        newUser().getFullName();
    }

    @Test(expected = ExNotFound.class)
    public void getShaedSP_shouldThrowIfUserNotFound()
            throws ExNotFound, SQLException
    {
        newUser().isCredentialCorrect(new byte[0]);
    }

    @Test(expected = ExNotFound.class)
    public void getLevel_shouldThrowIfUserNotFound()
            throws ExNotFound, SQLException
    {
        newUser().getLevel();
    }

    @Test(expected = AssertionError.class)
    public void setLevel_shouldAssertIfUserNotFound()
            throws Exception
    {
        newUser().setLevel(AuthorizationLevel.ADMIN);
    }

    @Test
    public void setLevel_shouldThrowIfNoMoreAdmin()
            throws Exception
    {
        User user = saveUser();

        try {
            user.setLevel(AuthorizationLevel.USER);
            fail();
        } catch (ExNoAdminOrOwner e) {}
    }

    @Test(expected = AssertionError.class)
    public void setName_shouldAssertIfUserNotFound()
            throws ExNotFound, SQLException
    {
        newUser().setName(new FullName("first", "last"));
    }

    @Test(expected = MySQLIntegrityConstraintViolationException.class)
    public void saveTeamServerUser_shouldThrowIfWithoutOrg()
            throws Exception
    {
        factUser.saveTeamServerUser(newOrganization());
    }

    @Test(expected = ExAlreadyExist.class)
    public void save_shouldThrowIfCreatingDuplicate()
            throws Exception
    {
        User user = newUser();
        saveUser(user);
        saveUser(user);
    }

    // see User.addRootStoreAndCheckForCollision for detail
    @Test
    public void save_shouldCorrectRootStoreCollision()
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
        sf.addMemberACL(attacker2, Role.EDITOR);
        assertEquals(sf.getMemberRoleThrows(attacker), Role.OWNER);
        assertEquals(sf.getMemberRoleThrows(attacker2), Role.EDITOR);

        // create the ligitimate user
        saveUser(user);

        // the collision should have been corrected
        assertNull(sf.getMemberRoleNullable(attacker));
        assertNull(sf.getMemberRoleNullable(attacker2));
    }

    @Test
    public void save_shouldCreateNewOrgIfPublicDeployment() throws Exception
    {
        User user = saveUser();

        // Check that the user is *not* in the main org and that he's an admin
        assertFalse(user.getOrganization().id().equals(OrganizationID.MAIN_ORGANIZATION));
        assertEquals(AuthorizationLevel.ADMIN, user.getLevel());
    }

    @Test
    public void save_shouldSaveToMainOrgIfEnterpriseDeployment() throws Exception
    {
        EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT = true;

        try {
            Organization mainOrg = factOrg.create(OrganizationID.MAIN_ORGANIZATION);

            // Check that the main organization doesn't exist yet.
            // This is important because we want to test that the first user is created with admin
            // privileges
            // Note: this test may fail if some earlier test create the main org. In this case, we
            // either have to get the ordering right, or provide a method for deleting an org.
            assertFalse(mainOrg.exists());

            // Create a new user
            User user = saveUser();

            assertTrue(mainOrg.exists());

            // Check that the user *is* in the main org and that he's an admin
            assertEquals(OrganizationID.MAIN_ORGANIZATION, user.getOrganization().id());
            assertEquals(AuthorizationLevel.ADMIN, user.getLevel());

            // Now create an additional user; check he's also in main org but as a regular user
            user = saveUser();
            assertEquals(OrganizationID.MAIN_ORGANIZATION, user.getOrganization().id());
            assertEquals(AuthorizationLevel.USER, user.getLevel());
        } finally {
            EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT = false;
        }
    }

    @Test(expected = ExBadCredential.class)
    public void signIn_shouldThrowBadCredentialIfUserNotFound()
            throws SQLException, ExBadCredential
    {
        newUser().throwIfBadCredential(new byte[0]);
    }

    @Test
    public void getPeerDevices_shouldListPeerDevices()
            throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();
        User user3 = saveUser();
        User user4 = saveUser();

        // The 4 devices we will expect.
        factDevice.create(new DID(UniqueID.generate())).save(user1, "", "", "Device1a");
        factDevice.create(new DID(UniqueID.generate())).save(user1, "", "", "Device1c");
        factDevice.create(new DID(UniqueID.generate())).save(user2, "", "", "Device2");
        factDevice.create(new DID(UniqueID.generate())).save(user3, "", "", "Device3a");
        factDevice.create(new DID(UniqueID.generate())).save(user3, "", "", "Device3b");
        factDevice.create(new DID(UniqueID.generate())).save(user3, "", "", "Device3c");
        factDevice.create(new DID(UniqueID.generate())).save(user4, "", "", "Device4");

        SharedFolder sf = factSharedFolder.create(SID.generate());

        sf.save("Test Folder", user1);
        sf.addMemberACL(user2, Role.EDITOR);
        sf.addMemberACL(user3, Role.EDITOR);

        Collection<Device> userDevices = user1.getDevices();
        Collection<Device> peerDevices = user1.getPeerDevices();

        // Only my devices.
        Assert.assertEquals(2, userDevices.size());
        // All devices that I share with (including my own devices).
        Assert.assertEquals(6, peerDevices.size());
    }

    @Test
    public void getPeerDevices_shouldListPeerDevicesWhenNoFoldersAreShared()
            throws Exception
    {
        User user1 = saveUser();

        factDevice.create(new DID(UniqueID.generate())).save(user1, "", "", "Device1a");
        factDevice.create(new DID(UniqueID.generate())).save(user1, "", "", "Device1b");

        Collection<Device> userDevices = user1.getDevices();
        Collection<Device> peerDevices = user1.getPeerDevices();

        Assert.assertEquals(2, userDevices.size());
        Assert.assertEquals(2, peerDevices.size());
    }
}
