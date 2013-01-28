/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class TestSharedFolder extends AbstractBusinessObjectTest
{
    @Test(expected = ExAlreadyExist.class)
    public void saveSharedFolder_shouldThrowOnDuplicate()
            throws ExNoPerm, IOException, ExNotFound, ExAlreadyExist, SQLException
    {
        SID sid = SID.generate();
        saveUserAndOrgAndSharedFolder(sid);

        User user = newUser();
        saveUser(user, saveOrganization());
        saveSharedFolder(sid, user);
    }

    @Test
    public void getName_shouldReturnCorrectNameAfterCreation()
            throws IOException, ExNotFound, SQLException, ExNoPerm, ExAlreadyExist
    {
        final String NAME = "haha";

        SharedFolder sf = newSharedFolder();
        User user = newUser();
        saveUser(user, saveOrganization());
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
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();
        assertTrue(sf.exists());
        sf.delete();
        assertFalse(sf.exists());
    }

    @Test
    public void addMemberACL_shouldThrowExAlreadyExistsIfSubjectExists()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user = newUser();
        saveUser(user, saveOrganization());

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
    public void addMemberACL_shouldAddTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        saveUser(owner, saveOrganization());
        SharedFolder sf = saveSharedFolder(owner);

        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(owner)), Role.EDITOR);

        User user = newUser();
        saveUser(user, saveOrganization());

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.addMemberACL(user, Role.EDITOR).size(), 4);
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user)), Role.EDITOR);

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.getMembers().size(), 4);
    }

    @Test
    public void addTeamServerACL_shouldNotAddDefaultOrgTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();
        List<User> users = Lists.newArrayList(sf.getMembers());

        User user = newUser();
        saveUser(user, factOrg.getDefault());
        User tsUser = getTeamServerUser(user);

        sf.addTeamServerACL(user);
        assertNull(sf.getMemberRoleNullable(tsUser));
        assertEquals(users, sf.getMembers());
    }

    @Test
    public void addTeamServerACL_shouldNotAddIfAlreadyExists()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        Organization org = saveOrganization();
        User user1 = newUser();
        saveUser(user1, org);

        User user2 = newUser();
        saveUser(user2, org);

        sf.addTeamServerACL(user1);
        List<User> users = Lists.newArrayList(sf.getMembers());

        sf.addTeamServerACL(user2);
        assertEquals(users, sf.getMembers());

        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user1)), Role.EDITOR);
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user2)), Role.EDITOR);
    }

    @Test
    public void deleteMemberOrPendingACL_shouldThrowIfNoOwnerLeft()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        saveUser(owner, saveOrganization());
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = newUser();
        saveUser(user1, saveOrganization());
        sf.addMemberACL(user1, Role.OWNER);
        sf.updateMemberACL(singleSRP(owner, Role.EDITOR));

        try {
            sf.deleteMemberOrPendingACL(Collections.singleton(user1.id()));
            assertTrue(false);
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void deleteMemberOrPendingACL_shouldThrowIfNoUserNotFound()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user = newUser();
        saveUser(user, saveOrganization());

        try {
            sf.deleteMemberOrPendingACL(Collections.singleton(user.id()));
            assertTrue(false);
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void deleteMemberOrPendingACL_shouldDeleteTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user1 = newUser();
        saveUser(user1, saveOrganization());
        User tsUser = getTeamServerUser(user1);

        User user2 = newUser();
        saveUser(user2, user1.getOrganization());
        assertEquals(getTeamServerUser(user2), tsUser);

        sf.addMemberACL(user1, Role.EDITOR);
        assertEquals(sf.getMemberRoleNullable(tsUser), Role.EDITOR);

        sf.addMemberACL(user2, Role.OWNER);
        assertEquals(sf.getMemberRoleNullable(tsUser), Role.EDITOR);

        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server id
        assertEquals(sf.deleteMemberOrPendingACL(Collections.singleton(user1.id())).size(), 5);

        assertNull(sf.getMemberRoleNullable(user1));

        // since user1 & 2 share the same org, the team server shouldn't have been removed.
        assertEquals(sf.getMemberRoleNullable(tsUser), Role.EDITOR);

        // why 4? owner, user2, owner's team server, user2's team server id
        assertEquals(sf.deleteMemberOrPendingACL(Collections.singleton(user2.id())).size(), 4);

        assertNull(sf.getMemberRoleNullable(user2));

        assertNull(sf.getMemberRoleNullable(tsUser));
    }

    @Test
    public void deleteTeamServerACL_shouldNotDeleteDefaultOrgTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user = newUser();
        saveUser(user, factOrg.getDefault());
        sf.addMemberACL(user, Role.OWNER);

        Collection<User> users = sf.getMembers();
        assertNull(sf.getMemberRoleNullable(getTeamServerUser(user)));

        assertEquals(sf.deleteTeamServerACL(user).size(), 0);
        assertEquals(users, sf.getMembers());
    }

    @Test(expected = AssertionError.class)
    public void deleteTeamServerACL_shouldAssertIfTeamServerNotFound()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user = newUser();
        saveUser(user, saveOrganization());

        sf.deleteTeamServerACL(user);
    }

    @Test
    public void delateTeamServerACL_shouldNotDeleteIfOtherUserInSameOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user1 = newUser();
        saveUser(user1, saveOrganization());
        sf.addMemberACL(user1, Role.OWNER);

        User user2 = newUser();
        saveUser(user2, user1.getOrganization());
        sf.addMemberACL(user2, Role.EDITOR);

        // delete user1's team server with user2 being in the same org
        assertEquals(sf.deleteTeamServerACL(user1).size(), 0);

        // the team server should remain
        assertEquals(sf.getMemberRoleNullable(getTeamServerUser(user1)), Role.EDITOR);

        // now, delete user2
        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server
        assertEquals(sf.deleteMemberOrPendingACL(Collections.singletonList(user2.id())).size(), 5);

        // why 4? owner, user1, owner's team server, user1's team server
        assertEquals(sf.deleteTeamServerACL(user1).size(), 4);

        // the team server should go away
        assertNull(sf.getMemberRoleNullable(getTeamServerUser(user1)));
    }

    @Test
    public void updateMemberACL_shouldThrowIfNoOwnerLeft()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        saveUser(owner, saveOrganization());
        SharedFolder sf = saveSharedFolder(owner);

        User user1 = newUser();
        saveUser(user1, saveOrganization());
        sf.addMemberACL(user1, Role.OWNER);

        // why 4? owner, user, owner's team server, user's team server
        assertEquals(sf.updateMemberACL(singleSRP(owner, Role.EDITOR)).size(), 4);

        try {
            sf.updateMemberACL(singleSRP(user1, Role.EDITOR));
            assertTrue(false);
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
    }

    @Test(expected = ExNotFound.class)
    public void updateMemberACL_shouldThrowIfNoUserNotFound()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user = newUser();
        saveUser(user, saveOrganization());

        sf.updateMemberACL(singleSRP(user, Role.EDITOR));
    }

    @Test
    public void updateMemberACL_shouldUpdate()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = saveUserAndOrgAndSharedFolder();

        User user1 = newUser();
        saveUser(user1, saveOrganization());
        sf.addMemberACL(user1, Role.EDITOR);

        User user2 = newUser();
        saveUser(user2, saveOrganization());
        sf.addMemberACL(user2, Role.OWNER);

        // intentionally make a no-op change
        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.updateMemberACL(singleSRP(user1, Role.EDITOR)).size(), 6);
        assertEquals(sf.getMemberRoleNullable(user1), Role.EDITOR);

        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.updateMemberACL(singleSRP(user2, Role.EDITOR)).size(), 6);
        assertEquals(sf.getMemberRoleNullable(user2), Role.EDITOR);
    }

    private User getTeamServerUser(User user)
            throws ExNotFound, SQLException
    {
        return newUser(user.getOrganization().id().toTeamServerUserID());
    }

    private List<SubjectRolePair> singleSRP(User user, Role role)
    {
        return Collections.singletonList(new SubjectRolePair(user.id(), role));
    }

    private SharedFolder saveUserAndOrgAndSharedFolder()
            throws ExNoPerm, IOException, ExNotFound, ExAlreadyExist, SQLException
    {
        return saveUserAndOrgAndSharedFolder(SID.generate());
    }

    private SharedFolder saveUserAndOrgAndSharedFolder(SID sid)
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        saveUser(owner, saveOrganization());
        return saveSharedFolder(sid, owner);
    }
}