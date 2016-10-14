/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExMemberLimitExceeded;
import com.aerofs.base.ex.ExWrongOrganization;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.SID;
import com.aerofs.proto.Sp.CreateGroupReply;
import com.aerofs.proto.Sp.ListGroupMembersReply;
import com.aerofs.proto.Sp.ListGroupStatusInSharedFolderReply;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
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
        verify(factEmailer).createAddedToGroupEmailer(owner, user2, group);
        assertEquals(service.listGroupMembers(group.id().getInt()).get().getUsersCount(), 1);
        assertEquals(service.listGroupMembers(group.id().getInt())
                .get()
                .getUsers(0)
                .getUserEmail(), email(user2));
        service.addGroupMembers(group.id().getInt(), emails(user3, user4));
        verify(factEmailer).createAddedToGroupEmailer(owner, user3, group);
        verify(factEmailer).createAddedToGroupEmailer(owner, user4, group);
        assertEquals(service.listGroupMembers(group.id().getInt()).get().getUsersCount(), 3);
    }

    @Test
    public void shouldLimitMembers()
        throws Exception
    {
        int prevValue = SPParam.MAX_GROUP_SIZE;
        try {
            SPParam.MAX_GROUP_SIZE = 2;
            service.addGroupMembers(group.id().getInt(), emails(user2));
            try {
                service.addGroupMembers(group.id().getInt(), emails(user3, user4));
                fail();
            } catch (ExMemberLimitExceeded e) {
                sqlTrans.rollback();
            }
            service.addGroupMembers(group.id().getInt(), emails(user3));
            try {
                service.addGroupMembers(group.id().getInt(), emails(user4));
                fail();
            } catch (ExMemberLimitExceeded e) {
                sqlTrans.rollback();
            }
        } finally {
            SPParam.MAX_GROUP_SIZE = prevValue;
        }
    }

    @Test(expected = ExWrongOrganization.class)
    public void shouldntAddMembersNotInOrg()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(outsideOrg));
    }

    @Test
    public void shouldntAddMembersSetToJoinOtherOrg()
            throws Exception
    {
        sqlTrans.begin();
        Organization org2 = saveOrganization();
        User org2admin = makeUserInOrg(org2, AuthorizationLevel.ADMIN);
        User pending = newUser();
        Group group2 = factGroup.save("another common name", org2.id(), null);
        sqlTrans.commit();

        setSession(org2admin);
        service.addGroupMembers(group2.id().getInt(), emails(pending));

        try {
            setSession(owner);
            service.addGroupMembers(group.id().getInt(), emails(outsideOrg));
            fail();
        } catch (ExWrongOrganization e) {
        }
    }

    @Captor ArgumentCaptor<String> signUpCodeCaptor;

    @Test
    public void shouldAddPendingGroupMembersToSameOrg()
            throws Exception
    {
        sqlTrans.begin();
        User pending = newUser();
        sqlTrans.commit();

        service.addGroupMembers(group.id().getInt(), emails(pending));
        verify(factEmailer).createGroupSignUpInvitationEmailer(eq(owner), eq(pending), eq(group),
                signUpCodeCaptor.capture());
        service.signUpWithCode(signUpCodeCaptor.getValue(), ByteString.copyFromUtf8("password"),
                "Firsty", "Lasto");

        sqlTrans.begin();
        assertEquals(pending.getOrganization(), org);
        sqlTrans.commit();
    }

    @Test
    public void shouldRemovePendingMembers()
            throws Exception
    {
        sqlTrans.begin();
        User pending = newUser();
        sqlTrans.commit();

        service.addGroupMembers(group.id().getInt(), emails(pending));
        service.removeGroupMembers(group.id().getInt(), emails(pending));

        sqlTrans.begin();
        assertTrue(group.listMembers().isEmpty());
        sqlTrans.commit();
    }

    @Test
    public void shouldListPendingMembers()
            throws Exception
    {
        sqlTrans.begin();
        User pending = newUser();
        sqlTrans.commit();

        service.addGroupMembers(group.id().getInt(), emails(pending));
        List<PBUser> users = service.listGroupMembers(group.id().getInt()).get().getUsersList();
        assertEquals(users.size(), 1);
        assertEquals(users.get(0).getUserEmail(), pending.id().getString());
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
            group.id().getInt(), BaseUtil.toPB(sid)).get();
        // user 2 and TS
        assertEquals(reply.getUserAndStateCount(), 2);
    }

    @Test
    public void shouldUpdateACL()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2));
        shareFolder(owner, sid, group, Permissions.VIEWER);
        joinSharedFolder(user2, sid);
        service.updateACL(BaseUtil.toPB(sid), SubjectPermissions.getStringFromSubject(group.id()),
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
        service.deleteACL(BaseUtil.toPB(sid), SubjectPermissions.getStringFromSubject(group.id()));
        sqlTrans.begin();
        assertEquals(sf.getJoinedGroups().size(), 0);
        assertEquals(group.listSharedFolders().size(), 0);
        assertEquals(sf.getPermissionsNullable(user2), null);
        sqlTrans.commit();
        verify(sharedFolderNotificationEmailer).sendRemovedFromFolderNotificationEmail(sf, owner, group);
    }

    @Test
    public void shouldDeleteFolderWithGroupMembers()
        throws Exception
    {
        service.addGroupMembers(group.id().getInt(), emails(user2));
        shareFolder(owner, sid, group, Permissions.VIEWER);
        joinSharedFolder(user2, sid);
        service.destroySharedFolder(BaseUtil.toPB(sid));

        sqlTrans.begin();
        assertFalse(sf.exists());
        sqlTrans.commit();
    }

    @Test
    public void canHandleSignedAndUnsignedGroupIDs()
            throws Exception
    {
        int negative = -10;
        long largeNegative = Long.MIN_VALUE;
        int nullGroup = GroupID.NULL_GROUP.getInt();
        int positive = 10;
        long largePositive = (long) Integer.MAX_VALUE + 100L;
        long tooLargePositive = Long.MAX_VALUE;

        GroupID.fromExternal(Integer.toString(negative));
        GroupID.fromExternal(Integer.toString(positive));
        GroupID.fromExternal(Long.toString(largePositive));
        try {
            GroupID.fromExternal(Long.toString(largeNegative));
            fail();
        } catch (ExBadArgs e) {}
        try {
            GroupID.fromExternal(Integer.toString(nullGroup));
            fail();
        } catch (ExBadArgs e) {}
        try {
            GroupID.fromExternal(Long.toString(tooLargePositive));
            fail();
        } catch (ExBadArgs e) {}
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
        u.setOrganization(o, auth);
        return u;
    }
}
