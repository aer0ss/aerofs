/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.ex.ExWrongOrganization;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Sp.CreateGroupReply;
import com.aerofs.proto.Sp.ListGroupMembersReply;
import com.aerofs.proto.Sp.ListGroupStatusInSharedFolderReply;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.Mockito.verify;

public class TestSP_GroupSharing extends AbstractSPFolderTest
{
    Group group;
    Organization org;
    // Shared folder.
    final SID sid = SID.generate();
    SharedFolder sf;
    User owner, user2, user3, user4;
    User outsideOrg;

    @Before
    public void setup()
            throws Exception
    {
        sqlTrans.begin();

        // Create test users.
        org = saveOrganization();
        owner = makeUserInOrg(org, AuthorizationLevel.ADMIN);
        user2 = makeUserInOrg(org, AuthorizationLevel.USER);
        user3 = makeUserInOrg(org, AuthorizationLevel.USER);
        user4 = makeUserInOrg(org, AuthorizationLevel.USER);
        outsideOrg = saveUser();

        // Make group
        makeAdmin(owner, org);
        setSession(owner);
        group = factGroup.save("common name", org.id(), null);

        // Test shared folder.
        sf = factSharedFolder.create(sid);
        sf.save("Shared Folder Name", owner);

        sqlTrans.commit();
    }

    @Test
    public void shouldCreateGroup()
        throws Exception
    {
        CreateGroupReply reply = service.createGroup("a group").get();
        GroupID gid = GroupID.fromExternal(reply.getGroupId());
        Group newGroup = factGroup.create(gid);
        sqlTrans.begin();
        assertTrue(newGroup.exists());
        sqlTrans.commit();
    }

    @Test
    public void shouldDeleteGroup()
            throws Exception
    {
        service.deleteGroup(group.id().getInt());
        assertEquals(service.listGroups(1, 0, "common").get().getGroupsCount(), 0);
    }

    @Test
    public void shouldAddMembers()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2));
        assertEquals(service.listGroupMembers(group.id().getInt()).get().getUsersCount(), 1);
        assertEquals(service.listGroupMembers(group.id().getInt())
                .get()
                .getUsers(0)
                .getUserEmail(), email(user2));
        service.addGroupMembers(group.id().getInt(), emails(user3, user4));
        assertEquals(service.listGroupMembers(group.id().getInt()).get().getUsersCount(), 3);
    }

    @Test(expected = ExWrongOrganization.class)
    public void shouldntAddMembersNotInOrg()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(outsideOrg));
    }

    @Test
    public void shouldRemoveMembers()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2, user3, user4));
        service.removeGroupMembers(group.id().getInt(), emails(user2));
        assertEquals(service.listGroupMembers(group.id().getInt()).get().getUsersCount(), 2);
        service.removeGroupMembers(group.id().getInt(), emails(user3, user4));
        assertEquals(service.listGroupMembers(group.id().getInt()).get().getUsersCount(), 0);
    }

    @Test
    public void shouldListMembers()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2, user3));
        ListGroupMembersReply reply = service.listGroupMembers(group.id().getInt()).get();
        List<PBUser> users = reply.getUsersList();
        assertEquals(users.size(), 2);
        List<String> userEmails = newArrayList();
        for (PBUser u : users) {
            userEmails.add(u.getUserEmail());
        }
        assertTrue(userEmails.contains(email(user2)));
        assertTrue(userEmails.contains(email(user3)));
    }

    @Test
    public void shouldJoinFolder()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2, user3));
        shareFolder(owner, sid, group, Permissions.VIEWER);

        ListGroupStatusInSharedFolderReply reply = service.listGroupStatusInSharedFolder(
            group.id().getInt(), sid.toPB()).get();
        // user 2 and TS
        assertEquals(reply.getUserPermissionsAndStateCount(), 2);
    }

    @Test
    public void shouldUpdateACL()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2));
        shareFolder(owner, sid, group, Permissions.VIEWER);
        joinSharedFolder(user2, sid);
        service.updateACL(sid.toPB(), SubjectPermissions.getStringFromSubject(group.id()),
                Permissions.EDITOR.toPB(), false);

        sqlTrans.begin();
        assertEquals(group.getRoleForSharedFolder(sf), Permissions.EDITOR);
        assertEquals(sf.getPermissions(user2), Permissions.EDITOR);
        sqlTrans.commit();
        verify(sharedFolderNotificationEmailer).sendRoleChangedNotificationEmail(sf, owner, group, Permissions.VIEWER, Permissions.EDITOR);
    }

    @Test
    public void shouldDeleteACL()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2));
        shareFolder(owner, sid, group, Permissions.VIEWER);
        joinSharedFolder(user2, sid);
        service.deleteACL(sid.toPB(), SubjectPermissions.getStringFromSubject(group.id()));
        sqlTrans.begin();
        assertEquals(sf.getJoinedGroups().size(), 0);
        assertEquals(group.listSharedFolders().size(), 0);
        assertEquals(sf.getPermissionsNullable(user2), null);
        sqlTrans.commit();
        verify(sharedFolderNotificationEmailer).sendRemovedFromFolderNotificationEmail(sf, owner, group);
    }

    private String email(User u) {
        return u.id().getString();
    }

    private List<String> emails(User... users) {
        List<String> emails = newArrayList();
        for (User u : users) {
            emails.add(email(u));
        }
        return emails;
    }

    private void makeAdmin(User admin, Organization org)
        throws Exception
    {
        admin.setOrganization(org, AuthorizationLevel.ADMIN);
    }

    private User makeUserInOrg(Organization o, AuthorizationLevel auth)
        throws Exception
    {
        User u = saveUser();
        u.setOrganization(org, auth);
        return u;
    }
}