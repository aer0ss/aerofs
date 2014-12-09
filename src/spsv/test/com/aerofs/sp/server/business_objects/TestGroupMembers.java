/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestGroupMembers extends AbstractBusinessObjectTest
{
    final OrganizationID orgId = new OrganizationID(1);
    Organization org;
    Group group;

    User user1, user2, user3;

    @Before
    public void setup()
            throws Exception
    {
        org = factOrg.save(orgId);
        group = factGroup.save("Common Name", orgId, null);

        // Create test users.
        user1 = saveUser();
        user2 = saveUser();
        user3 = saveUser();
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

        group.removeMember(user1);
        group.removeMember(user2);
        group.removeMember(user3);
        assertEquals(0, group.listMembers().size());
    }

    @Test
    public void shouldThrowWhenRemovingSameMemberTwice()
            throws Exception
    {
        group.addMember(user1);
        group.removeMember(user1);
        try {
            group.removeMember(user1);
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
        noExist.removeMember(user1);
    }
}
