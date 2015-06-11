/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestGroupMembers extends AbstractBusinessObjectTest
{
    Group group;

    User user1, user2, user3, pending1;

    @Before
    public void setup()
            throws Exception
    {
        // Create test users.
        user1 = saveUser();
        group = factGroup.save("Common Name", user1.getOrganization().id(), null);
        user2 = saveUser();
        user3 = saveUser();

        pending1 = newUser();
    }

    @Test
    public void shouldListNoMembersUponCreationOfGroup()
            throws Exception
    {
        assertEquals(0, group.listMembers().size());
    }

    @Test
    public void shouldAppropriatelyReportMembership()
            throws Exception
    {
        group.addMember(user1);
        assertTrue(group.hasMember(user1));
    }

    @Test
    public void shouldAddMembersSuccessfully()
            throws Exception
    {
        group.addMember(user1);
        group.addMember(user2);

        assertEquals(2, group.listMembers().size());
    }

    @Test
    public void shouldThrowWhenAddingSameMemberTwice()
            throws Exception
    {
        group.addMember(user1);
        try {
            group.addMember(user1);
            fail();
        } catch (ExAlreadyExist e) {}
    }

   @Test
    public void shouldRemoveMembersSuccessfully()
            throws Exception
    {
        group.addMember(user1);
        group.addMember(user2);
        group.addMember(user3);
        assertEquals(3, group.listMembers().size());

        group.removeMember(user1, null);
        group.removeMember(user2, null);
        group.removeMember(user3, null);
        assertEquals(0, group.listMembers().size());
    }

    @Test
    public void shouldThrowWhenRemovingSameMemberTwice()
            throws Exception
    {
        group.addMember(user1);
        group.removeMember(user1, null);
        try {
            group.removeMember(user1, null);
            fail();
        } catch (ExNotFound e) {}
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowOnAddIfGroupNotFound()
            throws Exception
    {
        Group noExist = factGroup.create(GroupID.fromExternal(666));
        noExist.addMember(user1);
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowOnRemoveIfGroupNotFound()
            throws Exception
    {
        Group noExist = factGroup.create(GroupID.fromExternal(666));
        noExist.removeMember(user1, null);
    }

    @Test
    public void shouldAcceptPendingMembers()
            throws Exception
    {
        group.addMember(pending1);
        assertEquals(group.listMembers().size(), 1);
        assertEquals(group.listMembers().get(0).id(), pending1.id());
        group.removeMember(pending1, null);
    }

    @Test
    public void pendingMembersShouldGetOrg()
            throws Exception
    {
        group.addMember(pending1);
        saveUser(pending1);
        assertEquals(pending1.getOrganization(), group.getOrganization());
    }
}
