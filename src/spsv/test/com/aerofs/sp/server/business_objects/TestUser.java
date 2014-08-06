/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableSet;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import org.junit.Assert;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Collection;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
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
        sf.addPendingUser(user2, Permissions.allOf(Permission.WRITE), user1);
        sf.setState(user2, SharedFolderState.JOINED);
        sf.addPendingUser(user3, Permissions.allOf(Permission.WRITE), user1);
        sf.setState(user3, SharedFolderState.JOINED);

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

    @Test
    public void deactivate_shouldMakeAllMethodsBehaveAsIfUserNeverExisted() throws Exception
    {
        User user = saveUser();
        saveSharedFolder(user);
        addCert(saveDevice(user));

        user.deactivate(ImmutableSet.<Long>builder(), null);

        assertFalse(user.exists());

        try {
            user.getFullName();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.getOrganization();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.getLevel();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.getSignupDate();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.isAdmin();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.belongsTo(factOrg.create(42));
            fail();
        } catch (ExNotFound e) {}

        assertThat(user.getDevices(), empty());
        assertThat(user.getPeerDevices(), empty());
        assertThat(user.getJoinedFolders(), empty());
        assertThat(user.getPendingSharedFolders(), empty());

        try {
            user.getACLEpoch();
            fail();
        } catch (AssertionError e) {}
    }


    @Test
    public void deactivate_shouldDeleteSharedFolderWithNoJoinedUsers() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addPendingUser(saveUser(), Permissions.allOf(Permission.WRITE), user);
        User left = saveUser();
        sf.addJoinedUser(left, Permissions.allOf());
        sf.setState(left, SharedFolderState.LEFT);

        User admin = saveUser();

        user.deactivate(ImmutableSet.<Long>builder(), admin);

        assertFalse(sf.exists());
    }

    @Test
    public void deactivate_shouldTransferOwnershipOnDeactivationOfLastOwner() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE));

        User admin = saveUser();

        user.deactivate(ImmutableSet.<Long>builder(), admin);

        assertTrue(sf.exists());
        assertTrue(sf.hasOwnerLeft());
        assertThat(sf.getJoinedUsers(), hasItem(admin));
    }

    @Test
    public void deactivate_shouldNotTransferOwnershipOnWhenOtherOwnersLeft() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        User admin = saveUser();

        user.deactivate(ImmutableSet.<Long>builder(), admin);

        assertTrue(sf.exists());
        assertTrue(sf.hasOwnerLeft());
        assertThat(sf.getJoinedUsers(), not(hasItem(admin)));
    }

    @Test
    public void deactivate_shouldFailWhenCannotTransferOwnership() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE));

        try {
            user.deactivate(ImmutableSet.<Long>builder(), null);
            fail();
        } catch (ExNoAdminOrOwner e) {}
    }
}
