/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class TestSP_OrgPreferences extends AbstractSPTest
{
    @Before
    public void setup()
    {
        setSessionUser(TEST_USER_1);
    }

    @Test
    public void shouldReturnSetOrganizationName()
            throws Exception
    {
        String oldName = "hahaha";
        String newName = "hohoho";

        service.addOrganization(oldName);

        assertEquals(service.getOrgPreferences().get().getOrgName(), oldName);

        service.setOrgPreferences(newName);

        assertEquals(service.getOrgPreferences().get().getOrgName(), newName);
    }
}
