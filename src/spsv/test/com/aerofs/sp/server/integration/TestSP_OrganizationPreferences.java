/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.TestStripeCustomerID;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class TestSP_OrganizationPreferences extends AbstractSPTest
{
    @Before
    public void setup()
    {
        setSessionUser(USER_1);
    }

    @Test
    public void shouldReturnSetOrganizationName()
            throws Exception
    {
        String oldName = "hahaha";
        String newName = "hohoho";

        service.addOrganization(oldName, null, null, TestStripeCustomerID.TEST.getID());

        assertEquals(service.getOrgPreferences().get().getOrganizationName(), oldName);

        service.setOrgPreferences(newName);

        assertEquals(service.getOrgPreferences().get().getOrganizationName(), newName);
    }
}
