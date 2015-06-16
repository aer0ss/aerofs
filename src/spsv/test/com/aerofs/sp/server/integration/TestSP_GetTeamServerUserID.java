/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

public class TestSP_GetTeamServerUserID extends AbstractSPTest
{
    User user, user2;

    @Before
    public void setup()
            throws Exception
    {
        sqlTrans.begin();
        try {
            user = saveUser();
            user2 = saveUser();
            user2.setOrganization(user.getOrganization(), AuthorizationLevel.USER);
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.rollback();
            throw e;
        }
    }

    @Test
    public void shouldThrowIfUserIsNonAdmin()
            throws Exception
    {
        setSession(user2);

        try {
            service.getTeamServerUserID();
            fail();
        } catch (ExNoPerm ignored) {
            // expected
        }
    }
}
