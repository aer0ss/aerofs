/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.ids.UniqueID;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestSP_UserSettingsToken extends AbstractSPTest
{
    private String _token;

    @Before
    public void setUp()
        throws Exception
    {
        // Test user.
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();
        setSession(user);

        // Test token.
        _token = UniqueID.generate().toStringFormal();
    }

    @Test
    public void shouldThrowWhenTokenDoesNotExist()
            throws Exception
    {
        assertFalse(service.getUserSettingsToken().get().hasToken());
    }

    @Test
    public void shouldStoreUserSettingsToken()
            throws Exception
    {
        service.setUserSettingsToken(_token);
        assertEquals(service.getUserSettingsToken().get().getToken(), _token);
    }

    @Test
    public void shouldDeleteUserSettingsToken()
            throws Exception
    {
        service.setUserSettingsToken(_token);
        assertEquals(service.getUserSettingsToken().get().getToken(), _token);
        service.deleteUserSettingsToken();
        assertFalse(service.getUserSettingsToken().get().hasToken());
    }
}
