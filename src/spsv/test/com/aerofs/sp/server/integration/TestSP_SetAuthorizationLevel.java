/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_SetAuthorizationLevel extends AbstractSPTest
{
    @Before
    public void setup()
    {
        setSessionUser(USER_1);
    }

    @Test
    public void shouldThrowIfSubjectNotFound()
            throws Exception
    {
        // switch user_1 to a different organization
        service.addOrganization("test", null, StripeCustomerID.TEST.getString());
        assertEquals(service.getAuthorizationLevel().get().getLevel(), PBAuthorizationLevel.ADMIN);

        try {
            service.setAuthorizationLevel("non-existing@user", PBAuthorizationLevel.USER);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldThrowIfRequesterIsInDifferentOrgThanSubject()
            throws Exception
    {
        // switch user_1 to a different organization
        service.addOrganization("test", null, StripeCustomerID.TEST.getString());
        assertEquals(service.getAuthorizationLevel().get().getLevel(), PBAuthorizationLevel.ADMIN);

        try {
            service.setAuthorizationLevel(USER_2.getString(), PBAuthorizationLevel.USER);
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfRequesterIsNotAdmin()
            throws Exception
    {
        service.setAuthorizationLevel(USER_2.getString(), PBAuthorizationLevel.ADMIN);
    }

    @Test
    public void shouldThrowIfRequesterEqualsSubject()
            throws Exception
    {
        setUserOneAsAdmin();

        try {
            service.setAuthorizationLevel(USER_1.getString(), PBAuthorizationLevel.ADMIN);
            fail();
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

        service.setAuthorizationLevel(USER_2.getString(), PBAuthorizationLevel.ADMIN);

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
