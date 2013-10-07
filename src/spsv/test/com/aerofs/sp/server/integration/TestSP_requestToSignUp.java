/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestSP_requestToSignUp extends AbstractSPTest
{
    @Test
    public void shouldRequestToSignUpEmailForNonexistingUser() throws Exception
    {
        String email = "user@gmail.com";
        service.requestToSignUp(email);
        verify(requestToSignUpEmailer).sendRequestToSignUpEmail(eq(email), any(String.class));
        verifyNoMoreInteractions(requestToSignUpEmailer);
    }

    @Test
    public void shouldSendAlreadySignedUpEmailForExistingUser() throws Exception
    {
        String email = "user@gmail.com";
        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        saveUser(user);
        sqlTrans.commit();

        service.requestToSignUp(email);
        verify(requestToSignUpEmailer).sendAlreadySignedUpEmail(email);
        verifyNoMoreInteractions(requestToSignUpEmailer);
    }
}
