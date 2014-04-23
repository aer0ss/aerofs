/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TestSP_SetQuota extends AbstractSPTest
{
    private User admin, nonadmin;

    @Before
    public void setUp() throws Exception
    {
        // set up users
        sqlTrans.begin();
        admin = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);
        nonadmin = saveUser();
        nonadmin.setOrganization(admin.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        setSessionUser(admin);
    }

    @Test
    public void shouldEnforceAdminOnlyForSetQuota() throws Exception
    {
        setSessionUser(nonadmin);
        try {
            service.setQuota(42L);
            fail();
        } catch (ExNoPerm ignored) {
            // succeed
        }
    }

    @Test
    public void shouldEnforceAdminOnlyForRemoveQuota() throws Exception
    {
        setSessionUser(nonadmin);
        try {
            service.removeQuota();
            fail();
        } catch (ExNoPerm ignored) {
            // succeed
        }
    }

    @Test
    public void shouldSetQuota() throws Exception
    {
        Long quota = 42L;
        service.setQuota(quota);

        sqlTrans.begin();
        assertEquals(quota, admin.getOrganization().getQuotaPerUser());
        sqlTrans.commit();

        Long newQuota = 9001L;
        service.setQuota(newQuota);

        sqlTrans.begin();
        assertEquals(newQuota, admin.getOrganization().getQuotaPerUser());
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveQuota() throws Exception
    {
        service.setQuota(42L);

        sqlTrans.begin();
        assertNotNull(admin.getOrganization().getQuotaPerUser());
        sqlTrans.commit();

        service.removeQuota();

        sqlTrans.begin();
        assertNull(admin.getOrganization().getQuotaPerUser());
        sqlTrans.commit();
    }
}
