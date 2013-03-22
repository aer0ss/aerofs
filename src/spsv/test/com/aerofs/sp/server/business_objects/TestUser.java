/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.ex.ExNoAdmin;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.device.Device;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class TestUser extends AbstractBusinessObjectTest
{
    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetOrganization()
            throws ExNotFound, SQLException
    {
        newUser().getOrganization();
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetFullName()
            throws ExNotFound, SQLException
    {
        newUser().getFullName();
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetShaedSP()
            throws ExNotFound, SQLException
    {
        newUser().isCredentialCorrect(new byte[0]);
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetLevel()
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

    @Test(expected = ExNoAdmin.class)
    public void setLevel_shouldThrowIfNoMoreAdmin()
            throws Exception
    {
        saveUser().setLevel(AuthorizationLevel.USER);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertIfUserNotFoundOnSetName()
            throws ExNotFound, SQLException
    {
        newUser().setName(new FullName("first", "last"));
    }

    @Test(expected = MySQLIntegrityConstraintViolationException.class)
    public void shouldThrowIfCreatingTeamServerUserWithoutOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        factUser.saveTeamServerUser(newOrganization());
    }

    @Test(expected = ExAlreadyExist.class)
    public void shouldThrowIfCreatingDuplicateUsers()
            throws Exception
    {
        User user = newUser();
        saveUser(user);
        saveUser(user);
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
        sf.addMemberACL(attacker2, Role.EDITOR);
        assertEquals(sf.getMemberRoleThrows(attacker), Role.OWNER);
        assertEquals(sf.getMemberRoleThrows(attacker2), Role.EDITOR);

        // create the ligitimate user
        saveUser(user);

        // the collision should have been corrected
        assertNull(sf.getMemberRoleNullable(attacker));
        assertNull(sf.getMemberRoleNullable(attacker2));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldThrowBadCredentialIfUserNotFoundOnSignIn()
            throws SQLException, ExBadCredential
    {
        newUser().signIn(new byte[0]);
    }

    @Test
    public void shouldUpdateTeamServerACLsOnSetOrg()
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

        Organization orgNew = saveOrganization();
        User tsUserNew = orgNew.getTeamServerUser();

        user.setOrganization(orgNew, AuthorizationLevel.USER);

        assertNull(sfRoot.getMemberRoleNullable(tsUserOld));
        assertNull(sf1.getMemberRoleNullable(tsUserOld));
        assertNull(sf2.getMemberRoleNullable(tsUserOld));
        assertEquals(sfRoot.getMemberRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf1.getMemberRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf2.getMemberRoleNullable(tsUserNew), Role.EDITOR);
    }

    @Test
    public void shouldListPeerDevices()
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
    public void shouldListPeerDevicesWhenNoFoldersAreShared()
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
