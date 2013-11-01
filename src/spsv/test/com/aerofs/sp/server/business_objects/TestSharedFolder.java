/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
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
        User user = saveUser();
        sf.save(NAME, user);
        assertEquals(sf.getName(), NAME);
    }

    @Test(expected = ExNotFound.class)
    public void getName_shouldThrowIfFolderNotFound()
            throws ExNotFound, SQLException
    {
        newSharedFolder().getName();
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
        sf.destroy();
        assertFalse(sf.exists());
    }

    @Test
    public void saveSharedFolder_shouldAddOwnerWithCorrectFields()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(SID.generate(), owner);
        assertJoinedRole(sf, owner, Role.OWNER);
        assertNull(sf.getSharerNullable(owner));
    }

    @Test
    public void saveSharedFolder_shouldAddTeamServerWithCorrectFields()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User tsUser = getTeamServerUser(owner);
        assertJoinedRole(sf, tsUser, Role.EDITOR);
        assertNull(sf.getSharerNullable(tsUser));
    }

    @Test
    public void shouldAllowTeamServerIfOneMemberOfSameOrgIsOWNER() throws Exception
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
        User user = saveUser();

        User ts = user.getOrganization().getTeamServerUser();
        try {
            sf.throwIfNoPrivilegeToChangeACL(ts);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldRejectTeamServerIfNoJoinedUsersOfSameOrgIsOWNER() throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();
        // why 4? owner, user, and their team servers
        addJoinedUser(sf, user, Role.EDITOR, owner, 4);

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

        sf.addPendingUser(user, Role.EDITOR, sharer);
        // why 0? no joined user is affected
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 0);

        // commit the transaction so the sqlTrans.handleException() below won't rollback the changes
        // we made so far.
        sqlTrans.commit();
        sqlTrans.begin();

        try {
            sf.addPendingUser(user, Role.OWNER, sharer);
            assertTrue(false);
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
            sqlTrans.begin();
        }

        assertEquals(Role.EDITOR, sf.getRoleNullable(user));
        assertEquals(SharedFolderState.LEFT, sf.getStateNullable(user));
    }

    @Test
    public void addPendingUser_shouldNotAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        sf.addPendingUser(user, Role.EDITOR, owner);
        assertNull(sf.getRoleNullable(getTeamServerUser(user)));

        // why 2? owner, owner's team server
        assertEquals(sf.getJoinedUsers().size(), 2);
    }

    @Test
    public void addPendingUser_shouldSetSharer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        sf.addPendingUser(user, Role.EDITOR, owner);
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
        User user = saveUser();

        // why 4? owner, user, and their team servers
        addJoinedUser(sf, user, Role.EDITOR, owner, 4);
        User tsUser = getTeamServerUser(user);
        assertEquals(sf.getRoleNullable(tsUser), Role.EDITOR);

        // why 4? owner, user, and their team servers
        assertEquals(sf.setState(user, state).size(), 4);
        assertNull(sf.getRoleNullable(tsUser));
    }

    @Test
    public void setStateAwayFromJoined_shouldNotRemoveTeamServerIfOWNER()
            throws Exception
    {
        User user = saveUser();
        User tsUser = getTeamServerUser(user);
        SharedFolder sf = saveSharedFolder(tsUser);
        assertJoinedRole(sf, tsUser, Role.OWNER);

        // make sure TS not downgraded by adding a user
        // why 2? the team server (the owner) and the user
        addJoinedUser(sf, user, Role.EDITOR, tsUser, 2);
        assertJoinedRole(sf, tsUser, Role.OWNER);

        // make sure TS not kicked out when last org member leaves
        // why 2? the team server (the owner) and the user
        assertEquals(sf.setState(user, SharedFolderState.LEFT).size(), 2);
        // why 0? because no state changes for joined users
        assertEquals(sf.setState(user, SharedFolderState.PENDING).size(), 0);
        assertJoinedRole(sf, tsUser, Role.OWNER);
    }

    @Test
    public void setStateToJoined_shouldAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user = saveUser();
        sf.addPendingUser(user, Role.EDITOR, owner);
        User tsUser = getTeamServerUser(user);
        assertNull(sf.getRoleNullable(tsUser));

        // why 4? owner, user, owner's team server, user's team server
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), 4);

        assertJoinedRole(sf, tsUser, Role.EDITOR);

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.getJoinedUsers().size(), 4);
    }

    @Test
    public void addTeamServerForUser_shouldNotAddIfAlreadyExists()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user1 = saveUser();
        User user2 = saveUser();
        // Move the two users to the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        sf.addTeamServerForUser(user1);
        List<User> users = Lists.newArrayList(sf.getJoinedUsers());

        sf.addTeamServerForUser(user2);
        assertEquals(users, sf.getJoinedUsers());

        assertJoinedRole(sf, getTeamServerUser(user1), Role.EDITOR);
        assertJoinedRole(sf, getTeamServerUser(user2), Role.EDITOR);
    }

    @Test
    public void removeUser_shouldThrowIfNoOwnerLeft()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUser();
        // why 4? owner, user, and their team servers
        addJoinedUser(sf, user1, Role.OWNER, owner, 4);
        sf.setRole(owner, Role.EDITOR);

        try {
            sf.removeUser(user1);
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
            sf.removeUser(user);
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

        User user1 = saveUser();
        User user2 = saveUser();
        // Move the two users into the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        User tsUser = getTeamServerUser(user1);
        assertEquals(getTeamServerUser(user2), tsUser);

        // Why 4? owner, user1, owner's TS, and user1/2's TS
        addJoinedUser(sf, user1, Role.EDITOR, owner, 4);
        assertJoinedRole(sf, tsUser, Role.EDITOR);

        // Why 5? owner, user1, user2, owner's TS, and user1/2's TS
        addJoinedUser(sf, user2, Role.OWNER, owner, 5);
        assertJoinedRole(sf, tsUser, Role.EDITOR);

        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server id
        assertEquals(sf.removeUser(user1).size(), 5);

        assertNull(sf.getRoleNullable(user1));

        // since user1 & 2 share the same org, the team server shouldn't have been removed.
        assertJoinedRole(sf, tsUser, Role.EDITOR);

        // why 4? owner, user2, owner's team server, user2's team server id
        assertEquals(sf.removeUser(user2).size(), 4);

        assertNull(sf.getRoleNullable(user2));

        assertNull(sf.getRoleNullable(tsUser));
    }

    @Test
    public void removeUser_shouldNotRemoveTeamServerIfOWNER()
            throws Exception
    {
        User user = saveUser();
        User tsUser = getTeamServerUser(user);
        SharedFolder sf = saveSharedFolder(tsUser);
        assertJoinedRole(sf, tsUser, Role.OWNER);

        // make sure TS not downgraded by adding a user
        // Why 2? the team server (the owner) and the user
        addJoinedUser(sf, user, Role.EDITOR, tsUser, 2);
        assertJoinedRole(sf, tsUser, Role.OWNER);

        // make sure TS not kicked out when last org member leaves
        sf.removeUser(user);
        assertJoinedRole(sf, tsUser, Role.OWNER);
    }

    @Test
    public void removeTeamServerForUser_shouldThrowIfTeamServerNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

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

        User user1 = saveUser();
        User user2 = saveUser();
        // Move the two users into the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        // Why 4? owner, user1, owner's TS, and user1/2's TS
        addJoinedUser(sf, user1, Role.OWNER, owner, 4);
        // Why 5? owner, user1, user2, owner's TS, and user1/2's TS
        addJoinedUser(sf, user2, Role.EDITOR, owner, 5);

        // remove user1's team server with user2 being in the same org
        assertEquals(sf.removeTeamServerForUser(user1).size(), 0);

        // the team server should remain
        assertJoinedRole(sf, getTeamServerUser(user1), Role.EDITOR);

        // now, remove user2
        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server
        assertEquals(sf.removeUser(user2).size(), 5);

        // why 4? owner, user1, owner's team server, user1's team server
        assertEquals(sf.removeTeamServerForUser(user1).size(), 4);

        // the team server should go away
        assertNull(sf.getRoleNullable(getTeamServerUser(user1)));
    }

    @Test
    public void setRole_shouldThrowIfNoOwnerLeft()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUser();
        // Why 4? owner, user, and their TS
        addJoinedUser(sf, user1, Role.OWNER, owner, 4);

        // why 4? owner, user, and their TS
        assertEquals(sf.setRole(owner, Role.EDITOR).size(), 4);

        try {
            sf.setRole(user1, Role.EDITOR);
            assertTrue(false);
        } catch (ExNoAdminOrOwner e) {
            sqlTrans.handleException();
        }
    }

    @Test(expected = ExNotFound.class)
    public void setRole_shouldThrowIfNoUserNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        sf.setRole(user, Role.EDITOR);
    }

    @Test
    public void setRole_shouldUpdateRoleAsExpected()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUser();
        // Why 4? owner, user, and their TS
        addJoinedUser(sf, user1, Role.EDITOR, owner, 4);

        User user2 = saveUser();
        // Why 6? owner, user1, user2, and their TS
        addJoinedUser(sf, user2, Role.OWNER, owner, 6);

        // intentionally make a no-op change
        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.setRole(user1, Role.EDITOR).size(), 6);
        assertJoinedRole(sf, user1, Role.EDITOR);

        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.setRole(user2, Role.EDITOR).size(), 6);
        assertJoinedRole(sf, user2, Role.EDITOR);
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

    private void addJoinedUser(SharedFolder sf, User user, Role role, User sharer,
            int usersExpectedToBeAffected)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        sf.addPendingUser(user, role, sharer);
        assertEquals(sf.setState(user, SharedFolderState.JOINED).size(), usersExpectedToBeAffected);
    }
}