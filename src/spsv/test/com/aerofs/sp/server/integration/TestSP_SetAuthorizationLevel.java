/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_SetAuthorizationLevel extends AbstractSPTest
{
    @Before
    public void setup()
    {
        setSession(USER_1);
    }

    @Test
    public void shouldThrowIfSubjectNotFound()
            throws Exception
    {
        try {
            service.setAuthorizationLevel("non-existing@user", PBAuthorizationLevel.USER);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldThrowIfRequesterIsInDifferentOrgThanSubject()
            throws Exception
    {
        try {
            service.setAuthorizationLevel(USER_2.id().getString(), PBAuthorizationLevel.USER);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfRequesterIsNotAdmin()
            throws Exception
    {
        service.setAuthorizationLevel(USER_2.id().getString(), PBAuthorizationLevel.ADMIN);
    }

    @Test
    public void shouldThrowIfRequesterEqualsSubject()
            throws Exception
    {
        try {
            service.setAuthorizationLevel(USER_1.id().getString(), PBAuthorizationLevel.ADMIN);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldSetAuthLevel()
            throws Exception
    {
        sqlTrans.begin();
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        assertEquals(USER_2.getLevel(), AuthorizationLevel.USER);
        sqlTrans.commit();

        service.setAuthorizationLevel(USER_2.id().getString(), PBAuthorizationLevel.ADMIN);

        sqlTrans.begin();
        assertEquals(USER_2.getLevel(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();
    }
}
