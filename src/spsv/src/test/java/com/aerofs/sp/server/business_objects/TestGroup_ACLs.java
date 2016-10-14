/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestGroup_ACLs extends AbstractBusinessObjectTest
{

    final OrganizationID orgId = new OrganizationID(1);
    Organization org;
    Group group;
    // Shared folder.
    final SID sid = SID.generate();
    SharedFolder sf;
    User owner, user2, user3;

    @Before
    public void setup()
            throws Exception
    {
        org = factOrg.save(orgId);
        group = factGroup.save("Common Name", orgId, null);

        // Create test users.
        owner = saveUser();
        user2 = saveUserWithNewOrganization();
        user3 = saveUserWithNewOrganization();

        // Test shared folder.
        sf = factSharedFolder.create(sid);
        sf.save("Shared Folder Name", owner);
    }

    @Test
    public void shouldReportSharedFoldersAndRolesAsInput()
            throws Exception
    {
        group.joinSharedFolder(sf, Permissions.VIEWER, owner);
        ImmutableCollection<SharedFolder> sfs = group.listSharedFolders();
        assertEquals(sfs.size(), 1);
        assertTrue(sfs.contains(sf));
        assertTrue(group.inSharedFolder(sf));
        assertEquals(group.getRoleForSharedFolder(sf), Permissions.VIEWER);
        group.deleteSharedFolder(sf);
        sfs = group.listSharedFolders();
        assertEquals(sfs.size(), 0);
        assertFalse(sfs.contains(sf));
        assertFalse(group.inSharedFolder(sf));
        try {
            group.getRoleForSharedFolder(sf);
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void shouldInviteExistingMembers()
            throws Exception
    {
        group.addMember(user2);
        group.joinSharedFolder(sf, Permissions.EDITOR, owner);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.PENDING, Permissions.EDITOR);
    }

    @Test
    public void shouldInviteNewMembers()
            throws Exception
    {
        group.joinSharedFolder(sf, Permissions.EDITOR, owner);
        group.addMember(user2);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.PENDING, Permissions.EDITOR);
    }

    @Test
    public void shouldResetLeftMembersToPending()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.EDITOR, owner);
        sf.setState(user2, SharedFolderState.LEFT);
        group.joinSharedFolder(sf, Permissions.EDITOR, owner);
        group.addMember(user2);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.PENDING, Permissions.EDITOR);
    }

    @Test
    public void shouldNotErrorOnAlreadyJoinedUsers()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.EDITOR, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        group.joinSharedFolder(sf, Permissions.EDITOR, owner);
        group.addMember(user2);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.EDITOR);
    }

    @Test
    public void shouldCalculateEffectiveRoles()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.allOf(Permission.MANAGE), owner);
        sf.setState(user2, SharedFolderState.JOINED);
        group.joinSharedFolder(sf, Permissions.allOf(Permission.WRITE), owner);
        group.addMember(user2);
        group.addMember(user3);
        // effective role should be calculated by the combination of all granted permissions
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED,
                Permissions.allOf(Permission.WRITE, Permission.MANAGE));
        assertUserInFolderWithStateAndRole(user3, sf, SharedFolderState.PENDING,
                Permissions.allOf(Permission.WRITE));
        group.deleteSharedFolder(sf);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.allOf(Permission.MANAGE));
        assertUserNotInFolder(user3, sf);
    }

    @Test
    public void shouldNotSendEmailsToJoinedUsers()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.allOf(Permission.MANAGE), owner);
        sf.setState(user2, SharedFolderState.JOINED);
        // empty group shouldn't need to send any emails
        assertEquals(group.joinSharedFolder(sf, Permissions.EDITOR, owner)._users.size(), 0);
        // user2 is already in folder, no need for an email
        assertTrue(group.addMember(user2)._folders.isEmpty());
        // user3 does need one though
        assertFalse(group.addMember(user3)._folders.isEmpty());
    }

    @Test
    public void shouldUpdateAclsOnDelete()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.VIEWER, owner);
        group.joinSharedFolder(sf, Permissions.EDITOR, owner);
        group.addMember(user2);
        group.addMember(user3);
        sf.setState(user2, SharedFolderState.JOINED);
        sf.setState(user3, SharedFolderState.JOINED);
        // all user clients and their TS need to update since user3's ACL will change
        assertEquals(group.removeMember(user3, null).size(), 6);
        // the remaining user2 and owner's clients and TS need to update
        assertEquals(group.deleteSharedFolder(sf).size(), 4);
        // still have user2 and owner's clients plus TS in the folder though
        assertEquals(sf.getAllUsers().size(), 4);
    }

    @Test
    public void shouldReinviteLeftUsers()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.VIEWER, owner);
        sf.setState(user2, SharedFolderState.LEFT);
        group.addMember(user2);
        // would need to send 1 invitation to user2 since they have been reset to pending
        assertEquals(group.joinSharedFolder(sf, Permissions.EDITOR, owner)._users.size(), 1);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.PENDING, Permissions.EDITOR);
        sf.setState(user2, SharedFolderState.JOINED);
        // owner and user2's client and team server needs to know that its permissions changed
        assertEquals(group.deleteSharedFolder(sf).size(), 4);
    }

    @Test
    public void shouldOnlyUpdateACLsOnChange()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.VIEWER, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        group.addMember(user2);
        assertTrue(group.joinSharedFolder(sf, Permissions.VIEWER, owner)._affected.isEmpty());
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.VIEWER);
        Group group2 = factGroup.save("Another Common Name", orgId, null);
        group2.addMember(user2);
        assertFalse(group2.joinSharedFolder(sf, Permissions.EDITOR, owner)._affected.isEmpty());
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.EDITOR);
        assertTrue(group.deleteSharedFolder(sf).isEmpty());
        assertFalse(group2.deleteSharedFolder(sf).isEmpty());
    }

    @Test
    public void groupRoleChangesShouldAffectEffectiveRoles()
            throws Exception
    {
        sf.addUserWithGroup(user2, null, Permissions.VIEWER, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        group.joinSharedFolder(sf, Permissions.OWNER, owner);
        group.addMember(user2);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.OWNER);
        group.changeRoleInSharedFolder(sf, Permissions.EDITOR);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.EDITOR);
        sf.setPermissions(user2, Permissions.allOf(Permission.MANAGE));
        // editor + manage permissions are equivalent to owner
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED, Permissions.OWNER);
        assertEquals(sf.getPermissionsInGroupNullable(user2, group), Permissions.EDITOR);
        group.deleteSharedFolder(sf);
        assertUserInFolderWithStateAndRole(user2, sf, SharedFolderState.JOINED,
                Permissions.allOf(Permission.MANAGE));
    }

    @Test
    public void removingLastOwnerFromGroupThrowsException()
            throws Exception
    {
        group.addMember(user2);
        group.addMember(user3);
        group.joinSharedFolder(sf, Permissions.OWNER, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        sf.setState(user3, SharedFolderState.JOINED);
        sf.setPermissions(owner, Permissions.EDITOR);

        // can remove this owner as user2 is still a member
        group.removeMember(user3, null);
        try {
            group.removeMember(user2, null);
            fail();
        } catch (ExNoAdminOrOwner e) {}

        try {
            group.deleteSharedFolder(sf);
            fail();
        } catch (ExNoAdminOrOwner e) {}
    }

    @Test
    public void canRemoveGroupIfNoOtherMembers()
            throws Exception
    {
        group.addMember(user2);
        group.addMember(user3);

        group.joinSharedFolder(sf, Permissions.OWNER, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        sf.removeIndividualUser(owner);
        sf.setState(user3, SharedFolderState.JOINED);

        // 2 users and their respective TS
        assertEquals(group.deleteSharedFolder(sf).size(), 4);
    }

    @Test
    public void canRemoveGroupWithMultipleMembersFromSameOrg()
            throws Exception
    {
        user3.setOrganization(user2.getOrganization(), AuthorizationLevel.USER);
        group.addMember(user2);
        group.addMember(user3);

        group.joinSharedFolder(sf, Permissions.OWNER, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        sf.setState(user3, SharedFolderState.JOINED);

        group.deleteSharedFolder(sf);
    }

    @Test
    public void removedMembersShouldNotShowUpInGroupSFStatus()
            throws Exception
    {
        group.addMember(user2);
        group.addMember(user3);
        group.joinSharedFolder(sf, Permissions.EDITOR, owner);
        sf.setState(user2, SharedFolderState.JOINED);
        List<UserPermissionsAndState> groupStatus =
                Lists.newArrayList(sf.getUserRolesAndStatesForGroup(group));
        assertEquals(groupStatus.size(), 2);
        assertTrue(groupStatus.contains(new UserPermissionsAndState(user2, Permissions.EDITOR, SharedFolderState.JOINED)));
        assertTrue(groupStatus.contains(new UserPermissionsAndState(user3, Permissions.EDITOR, SharedFolderState.PENDING)));

        group.removeMember(user3, null);
        groupStatus = Lists.newArrayList(sf.getUserRolesAndStatesForGroup(group));
        assertEquals(groupStatus.size(), 1);
        assertTrue(groupStatus.contains(new UserPermissionsAndState(user2, Permissions.EDITOR, SharedFolderState.JOINED)));
    }

    private void assertUserInFolderWithStateAndRole(User u, SharedFolder sf, SharedFolderState state, Permissions role)
            throws Exception
    {
        for (UserPermissionsAndState ups : sf.getAllUsersRolesAndStates()) {
            if (ups._user.equals(u)) {
                assertEquals(ups._permissions, role);
                assertEquals(ups._state, state);
                return;
            }
        }
        fail("User not in this shared folder");
    }

    private void assertUserNotInFolder(User u, SharedFolder sf)
            throws Exception
    {
        if (sf.getAllUsers().contains(u)) {
            fail("User found in this shared folder");
        }
    }

}
