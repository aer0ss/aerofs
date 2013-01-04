/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import org.junit.Test;

import java.util.Collections;

/**
 * A class to test the get device info SP call.
 */
public class TestSP_InviteUser extends AbstractSPTest
{
    @Test(expected = ExBadArgs.class)
    public void inviteUser_shouldThrowOnEmptyInviteeList()
            throws Exception
    {
        setSessionUser(USER_1);

        service.inviteToSignUp(Collections.<String>emptyList());
    }

    @Test(expected = ExAlreadyExist.class)
    public void inviteUser_shouldThrowIfUserAlreadyExists()
            throws Exception
    {
        setSessionUser(USER_1);

        service.inviteToSignUp(Collections.singletonList(USER_2.toString()));
    }
}
