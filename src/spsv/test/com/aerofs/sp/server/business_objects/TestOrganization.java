/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class TestOrganization extends AbstractBusinessObjectTest
{
    @Test
    public void saveOrganization_shouldAddTeamServerUser()
            throws Exception
    {
        User tsUser = save().getTeamServerUser();

        assertTrue(tsUser.exists());
    }

    @Test(expected = AssertionError.class)
    public void setName_shouldAssertForNonExistingOrg()
            throws SQLException
    {
        Organization org = factOrg.create(123);
        org.setName("hoho");
    }

    @Test(expected = ExNotFound.class)
    public void getName_shouldThrowForNonExistingOrg()
            throws SQLException, ExNotFound
    {
        factOrg.create(123).getName();
    }

    @Test
    public void getName_shouldReturnSetName()
            throws Exception
    {
        Organization org = save();

        final String NAME = "12345676543456765432";
        org.setName(NAME);
        assertEquals(org.getName(), NAME);
    }

    private Organization save()
            throws Exception
    {
        return factOrg.save();
    }
}
