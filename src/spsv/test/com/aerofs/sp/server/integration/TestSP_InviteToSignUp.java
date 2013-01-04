/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.ex.ExBadArgs;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

/**
 * A class to test the get device info SP call.
 */
public class TestSP_InviteToSignUp extends AbstractSPTest
{
    @Test(expected = ExBadArgs.class)
    public void inviteUser_shouldThrowOnEmptyInviteeList()
            throws Exception
    {
        setSessionUser(USER_1);

        service.inviteToSignUp(Collections.<String>emptyList());
    }

    @Test
    public void inviteUser_shouldIgnoreIfUserAlreadyExists()
            throws Exception
    {
        setSessionUser(USER_1);

        service.inviteToSignUp(Collections.singletonList(USER_2.toString()));

        Mockito.verifyZeroInteractions(factEmailer);
    }
}
