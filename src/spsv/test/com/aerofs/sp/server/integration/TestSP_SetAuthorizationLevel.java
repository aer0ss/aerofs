/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotSame;

public class TestSP_SetAuthorizationLevel extends AbstractSPTest
{
    @Before
    public void setup()
    {
        setSessionUser(USER_1);
    }

    @Test
    public void shouldThrowIfRequesterIsInDifferentOrgThanSubject()
            throws Exception
    {
        // switch user_1 to a different organization
        service.addOrganization("test");
        assertEquals(service.getAuthorizationLevel().get().getLevel(), PBAuthorizationLevel.ADMIN);

        try {
            service.setAuthorizationLevel(USER_2.toString(), PBAuthorizationLevel.USER);
            assertFalse(true);
        } catch (ExNoPerm e) {}
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfRequesterIsNotAdmin()
            throws Exception
    {
        service.setAuthorizationLevel(USER_2.toString(), PBAuthorizationLevel.ADMIN);
    }

    @Test
    public void shouldThrowIfRequesterEqualsSubject()
            throws Exception
    {
        setUserOneAsAdmin();

        try {
            service.setAuthorizationLevel(USER_1.toString(), PBAuthorizationLevel.ADMIN);
            assertFalse(true);
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldSetAuthLevel()
            throws Exception
    {
        setUserOneAsAdmin();

        trans.begin();
        assertEquals(udb.getLevel(USER_2), AuthorizationLevel.USER);
        trans.commit();

        service.setAuthorizationLevel(USER_2.toString(), PBAuthorizationLevel.ADMIN);

        trans.begin();
        assertEquals(udb.getLevel(USER_2), AuthorizationLevel.ADMIN);
        trans.commit();
    }

    private void setUserOneAsAdmin()
            throws SQLException
    {
        trans.begin();
        udb.setLevel(USER_1, AuthorizationLevel.ADMIN);
        trans.commit();
    }

}
