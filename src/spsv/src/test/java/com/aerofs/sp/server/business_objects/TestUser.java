/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestUser extends AbstractBusinessObjectTest
{

    private void addSharedFolder(User sharer, List<User> sharees, String name, SharedFolderState sfs)
            throws SQLException, ExAlreadyExist, ExNotFound, ExNoPerm
    {
        SharedFolder sf = factSharedFolder.create(SID.generate());
        sf.save(name, sharer);
        for (User sharee: sharees) {
            sf.addPendingUser(sharee, Permissions.allOf(Permission.WRITE), sharer);
            sf.setState(sharee, sfs);
        }
    }

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

        addSharedFolder(user1, Arrays.asList(user2, user3), "Test Folder", SharedFolderState.JOINED);

        Collection<Device> userDevices = user1.getDevices();
        Collection<Device> peerDevices = user1.getPeerDevices();

        // Only my devices.
        assertEquals(2, userDevices.size());
        // All devices that I share with (including my own devices).
        assertEquals(6, peerDevices.size());
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

        assertEquals(2, userDevices.size());
        assertEquals(2, peerDevices.size());
    }

    @Test
    public void countJoinedSharedFolders_shouldCountUserSharedFolders()
            throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();
        User user3 = saveUser();

        assertEquals(0, user1.countJoinedSharedFolders());
        assertEquals(0, user2.countJoinedSharedFolders());
        assertEquals(0, user3.countJoinedSharedFolders());

        // Add 5 shared folders.
        addSharedFolder(user1, Arrays.asList(user2), "Test Folder", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user2), "test Folder", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user3), "1sf", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user3), "1Sf", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user3), "sf", SharedFolderState.JOINED);

        assertEquals(5, user1.countJoinedSharedFolders());
        assertEquals(2, user2.countJoinedSharedFolders());
        assertEquals(3, user3.countJoinedSharedFolders());
    }

    @Test
    public void countJoinedSharedFoldersWithSearchString_shouldCountUserSharedFoldersWithSearchString()
            throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();
        User user3 = saveUser();

        assertEquals(0, user1.countJoinedSharedFolders());
        assertEquals(0, user2.countJoinedSharedFolders());
        assertEquals(0, user3.countJoinedSharedFolders());

        createSharedFolders(user1, user2);
        createSharedFolders(user1, user3);

        assertEquals(4, user1.countJoinedSharedFoldersWithSearchString("tes"));
        assertEquals(0, user2.countJoinedSharedFoldersWithSearchString("a"));
        assertEquals(5, user3.countJoinedSharedFoldersWithSearchString("s"));
    }

    @Test
    public void countJoinedSharedFoldersWithSearchString_shouldCountUserSharedFoldersWithNullSearchString()
            throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();

        assertEquals(0, user1.countJoinedSharedFolders());
        assertEquals(0, user2.countJoinedSharedFolders());

        createSharedFolders(user1, user2);

        assertEquals(2, user1.countJoinedSharedFoldersWithSearchString("tes"));
        assertEquals(5, user1.countJoinedSharedFoldersWithSearchString(null));
        assertEquals(5, user2.countJoinedSharedFoldersWithSearchString(null));
    }

    @Test
    public void getJoinedSharedFolders_shouldListInAlphabeticalOrder() throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();

        createSharedFolders(user1, user2);

        List<String> orderedNames =
                Lists.newArrayList("1Sf", "1sf", "sf", "Test Folder", "test Folder");
        List<String> resultNames = Lists.newArrayList();
        for (SharedFolder sf : user1.getJoinedSharedFolders()) {
            resultNames.add(sf.getName(user1));
        }

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());
    }

    @Test
    public void getJoinedSharedFolders_shouldListAlphabeticallyWithLimitOffset() throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();

        createSharedFolders(user1, user2);

        //Page 1
        List<String> orderedNames = Lists.newArrayList("1Sf", "1sf", "sf");
        List<String> resultNames = Lists.newArrayList();
        for (SharedFolder sf : user1.getJoinedSharedFolders(3, 0, null)) {
            resultNames.add(sf.getName(user1));
        }

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());

        //Page 2
        orderedNames = Lists.newArrayList("Test Folder","test Folder");
        resultNames = Lists.newArrayList();
        for (SharedFolder sf : user1.getJoinedSharedFolders(3, 3, null)) {
            resultNames.add(sf.getName(user1));
        }

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());
    }

    @Test
    public void getJoinedSharedFolders_shouldListAlphabeticallyWithLimitOffsetSearchString()
            throws Exception
    {
        User user1 = saveUser();
        User user2 = saveUser();

        createSharedFolders(user1, user2);

        //Page 1
        List<String> orderedNames = Lists.newArrayList("1Sf");
        List<String> resultNames = Lists.newArrayList();
        for (SharedFolder sf : user1.getJoinedSharedFolders(1, 0, "1")) {
            resultNames.add(sf.getName(user1));
        }

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());

        //Page 2
        orderedNames = Lists.newArrayList("1sf");
        resultNames = Lists.newArrayList();
        for (SharedFolder sf : user1.getJoinedSharedFolders(1, 1, "1")) {
            resultNames.add(sf.getName(user1));
        }

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());

        //No Matches
        Assert.assertArrayEquals(new String[0], user1.getJoinedSharedFolders(10, 0, "zzz").toArray());
    }

    @Test
    public void shouldSupportUtf8mb4Name() throws Exception
    {
        User user = saveUser();
        FullName expected = new FullName("\uD83D\uDCA9", "\uD83D\uDCA9");
        user.setName(expected);

        assertEquals(expected, user.getFullName());
    }

    private void createSharedFolders(User user1, User user2)
            throws Exception
    {
        // Add 5 shared folders.
        addSharedFolder(user1, Arrays.asList(user2), "Test Folder", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user2), "test Folder", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user2), "1sf", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user2), "1Sf", SharedFolderState.JOINED);
        addSharedFolder(user1, Arrays.asList(user2), "sf", SharedFolderState.JOINED);
    }
}
