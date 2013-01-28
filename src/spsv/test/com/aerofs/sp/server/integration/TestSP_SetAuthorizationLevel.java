/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

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
        service.addOrganization("test", null, StripeCustomerID.TEST.getID());
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

        sqlTrans.begin();
        assertEquals(udb.getLevel(USER_2), AuthorizationLevel.USER);
        sqlTrans.commit();

        service.setAuthorizationLevel(USER_2.toString(), PBAuthorizationLevel.ADMIN);

        sqlTrans.begin();
        assertEquals(udb.getLevel(USER_2), AuthorizationLevel.ADMIN);
        sqlTrans.commit();
    }

    private void setUserOneAsAdmin()
            throws SQLException
    {
        sqlTrans.begin();
        udb.setLevel(USER_1, AuthorizationLevel.ADMIN);
        sqlTrans.commit();
    }

}
