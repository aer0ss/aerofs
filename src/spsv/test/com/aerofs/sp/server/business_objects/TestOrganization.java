/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class TestOrganization extends AbstractBusinessObjectTest
{
    @Test
    public void saveOrganization_shouldNeverCreateDefaultOrganization()
            throws ExNoPerm, IOException, ExNotFound, SQLException
    {
        assertFalse(save().isDefault());
    }

    @Test
    public void saveOrganization_shouldAddTeamServerUser()
            throws ExNoPerm, IOException, ExNotFound, SQLException
    {
        User tsUser = factUser.create(save().id().toTeamServerUserID());

        assertTrue(tsUser.exists());
    }

    @Test(expected = AssertionError.class)
    public void setName_shouldAssertForNonExistingOrg()
            throws SQLException
    {
        Organization org = factOrg.create(new OrganizationID(123));
        org.setName("hoho");
    }

    @Test(expected = ExNotFound.class)
    public void getName_shouldThrowForNonExistingOrg()
            throws SQLException, ExNotFound
    {
        factOrg.create(new OrganizationID(123)).getName();
    }

    @Test
    public void getName_shouldReturnSetName()
            throws ExNoPerm, IOException, ExNotFound, SQLException
    {
        Organization org = save();

        final String NAME = "12345676543456765432";
        org.setName(NAME);
        assertEquals(org.getName(), NAME);
    }

    private Organization save()
            throws ExNoPerm, IOException, ExNotFound, SQLException
    {
        return factOrg.save("test", null, StripeCustomerID.TEST);
    }
}
