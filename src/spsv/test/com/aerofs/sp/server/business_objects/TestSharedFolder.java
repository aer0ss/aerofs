/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSharedFolder extends AbstractBusinessObjectTest
{
    public static OrganizationID orgID = new OrganizationID(1);

    @Test(expected = ExAlreadyExist.class)
    public void saveSharedFolder_shouldThrowOnDuplicate()
            throws Exception
    {
        SID sid = SID.generate();
        saveUserAndSharedFolder(sid);

        User user = saveUser();
        saveSharedFolder(sid, user);
    }

    @Test
    public void getName_shouldReturnCorrectNameAfterCreation()
            throws Exception
    {
        final String NAME = "haha";

        SharedFolder sf = newSharedFolder();

        // Check that the name is correct for the owner of the shared folder
        User owner = saveUser();
        sf.save(NAME, owner);
        assertEquals(sf.getName(owner), NAME);

        // Check that the name is correct for a new user that hasn't set a name for the folder yet.
        User newUser = newUser();
        assertEquals(sf.getName(newUser), NAME);
    }

    @Test(expected = ExNotFound.class)
    public void getName_shouldThrowIfFolderNotFound()
            throws ExNotFound, SQLException
    {
        newSharedFolder().getName(newUser());
    }

    @Test
    public void setName_shouldChangeName()
            throws Exception
    {
        final String ORIGINAL_NAME = "haha";
        final String NEW_NAME1 = "hehehe";
        final String NEW_NAME2 = "hihihihihi";

        SharedFolder sf = newSharedFolder();
        User owner = saveUser();
        User otherUser = saveUser();

        sf.save(ORIGINAL_NAME, owner);

        sf.setName(otherUser, NEW_NAME1);
        assertEquals(sf.getName(otherUser), NEW_NAME1);
        assertEquals(sf.getName(owner), ORIGINAL_NAME);

        sf.setName(owner, NEW_NAME2);
        assertEquals(sf.getName(otherUser), NEW_NAME1);
        assertEquals(sf.getName(owner), NEW_NAME2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setName_shouldThrowIfEmptyName()
            throws Exception
    {
        User user = saveUser();
        SharedFolder sf = saveSharedFolder(user);
        sf.setName(user, "");
    }

    @Test(expected = ExNotFound.class)
    public void setName_shouldThrowIfFolderNotFound()
            throws Exception
    {
        SharedFolder sf = newSharedFolder();
        sf.setName(saveUser(), "aaa");
    }

    @Test(expected = ExNotFound.class)
    public void setName_shouldThrowIfUserNotFound()
            throws Exception
    {
        SharedFolder sf = saveSharedFolder(saveUser());
        sf.setName(newUser(), "aaa");
    }

    @Test(expected = AssertionError.class)
    public void destroy_shouldAssertIfSharedFolderNotFound()
            throws SQLException
    {
        newSharedFolder().destroy();
    }

    @Test
    public void destroy_shouldNotFindAfterDeletion()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();
        assertTrue(sf.exists());
        // why 2? the user and his Team Server
        assertEquals(sf.destroy().size(), 2);
        assertFalse(sf.exists());
    }

    @Test
    public void saveSharedFolder_shouldAddOwnerWithCorrectFields()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(SID.generate(), owner);
        assertJoinedRole(sf, owner, Permissions.allOf(Permission.MANAGE, Permission.WRITE));
        assertNull(sf.getSharerNullable(owner));
    }

    @Test
    public void saveSharedFolder_shouldAddTeamServerWithCorrectFields()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User tsUser = getTeamServerUser(owner);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE));
        assertNull(sf.getSharerNullable(tsUser));
    }

    @Test
    public void shouldAllowTeamServerIfOneMemberOfSameOrgIsOwner() throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User ts = owner.getOrganization().getTeamServerUser();
        sf.throwIfNoPrivilegeToChangeACL(ts);
    }

    @Test
    public void shouldRejectTeamServerIfNoMemberOfSameOrg() throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUserWithNewOrganization();

        User ts = user.getOrganization().getTeamServerUser();
        try {
            sf.throwIfNoPrivilegeToChangeACL(ts);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldRejectTeamServerIfNoJoinedUsersOfSameOrgIsOwner() throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUserWithNewOrganization();
        // why 4? owner, user, and their Team Servers
        addJoinedUser(sf, user, Permissions.allOf(Permission.WRITE), owner, 4);

        User ts = user.getOrganization().getTeamServerUser();
        try {
            sf.throwIfNoPrivilegeToChangeACL(ts);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void addPendingUser_shouldThrowExAlreadyExistsIfSubjectExists()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();
        User sharer = saveUser();

        sf.addPendingUser(user, Permissions.allOf(Permission.WRITE), sharer);
        // why 0? no joined user is affected
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 0);

        // commit the transaction so the sqlTrans.handleException() below won't rollback the changes
        // we made so far.
        sqlTrans.commit();
        sqlTrans.begin();

        try {
            sf.addPendingUser(user, Permissions.allOf(Permission.WRITE, Permission.MANAGE), sharer);
            assertTrue(false);
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
            sqlTrans.begin();
        }

        assertEquals(Permissions.allOf(Permission.WRITE), sf.getPermissionsNullable(user));
        assertEquals(SharedFolderState.LEFT, sf.getStateNullable(user));
    }

    @Test
    public void addPendingUser_shouldNotAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUserWithNewOrganization();

        sf.addPendingUser(user, Permissions.allOf(Permission.WRITE), owner);
        assertNull(sf.getPermissionsNullable(getTeamServerUser(user)));

        // why 2? owner, owner's Team Server
        assertEquals(sf.getJoinedUsers().size(), 2);
    }

    @Test
    public void addPendingUser_shouldSetSharer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        sf.addPendingUser(user, Permissions.allOf(Permission.WRITE), owner);
        assertEquals(sf.getSharerNullable(user), owner);
    }

    @Test
    public void setStateToPending_shouldRemoveTeamServer()
            throws Exception
    {
        setStateAwayFromJoined_shouldRemoveTeamServer(SharedFolderState.PENDING);
    }

    @Test
    public void setStateToLeft_shouldRemoveTeamServer()
            throws Exception
    {
        setStateAwayFromJoined_shouldRemoveTeamServer(SharedFolderState.LEFT);
    }

    private void setStateAwayFromJoined_shouldRemoveTeamServer(SharedFolderState state)
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUserWithNewOrganization();

        // why 4? owner, user, and their Team Servers
        addJoinedUser(sf, user, Permissions.allOf(Permission.WRITE), owner, 4);
        User tsUser = getTeamServerUser(user);
        assertEquals(sf.getPermissionsNullable(tsUser), Permissions.allOf(Permission.WRITE));

        // why 4? owner, user, and their team servers
        assertEquals(sf.setState(user, state).size(), 4);
        assertNull(sf.getPermissionsNullable(tsUser));
    }

    @Test
    public void setStateAwayFromJoined_shouldNotRemoveTeamServerIfOwner()
            throws Exception
    {
        User user = saveUser();
        User tsUser = getTeamServerUser(user);
        SharedFolder sf = saveSharedFolder(tsUser);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        // make sure TS not downgraded by adding a user
        // why 2? the team server (the owner) and the user
        addJoinedUser(sf, user, Permissions.allOf(Permission.WRITE), tsUser, 2);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        // make sure TS not kicked out when last org member leaves
        // why 2? the team server (the owner) and the user
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 2);
        // why 0? because no state changes for joined users
        assertEquals(sf.setState(user, SharedFolderState.PENDING).size(), 0);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
    }

    @Test
    public void setStateToJoined_shouldAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user = saveUserWithNewOrganization();
        sf.addPendingUser(user, Permissions.allOf(Permission.WRITE), owner);
        User tsUser = getTeamServerUser(user);
        assertNull(sf.getPermissionsNullable(tsUser));

        // why 4? owner, user, owner's team server, user's team server
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), 4);

        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE));

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.getJoinedUsers().size(), 4);
    }

    @Test
    public void setState_shouldNotifyAppropriateUsers()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user = saveUserWithNewOrganization();
        sf.addPendingUser(user, Permissions.allOf(Permission.WRITE), owner);

        // pending => joined
        // why 4? the owner, the user, and their team servers
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), 4);

        // joined => joined
        // why 0? no state has changed
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), 0);

        // joined => left
        // why 4? the owner, the user, and their team servers
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 4);

        // left => left
        // why 0? no state has changed
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 0);

        // left => joined
        // why 4? the owner, the user, and their team servers
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), 4);

        // joined => pending
        // why 4? the owner, the user, and their team servers
        assertEquals(sf.setState(user, SharedFolderState.PENDING).size(), 4);

        // pending => pending
        // why 0? no state has changed
        assertEquals(sf.setState(user, SharedFolderState.PENDING).size(), 0);

        // pending => left
        // why 0? no state has changed
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 0);

        // left => pending
        // why 0? no state has changed
        assertEquals(sf.setState(user, SharedFolderState.PENDING).size(), 0);
    }

    @Test
    public void addTeamServerForUser_shouldNotAddIfAlreadyExists()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user1 = saveUserWithNewOrganization();
        User user2 = saveUser();
        // Move the two users to the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        // why 3? owner, owner's TS, and user1's TSs
        assertEquals(sf.addTeamServerForUser(user1).size(), 3);
        List<User> users = Lists.newArrayList(sf.getJoinedUsers());

        // why 0? the team server is already added. so no one will be affected
        assertEquals(sf.addTeamServerForUser(user2).size(), 0);
        assertEquals(users, sf.getJoinedUsers());

        assertJoinedRole(sf, getTeamServerUser(user1), Permissions.allOf(Permission.WRITE));
        assertJoinedRole(sf, getTeamServerUser(user2), Permissions.allOf(Permission.WRITE));
    }

    @Test
    public void removeUser_shouldThrowIfNoOwnerLeft()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUserWithNewOrganization();
        // why 4? owner, user, and their team servers
        addJoinedUser(sf, user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE), owner, 4);
        assertEquals(sf.setPermissions(owner, Permissions.allOf(Permission.WRITE)).size(), 4);

        try {
            sf.removeIndividualUser(user1);
            fail();
        } catch (ExNoAdminOrOwner e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void removeUser_shouldThrowIfNoUserNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        try {
            sf.removeIndividualUser(user);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void removeServer_shouldRemoveTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUserWithNewOrganization();
        User user2 = saveUser();
        // Move the two users into the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        User tsUser = getTeamServerUser(user1);
        assertEquals(getTeamServerUser(user2), tsUser);

        // Why 4? owner, user1, owner's TS, and user1/2's TS
        addJoinedUser(sf, user1, Permissions.allOf(Permission.WRITE), owner, 4);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE));

        // Why 5? owner, user1, user2, owner's TS, and user1/2's TS
        addJoinedUser(sf, user2, Permissions.allOf(Permission.WRITE, Permission.MANAGE), owner, 5);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE));

        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server id
        assertEquals(sf.removeIndividualUser(user1).size(), 5);

        assertNull(sf.getPermissionsNullable(user1));

        // since user1 & 2 share the same org, the team server shouldn't have been removed.
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE));

        // why 4? owner, user2, owner's team server, user2's team server id
        assertEquals(sf.removeIndividualUser(user2).size(), 4);

        assertNull(sf.getPermissionsNullable(user2));

        assertNull(sf.getPermissionsNullable(tsUser));
    }

    @Test
    public void removeUser_shouldNotRemoveTeamServerIfOwner()
            throws Exception
    {
        User user = saveUser();
        User tsUser = getTeamServerUser(user);
        SharedFolder sf = saveSharedFolder(tsUser);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        // make sure TS not downgraded by adding a user
        // Why 2? the team server (the owner) and the user
        addJoinedUser(sf, user, Permissions.allOf(Permission.WRITE), tsUser, 2);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        // make sure TS not kicked out when last org member leaves
        // why 2? the user and the team server
        assertEquals(sf.removeIndividualUser(user).size(), 2);
        assertJoinedRole(sf, tsUser, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
    }

    @Test
    public void removeUser_shouldNotSelfDestroy() throws Exception
    {
        User user = saveUser();
        SharedFolder shared = saveSharedFolder(user);
        shared.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        shared.removeIndividualUser(user);

        assertTrue(shared.exists());
    }

    @Test
    public void removeUser_shouldSelfDestroyWhenNoJoinedUsersLeft() throws Exception
    {
        User user = saveUser();
        SharedFolder shared = saveSharedFolder(user);

        User invited = saveUser();
        shared.addPendingUser(invited, Permissions.allOf(Permission.WRITE), user);

        User left = saveUser();
        shared.addJoinedUser(left, Permissions.allOf(Permission.WRITE));
        shared.setState(left, SharedFolderState.LEFT);

        shared.removeIndividualUser(user);

        assertFalse(shared.exists());
        assertEquals(ImmutableList.of(factSharedFolder.create(SID.rootSID(invited.id()))),
                invited.getJoinedFolders());
        assertTrue(invited.getPendingSharedFolders().isEmpty());
        assertEquals(ImmutableList.of(factSharedFolder.create(SID.rootSID(left.id()))),
                left.getJoinedFolders());
        assertTrue(left.getPendingSharedFolders().isEmpty());
    }

    @Test
    public void removeTeamServerForUser_shouldThrowIfTeamServerNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUserWithNewOrganization();

        assertNull(sf.getPermissionsNullable(getTeamServerUser(user)));

        try {
            sf.removeTeamServerForUser(user);
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void removeTeamServerForUser_shouldNotRemoveIfOtherUserInSameOrg()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUserWithNewOrganization();
        User user2 = saveUser();
        // Move the two users into the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        // Why 4? owner, user1, owner's TS, and user1/2's TS
        addJoinedUser(sf, user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE), owner, 4);
        // Why 5? owner, user1, user2, owner's TS, and user1/2's TS
        addJoinedUser(sf, user2, Permissions.allOf(Permission.WRITE), owner, 5);

        // remove user1's team server with user2 being in the same org
        assertEquals(sf.removeTeamServerForUser(user1).size(), 0);

        // the team server should remain
        assertJoinedRole(sf, getTeamServerUser(user1), Permissions.allOf(Permission.WRITE));

        // now, remove user2
        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server
        assertEquals(sf.removeIndividualUser(user2).size(), 5);

        // why 4? owner, user1, owner's team server, user1's team server
        assertEquals(sf.removeTeamServerForUser(user1).size(), 4);

        // the team server should go away
        assertNull(sf.getPermissionsNullable(getTeamServerUser(user1)));
    }

    @Test
    public void setRole_shouldThrowIfNoOwnerLeft()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUserWithNewOrganization();
        // Why 4? owner, user, and their TS
        addJoinedUser(sf, user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE), owner, 4);

        // why 4? owner, user, and their TS
        assertEquals(sf.setPermissions(owner, Permissions.allOf(Permission.WRITE)).size(), 4);

        try {
            sf.setPermissions(user1, Permissions.allOf(Permission.WRITE));
            assertTrue(false);
        } catch (ExNoAdminOrOwner e) {
            sqlTrans.handleException();
        }
    }

    @Test(expected=ExNotFound.class)
    public void setRole_shouldThrowIfUserNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        sf.setPermissions(user, Permissions.allOf(Permission.WRITE));
    }

    @Test
    public void setRole_shouldUpdateRoleAsExpected()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUserWithNewOrganization();
        // Why 4? owner, user, and their TS
        addJoinedUser(sf, user1, Permissions.allOf(Permission.WRITE), owner, 4);

        User user2 = saveUserWithNewOrganization();
        // Why 6? owner, user1, user2, and their TS
        addJoinedUser(sf, user2, Permissions.allOf(Permission.WRITE, Permission.MANAGE), owner, 6);

        // intentionally make a no-op change, no ACL updates needed
        assertEquals(sf.setPermissions(user1, Permissions.allOf(Permission.WRITE)).size(), 0);
        assertJoinedRole(sf, user1, Permissions.allOf(Permission.WRITE));

        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.setPermissions(user2, Permissions.allOf(Permission.WRITE)).size(), 6);
        assertJoinedRole(sf, user2, Permissions.allOf(Permission.WRITE));
    }

    @Test
    public void setRole_shouldNotifyNobodyIfSubjectIsNotJoined()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user = saveUser();
        sf.addPendingUser(user, Permissions.allOf(Permission.WRITE), owner);
        assertEquals(sf.setPermissions(user,
                Permissions.allOf(Permission.WRITE, Permission.MANAGE)).size(), 0);

        // why 0? no joined users are affected
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 0);

        assertEquals(sf.setPermissions(user, Permissions.allOf()).size(), 0);
    }

    @Test
    public void getAllUsersRolesAndStates_shouldListAllUsersRegardlessOfState()
            throws Exception
    {
        User owner = saveUser();
        User pendingMember = saveUser();
        User joinedMember = saveUser();
        User leftMember = saveUser();

        Organization org = owner.getOrganization();
        pendingMember.setOrganization(org, AuthorizationLevel.USER);
        joinedMember.setOrganization(org, AuthorizationLevel.USER);
        leftMember.setOrganization(org, AuthorizationLevel.USER);

        SharedFolder folder = saveSharedFolder(owner);
        folder.addPendingUser(pendingMember, Permissions.allOf(Permission.WRITE), owner);
        folder.addPendingUser(joinedMember, Permissions.allOf(Permission.WRITE), owner);
        folder.addPendingUser(leftMember, Permissions.allOf(Permission.WRITE), owner);

        folder.setState(joinedMember, SharedFolderState.JOINED);
        folder.setState(leftMember, SharedFolderState.JOINED);
        folder.setState(leftMember, SharedFolderState.LEFT);

        ImmutableList<UserPermissionsAndState> expected =
                ImmutableList.of(
                        new UserPermissionsAndState(org.getTeamServerUser(),
                                Permissions.allOf(Permission.WRITE), SharedFolderState.JOINED),
                        new UserPermissionsAndState(owner, Permissions.allOf(Permission.WRITE,
                                Permission.MANAGE), SharedFolderState.JOINED),
                        new UserPermissionsAndState(pendingMember, Permissions.allOf(
                                Permission.WRITE), SharedFolderState.PENDING),
                        new UserPermissionsAndState(joinedMember, Permissions.allOf(
                                Permission.WRITE), SharedFolderState.JOINED),
                        new UserPermissionsAndState(leftMember, Permissions.allOf(Permission.WRITE), SharedFolderState.LEFT)
                );

        assertEquals(expected, folder.getAllUsersRolesAndStates());
    }

    @Test
    public void getAllUsersRolesAndStates_shouldIncludePendingAndNonExistingUsers()
            throws Exception
    {
        User owner = saveUser();
        User nonExistingUser = newUser();

        SharedFolder folder = saveSharedFolder(owner);
        folder.addPendingUser(nonExistingUser, Permissions.allOf(Permission.WRITE), owner);

        ImmutableList<UserPermissionsAndState> expected =
                ImmutableList.of(
                        new UserPermissionsAndState(owner.getOrganization().getTeamServerUser(),
                                Permissions.allOf(Permission.WRITE), SharedFolderState.JOINED),
                        new UserPermissionsAndState(owner, Permissions.allOf(Permission.WRITE,
                                Permission.MANAGE), SharedFolderState.JOINED),
                        new UserPermissionsAndState(nonExistingUser,
                                Permissions.allOf(Permission.WRITE), SharedFolderState.PENDING)
                );

        assertFalse(nonExistingUser.exists());
        assertEquals(expected, folder.getAllUsersRolesAndStates());
    }

    @Test
    public void getAllUsersExceptTeamServers_shouldGetAllUsersExceptTeamServers() throws Exception
    {
        User owner = saveUser();
        User joinedMember = saveUser();

        Organization org = owner.getOrganization();
        joinedMember.setOrganization(org, AuthorizationLevel.USER);

        SharedFolder folder = saveSharedFolder(owner);
        folder.addPendingUser(joinedMember, Permissions.allOf(Permission.WRITE), owner);

        folder.setState(joinedMember, SharedFolderState.JOINED);

        ImmutableList<User> expected = new ImmutableList.Builder<User>()
                .add(owner)
                .add(joinedMember)
                .build();

        assertEquals(expected, folder.getAllUsersExceptTeamServers());
    }

    @Test
    public void shouldThrowExAlreadyExistOnlyIfUserAndGroupMatchAndJoined()
            throws Exception
    {
        User owner = saveUser();
        User user2 = saveUser();
        // foreign key constraint that the organization has to exist
        Organization org = factOrg.save(orgID);
        Group group = factGroup.save("common name", orgID, null);

        SharedFolder folder = saveSharedFolder(owner);
        folder.addUserWithGroup(user2, null, Permissions.EDITOR, owner);
        // won't fail because user hasn't joined yet
        folder.addUserWithGroup(user2, null, Permissions.OWNER, owner);
        folder.setState(user2, SharedFolderState.JOINED);
        try {
            folder.addUserWithGroup(user2, null, Permissions.OWNER, owner);
            fail();
        } catch (ExAlreadyExist e) {}
        folder.addUserWithGroup(user2, group, Permissions.VIEWER, owner);
        try {
            folder.addUserWithGroup(user2, group, Permissions.VIEWER, owner);
            fail();
        } catch (ExAlreadyExist e) {}
    }

    @Test
    public void shouldRemoveOneMembershipAtATime()
            throws Exception
    {
        User owner = saveUser();
        User user2 = saveUser();
        Organization org = factOrg.save(orgID);
        Group group = factGroup.save("common name", orgID, null);

        SharedFolder folder = saveSharedFolder(owner);
        folder.addUserWithGroup(user2, null, Permissions.EDITOR, owner);
        folder.setState(user2, SharedFolderState.JOINED);
        folder.addUserWithGroup(user2, group, Permissions.EDITOR, owner);
        assertEquals(folder.getStateNullable(user2), SharedFolderState.JOINED);
        folder.removeIndividualUser(user2);
        assertEquals(folder.getStateNullable(user2), SharedFolderState.JOINED);
        folder.removeUserWithGroup(user2, group);
        assertEquals(folder.getStateNullable(user2), null);
    }

    private User getTeamServerUser(User user)
            throws ExNotFound, SQLException
    {
        return user.getOrganization().getTeamServerUser();
    }

    private SharedFolder saveUserAndSharedFolder()
            throws Exception
    {
        return saveUserAndSharedFolder(SID.generate());
    }

    private SharedFolder saveUserAndSharedFolder(SID sid)
            throws Exception
    {
        User owner = saveUser();
        return saveSharedFolder(sid, owner);
    }

    private void addJoinedUser(SharedFolder sf, User user, Permissions permissions, User sharer,
            int usersExpectedToBeAffected)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        sf.addPendingUser(user, permissions, sharer);
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), usersExpectedToBeAffected);
    }
}