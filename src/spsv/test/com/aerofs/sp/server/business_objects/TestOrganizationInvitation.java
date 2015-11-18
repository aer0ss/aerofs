/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestOrganizationInvitation extends AbstractBusinessObjectTest
{
    @Test
    public void update_shouldUpdateTheInviter()
            throws Exception
    {
        User inviter1 = saveUser();
        User inviter2 = saveUser();
        User invitee = saveUser();
        Organization org = saveOrganization();
        OrganizationInvitation oi = factOrgInvite.save(inviter1, invitee, org, null);
        assertTrue(oi.exists());
        assertEquals(oi.getInviter(), inviter1);

        factOrgInvite.update(inviter2, invitee, org);

        OrganizationInvitation updated = factOrgInvite.create(invitee, org);

        assertTrue(updated.exists());
        assertEquals(updated.getInviter(), inviter2);

    }

    @Test(expected = ExNotFound.class)
    public void delete_shouldThrowIfNotFound()
            throws ExNotFound, SQLException
    {
        factOrgInvite.create(newUser(), newOrganization()).delete();
    }

    @Test
    public void delete_shouldDelete()
            throws Exception
    {
        User user = saveUser();
        Organization org = saveOrganization();
        OrganizationInvitation oi = factOrgInvite.save(saveUser(), user, org, null);
        assertTrue(oi.exists());
        oi.delete();
        assertFalse(oi.exists());
    }
}
