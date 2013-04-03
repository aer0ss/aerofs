/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.acl.Role;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.sql.SQLException;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

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
            throws ExNoPerm, ExNotFound, SQLException
    {
        newSharedFolder().getName();
    }

    @Test(expected = AssertionError.class)
    public void delete_shouldAssertIfSharedFolderNotFound()
            throws SQLException
    {
        newSharedFolder().delete();
    }

    @Test
    public void delete_shouldNotFindAfterDeletion()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();
        assertTrue(sf.exists());
        sf.delete();
        assertFalse(sf.exists());
    }

    @Test
    public void addMemberACL_shouldThrowExAlreadyExistsIfSubjectExists()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        sf.addMemberACL(user, Role.EDITOR);

        sqlTrans.commit();
        sqlTrans.begin();

        try {
            sf.addMemberACL(user, Role.OWNER);
            assertTrue(false);
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
            sqlTrans.begin();
        }

        assertEquals(Role.EDITOR, sf.getMemberRoleNullable(user));
    }

    @Test
    public void shaveSharedFolder_shouldAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(owner)), Role.EDITOR);
    }

    @Test
    public void addMemberACL_shouldAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.addMemberACL(user, Role.EDITOR).size(), 4);
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user)), Role.EDITOR);

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.getMembers().size(), 4);
    }

    @Test
    public void addPendingACL_shouldNotAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        sf.addPendingACL(owner, user, Role.EDITOR);
        assertNull(sf.getMemberRoleNullable(getTeamServerUser(user)));

        // why 2? owner, owner's team server
        assertEquals(sf.getMembers().size(), 2);
    }

    @Test
    public void setPending_shouldRemoveTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        sf.addMemberACL(user, Role.EDITOR);
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user)), Role.EDITOR);

        sf.setPending(user);
        assertNull(sf.getMemberRoleNullable(getTeamServerUser(user)));
    }

    @Test
    public void setMember_shouldAddTeamServer()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);
        User user = saveUser();

        sf.addPendingACL(owner, user, Role.EDITOR);
        assertNull(sf.getMemberRoleNullable(getTeamServerUser(user)));

        sf.setMember(user);
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user)), Role.EDITOR);
    }

    @Test
    public void addTeamServerACL_shouldNotAddIfAlreadyExists()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user1 = saveUser();
        User user2 = saveUser();
        // Move the two users to the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        sf.addTeamServerACL(user1);
        List<User> users = Lists.newArrayList(sf.getMembers());

        sf.addTeamServerACL(user2);
        assertEquals(users, sf.getMembers());

        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user1)), Role.EDITOR);
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user2)), Role.EDITOR);
    }

    @Test
    public void deleteMemberOrPendingACL_shouldThrowIfNoOwnerLeft()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUser();
        sf.addMemberACL(user1, Role.OWNER);
        sf.updateMemberACL(owner, Role.EDITOR);

        try {
            sf.deleteMemberOrPendingACL(user1);
            assertTrue(false);
        } catch (ExNoAdminOrOwner e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void deleteMemberOrPendingACL_shouldThrowIfNoUserNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        try {
            sf.deleteMemberOrPendingACL(user);
            assertTrue(false);
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void deleteMemberOrPendingACL_shouldDeleteTeamServer()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user1 = saveUser();
        User user2 = saveUser();
        // Move the two users into the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        User tsUser = getTeamServerUser(user1);
        assertEquals(getTeamServerUser(user2), tsUser);

        sf.addMemberACL(user1, Role.EDITOR);
        assertEquals(sf.getMemberRoleNullable(tsUser), Role.EDITOR);

        sf.addMemberACL(user2, Role.OWNER);
        assertEquals(sf.getMemberRoleNullable(tsUser), Role.EDITOR);

        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server id
        assertEquals(sf.deleteMemberOrPendingACL(user1).size(), 5);

        assertNull(sf.getMemberRoleNullable(user1));

        // since user1 & 2 share the same org, the team server shouldn't have been removed.
        assertEquals(sf.getMemberRoleNullable(tsUser), Role.EDITOR);

        // why 4? owner, user2, owner's team server, user2's team server id
        assertEquals(sf.deleteMemberOrPendingACL(user2).size(), 4);

        assertNull(sf.getMemberRoleNullable(user2));

        assertNull(sf.getMemberRoleNullable(tsUser));
    }

    @Test(expected = AssertionError.class)
    public void deleteTeamServerACL_shouldAssertIfTeamServerNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        sf.deleteTeamServerACL(user);
    }

    @Test
    public void delateTeamServerACL_shouldNotDeleteIfOtherUserInSameOrg()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user1 = saveUser();
        User user2 = saveUser();
        // Move the two users into the same org
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);

        sf.addMemberACL(user1, Role.OWNER);
        sf.addMemberACL(user2, Role.EDITOR);

        // delete user1's team server with user2 being in the same org
        assertEquals(sf.deleteTeamServerACL(user1).size(), 0);

        // the team server should remain
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user1)), Role.EDITOR);

        // now, delete user2
        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server
        assertEquals(sf.deleteMemberOrPendingACL(user2).size(), 5);

        // why 4? owner, user1, owner's team server, user1's team server
        assertEquals(sf.deleteTeamServerACL(user1).size(), 4);

        // the team server should go away
        assertNull(sf.getMemberRoleNullable(getTeamServerUser(user1)));
    }

    @Test
    public void updateMemberACL_shouldThrowIfNoOwnerLeft()
            throws Exception
    {
        User owner = saveUser();
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = saveUser();
        sf.addMemberACL(user1, Role.OWNER);

        // why 4? owner, user, owner's team server, user's team server
        assertEquals(sf.updateMemberACL(owner, Role.EDITOR).size(), 4);

        try {
            sf.updateMemberACL(user1, Role.EDITOR);
            assertTrue(false);
        } catch (ExNoAdminOrOwner e) {
            sqlTrans.handleException();
        }
    }

    @Test(expected = ExNotFound.class)
    public void updateMemberACL_shouldThrowIfNoUserNotFound()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user = saveUser();

        sf.updateMemberACL(user, Role.EDITOR);
    }

    @Test
    public void updateMemberACL_shouldUpdate()
            throws Exception
    {
        SharedFolder sf = saveUserAndSharedFolder();

        User user1 = saveUser();
        sf.addMemberACL(user1, Role.EDITOR);

        User user2 = saveUser();
        sf.addMemberACL(user2, Role.OWNER);

        // intentionally make a no-op change
        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.updateMemberACL(user1, Role.EDITOR).size(), 6);
        assertEquals(sf.getMemberRoleNullable(user1), Role.EDITOR);

        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.updateMemberACL(user2, Role.EDITOR).size(), 6);
        assertEquals(sf.getMemberRoleNullable(user2), Role.EDITOR);
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
}