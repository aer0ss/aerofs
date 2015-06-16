/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_SetAuthorizationLevel extends AbstractSPTest
{
    User user1, user2;

    @Before
    public void setup()
            throws Exception
    {
        sqlTrans.begin();
        try {
            user1 = saveUserWithNewOrganization();
            user2 = saveUserWithNewOrganization();
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.handleException();
            throw e;
        }

        setSession(user1);
    }

    @Test
    public void shouldThrowIfSubjectNotFound()
            throws Exception
    {
        try {
            service.setAuthorizationLevel("non-existing@user", PBAuthorizationLevel.USER);
            fail();
        } catch (ExNoPerm ignored) {}
    }

    @Test
    public void shouldThrowIfRequesterIsInDifferentOrgThanSubject()
            throws Exception
    {
        try {
            service.setAuthorizationLevel(user2.id().getString(), PBAuthorizationLevel.USER);
            fail();
        } catch (ExNoPerm ignored) {}
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfRequesterIsNotAdmin()
            throws Exception
    {
        service.setAuthorizationLevel(user2.id().getString(), PBAuthorizationLevel.ADMIN);
    }

    @Test
    public void shouldThrowIfRequesterEqualsSubject()
            throws Exception
    {
        try {
            service.setAuthorizationLevel(user1.id().getString(), PBAuthorizationLevel.ADMIN);
            fail();
        } catch (ExNoPerm ignored) {}
    }

    @Test
    public void shouldSetAuthLevel()
            throws Exception
    {
        sqlTrans.begin();
        user2.setOrganization(user1.getOrganization(), AuthorizationLevel.USER);
        assertEquals(user2.getLevel(), AuthorizationLevel.USER);
        sqlTrans.commit();

        service.setAuthorizationLevel(user2.id().getString(), PBAuthorizationLevel.ADMIN);

        sqlTrans.begin();
        assertEquals(user2.getLevel(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();
    }
}
