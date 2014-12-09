/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.SID;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder.GroupPermissions;
import com.aerofs.sp.server.lib.sf.SharedFolder.UserPermissionsAndState;
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

public class TestGroupShares extends AbstractBusinessObjectTest
{
    // Organization.
    final OrganizationID orgId = new OrganizationID(1);
    Organization org;
    // Group.
    final GroupID gid = GroupID.fromInternal(1);
    Group group;
    // Shared folder.
    final SID sid = SID.generate();
    SharedFolder sf;

    User owner, user1, user2;

    @Before
    public void setup() throws Exception
    {
        // Create test users.
        owner = saveUser();
        user1 = saveUser();
        user2 = saveUser();

        // Test organization.
        org = factOrg.save(orgId);

        // Test group.
        group = factGroup.save(gid, "Common Name", orgId, null);

        // Test shared folder.
        sf = factSharedFolder.create(sid);
        sf.save("Shared Folder Name", owner);
    }

    @Test
    public void shouldSuccessfullyJoinSharedFolder()
            throws Exception
    {
        group.joinSharedFolder(sf, Permissions.EDITOR, null);
    }

    @Test
    public void shouldFailToJoinFolderTwice()
            throws Exception
    {
        group.joinSharedFolder(sf, Permissions.EDITOR, null);
        try {
            group.joinSharedFolder(sf, Permissions.EDITOR, null);
            fail();
        } catch (ExAlreadyExist e) {}
    }

    @Test
    public void shouldReportJoinedStatusCorrectly()
            throws Exception
    {
        assertFalse(group.inSharedFolder(sf));
        group.joinSharedFolder(sf, Permissions.EDITOR, null);
        assertTrue(group.inSharedFolder(sf));
        for (GroupPermissions gp : sf.getAllGroupsAndRoles()) {
            if (gp._group.equals(group)) {
                return;
            }
        }
        fail("group did not successfully join folder");
    }

    @Test
    public void shouldReportRoleCorrectly()
            throws Exception
    {
        group.joinSharedFolder(sf, Permissions.EDITOR, null);
        assertEquals(Permissions.EDITOR, group.getRoleForSharedFolder(sf));
        for (GroupPermissions gp : sf.getAllGroupsAndRoles()) {
            if (gp._group.equals(group)) {
                assertEquals(gp._permissions, Permissions.EDITOR);
                return;
            }
        }
        fail("group did not successfully join folder");
    }

    @Test
    public void shouldReportJoinedFoldersListCorrectly()
            throws Exception
    {
        assertEquals(0, group.listMembers().size());
        group.joinSharedFolder(sf, Permissions.EDITOR, null);

        ImmutableCollection<SharedFolder> sfList = group.listSharedFolders();

        assertEquals(1, sfList.size());
        assertTrue(sfList.contains(sf));
    }

    @Test
    public void shouldReportStatusInSharedFolder()
            throws Exception
    {
        group.addMember(user1);
        group.joinSharedFolder(sf, Permissions.EDITOR, null);
        List<UserPermissionsAndState> groupStatus =
                Lists.newArrayList(sf.getUserRolesAndStatesForGroup(group));
        assertEquals(groupStatus.size(), 1);
        assertEquals(groupStatus.get(0), new UserPermissionsAndState(user1, Permissions.EDITOR,
                SharedFolderState.PENDING));

        group.addMember(user2);
        sf.setState(user1, SharedFolderState.JOINED);
        groupStatus = Lists.newArrayList(sf.getUserRolesAndStatesForGroup(group));
        assertEquals(groupStatus.size(), 2);
        assertTrue(groupStatus.contains(new UserPermissionsAndState(user1, Permissions.EDITOR, SharedFolderState.JOINED)));
        assertTrue(groupStatus.contains(new UserPermissionsAndState(user2, Permissions.EDITOR, SharedFolderState.PENDING)));
    }
}
