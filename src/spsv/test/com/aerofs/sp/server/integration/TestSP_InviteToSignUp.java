/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExEmailNotVerified;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static junit.framework.Assert.fail;

/**
 * A class to test the get device info SP call.
 */
public class TestSP_InviteToSignUp extends AbstractSPTest
{
    @Test(expected = ExBadArgs.class)
    public void shouldThrowOnEmptyInviteeList()
            throws Exception
    {
        setSessionUser(USER_1);

        service.inviteToSignUp(Collections.<String>emptyList());
    }

    @Test
    public void shouldIgnoreIfUserAlreadyExists()
            throws Exception
    {
        setSessionUser(USER_1);

        service.inviteToSignUp(Collections.singletonList(USER_2.id().getString()));

        Mockito.verifyZeroInteractions(factEmailer);
    }

    @Test
    public void shouldThrowIfIniviterEmailNotVerified()
            throws Exception
    {
        sqlTrans.begin();
        User user = saveEmailUnverifiedUser();
        sqlTrans.commit();

        setSessionUser(user);

        try {
            service.inviteToSignUp(Collections.singletonList(USER_1.id().getString()));
            fail();
        } catch (ExEmailNotVerified e) { }
    }
}
