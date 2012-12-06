/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
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
    public void createNewSharedFolder_shouldThrowOnDuplicate()
            throws ExNoPerm, IOException, ExNotFound, ExAlreadyExist, SQLException
    {
        SID sid = new SID(UniqueID.generate());
        createNewUserAndOrgAndSharedFolder(sid);

        User user = newUser();
        createNewUser(user, createNewOrg());
        createNewSharedFolder(sid, user);
    }

    @Test
    public void getName_shouldReturnCorrectNameAfterCreation()
            throws IOException, ExNotFound, SQLException, ExNoPerm, ExAlreadyExist
    {
        final String NAME = "haha";

        SharedFolder sf = newSharedFolder();
        User user = newUser();
        createNewUser(user, createNewOrg());
        sf.createNewSharedFolder(NAME, user);
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
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();
        assertTrue(sf.exists());
        sf.delete();
        assertFalse(sf.exists());
    }

    @Test
    public void addACL_shouldDoNothingIfSubjectExists()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user = newUser();
        createNewUser(user, createNewOrg());

        sf.addACL(user, Role.EDITOR);

        assertTrue(sf.addACL(user, Role.OWNER).isEmpty());

        // To the implemantor of the User class: shouldn't we update the existing role?
        assertEquals(sf.getRoleNullable(user), Role.EDITOR);
    }

    @Test
    public void addACL_shouldAddTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        createNewUser(owner, createNewOrg());
        SharedFolder sf = createNewSharedFolder(owner);

        assertEquals(sf.getRoleNullable(getTeamServerUser(owner)), Role.EDITOR);

        User user = newUser();
        createNewUser(user, createNewOrg());

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.addACL(user, Role.EDITOR).size(), 4);
        assertEquals(sf.getRoleNullable(getTeamServerUser(user)), Role.EDITOR);

        // why 4? owner, user, owner's team server, user's team server id
        assertEquals(sf.getUsers().size(), 4);
    }

    @Test
    public void addTeamServerACL_shouldNotAddDefaultOrgTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();
        List<User> users = Lists.newArrayList(sf.getUsers());

        User user = newUser();
        createNewUser(user, factOrg.getDefault());
        User tsUser = getTeamServerUser(user);

        sf.addTeamServerACL(user);
        assertNull(sf.getRoleNullable(tsUser));
        assertEquals(users, sf.getUsers());
    }

    @Test
    public void addTeamServerACL_shouldNotAddIfAlreadyExists()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        Organization org = createNewOrg();
        User user1 = newUser();
        createNewUser(user1, org);

        User user2 = newUser();
        createNewUser(user2, org);

        sf.addTeamServerACL(user1);
        List<User> users = Lists.newArrayList(sf.getUsers());

        sf.addTeamServerACL(user2);
        assertEquals(users, sf.getUsers());

        assertEquals(sf.getRoleNullable(getTeamServerUser(user1)), Role.EDITOR);
        assertEquals(sf.getRoleNullable(getTeamServerUser(user2)), Role.EDITOR);
    }

    @Test(expected = ExNoPerm.class)
    public void deleteACL_shouldThrowIfNoOwnerLeft()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        createNewUser(owner, createNewOrg());
        SharedFolder sf = createNewSharedFolder(owner);

        User user1 = newUser();
        createNewUser(user1, createNewOrg());
        sf.addACL(user1, Role.OWNER);
        sf.updateACL(singleSRP(owner, Role.EDITOR));

        sf.deleteACL(Collections.singleton(user1.id()));
    }

    @Test(expected = ExNotFound.class)
    public void deleteACL_shouldThrowIfNoUserNotFound()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user = newUser();
        createNewUser(user, createNewOrg());

        sf.deleteACL(Collections.singleton(user.id()));
    }

    @Test
    public void deleteACL_shouldDeleteTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user1 = newUser();
        createNewUser(user1, createNewOrg());
        User tsUser = getTeamServerUser(user1);

        User user2 = newUser();
        createNewUser(user2, user1.getOrganization());
        assertEquals(getTeamServerUser(user2), tsUser);

        sf.addACL(user1, Role.EDITOR);
        assertEquals(sf.getRoleNullable(tsUser), Role.EDITOR);

        sf.addACL(user2, Role.OWNER);
        assertEquals(sf.getRoleNullable(tsUser), Role.EDITOR);

        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server id
        assertEquals(sf.deleteACL(Collections.singleton(user1.id())).size(), 5);

        assertNull(sf.getRoleNullable(user1));

        // since user1 & 2 share the same org, the team server shouldn't have been removed.
        assertEquals(sf.getRoleNullable(tsUser), Role.EDITOR);

        // why 4? owner, user2, owner's team server, user2's team server id
        assertEquals(sf.deleteACL(Collections.singleton(user2.id())).size(), 4);

        assertNull(sf.getRoleNullable(user2));

        assertNull(sf.getRoleNullable(tsUser));
    }

    @Test
    public void deleteTeamServerACL_shouldNotDeleteDefaultOrgTeamServer()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user = newUser();
        createNewUser(user, factOrg.getDefault());
        sf.addACL(user, Role.OWNER);

        Collection<User> users = sf.getUsers();
        assertNull(sf.getRoleNullable(getTeamServerUser(user)));

        assertEquals(sf.deleteTeamServerACL(user).size(), 0);
        assertEquals(users, sf.getUsers());
    }

    @Test(expected = AssertionError.class)
    public void deleteTeamServerACL_shouldAssertIfTeamServerNotFound()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user = newUser();
        createNewUser(user, createNewOrg());

        sf.deleteTeamServerACL(user);
    }

    @Test
    public void delateTeamServerACL_shouldNotDeleteIfOtherUserInSameOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user1 = newUser();
        createNewUser(user1, createNewOrg());
        sf.addACL(user1, Role.OWNER);

        User user2 = newUser();
        createNewUser(user2, user1.getOrganization());
        sf.addACL(user2, Role.EDITOR);

        // delete user1's team server with user2 being in the same org
        assertEquals(sf.deleteTeamServerACL(user1).size(), 0);

        // the team server should remain
        assertEquals(sf.getRoleNullable(getTeamServerUser(user1)), Role.EDITOR);

        // now, delete user2
        // why 5? owner, user1, user2, owner's team server, user1 & 2's team server
        assertEquals(sf.deleteACL(Collections.singletonList(user2.id())).size(), 5);

        // why 4? owner, user1, owner's team server, user1's team server
        assertEquals(sf.deleteTeamServerACL(user1).size(), 4);

        // the team server should go away
        assertNull(sf.getRoleNullable(getTeamServerUser(user1)));
    }

    @Test(expected = ExNoPerm.class)
    public void updateACL_shouldThrowIfNoOwnerLeft()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        createNewUser(owner, createNewOrg());
        SharedFolder sf = createNewSharedFolder(owner);

        User user1 = newUser();
        createNewUser(user1, createNewOrg());
        sf.addACL(user1, Role.OWNER);

        // why 4? owner, user, owner's team server, user's team server
        assertEquals(sf.updateACL(singleSRP(owner, Role.EDITOR)).size(), 4);

        sf.updateACL(singleSRP(user1, Role.EDITOR));
    }

    @Test(expected = ExNotFound.class)
    public void updateACL_shouldThrowIfNoUserNotFound()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user = newUser();
        createNewUser(user, createNewOrg());

        sf.updateACL(singleSRP(user, Role.EDITOR));
    }

    @Test
    public void updateACL_shouldUpdate()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = createNewUserAndOrgAndSharedFolder();

        User user1 = newUser();
        createNewUser(user1, createNewOrg());
        sf.addACL(user1, Role.EDITOR);

        User user2 = newUser();
        createNewUser(user2, createNewOrg());
        sf.addACL(user2, Role.OWNER);

        // intentionally make a no-op change
        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.updateACL(singleSRP(user1, Role.EDITOR)).size(), 6);
        assertEquals(sf.getRoleNullable(user1), Role.EDITOR);

        // wh 6? owner, user1, user2, and their perspective team servers
        assertEquals(sf.updateACL(singleSRP(user2, Role.EDITOR)).size(), 6);
        assertEquals(sf.getRoleNullable(user2), Role.EDITOR);
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

    private SharedFolder createNewUserAndOrgAndSharedFolder()
            throws ExNoPerm, IOException, ExNotFound, ExAlreadyExist, SQLException
    {
        return createNewUserAndOrgAndSharedFolder(new SID(UniqueID.generate()));
    }

    private SharedFolder createNewUserAndOrgAndSharedFolder(SID sid)
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User owner = newUser();
        createNewUser(owner, createNewOrg());
        return createNewSharedFolder(sid, owner);
    }
}