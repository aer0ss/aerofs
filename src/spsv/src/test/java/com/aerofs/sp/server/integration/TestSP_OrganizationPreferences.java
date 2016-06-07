/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestSP_OrganizationPreferences extends AbstractSPTest
{
    @Before
    public void setup()
    {
        setSession(USER_1);
    }

    @Test
    public void shouldReturnSetOrganizationNameAndPhone()
            throws Exception
    {
        String name = "hohoho";
        String phone = "xixixix";

        service.setOrgPreferences(name, phone);

        assertEquals(service.getOrgPreferences().get().getOrganizationName(), name);
        assertEquals(service.getOrgPreferences().get().getOrganizationContactPhone(), phone);
    }

    @Test
    public void shouldSupportOrgNameUtf8mb4()
            throws Exception
    {
        String name = "\uD83D\uDCA9";
        String phone = "xixixix";

        service.setOrgPreferences(name, phone);
        assertEquals(service.getOrgPreferences().get().getOrganizationName(), name);
    }
}
