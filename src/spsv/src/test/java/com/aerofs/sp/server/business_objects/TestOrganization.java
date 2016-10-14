/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestOrganization extends AbstractBusinessObjectTest
{
    @Test
    public void saveOrganization_shouldAddTeamServerUser()
            throws Exception
    {
        User tsUser = saveOrganization().getTeamServerUser();

        assertTrue(tsUser.exists());
    }

    @Test
    public void saveOrganization_shouldSaveWithGivenId() throws SQLException, ExAlreadyExist
    {
        OrganizationID orgID = new OrganizationID(8707);
        Organization org = factOrg.save(orgID);
        assertEquals(orgID, org.id());
    }

    @Test
    public void saveOrganization_shouldThrowIfOrgAlreadyExists()
            throws Exception
    {
        // Create an organization with a random id
        Organization org = saveOrganization();

        // Try to create another one with the same id
        try {
            factOrg.save(org.id());
            fail();
        } catch (ExAlreadyExist e) { /* expected */ }
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
        Organization org = saveOrganization();

        final String NAME = "12345676543456765432";
        org.setName(NAME);
        assertEquals(org.getName(), NAME);
    }

    @Test
    public void shouldReportExistenceCorrectly() throws SQLException, ExAlreadyExist
    {
        Organization org = factOrg.create(3624);
        assertFalse(org.exists());
        factOrg.save(org.id());
        assertTrue(org.exists());
    }
}
