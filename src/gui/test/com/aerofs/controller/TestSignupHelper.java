/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.LocalSPServiceReactorCaller;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSignupHelper extends AbstractTest
{
    private final InvitationEmailer.Factory factEmail = mock(InvitationEmailer.Factory.class);

    private final LocalSPServiceReactorCaller serviceReactorCaller =
            new LocalSPServiceReactorCaller(factEmail);

    @Spy SPServiceBlockingStub _sp = new SPServiceBlockingStub(serviceReactorCaller);
    @InjectMocks SignupHelper _signupHelper;

    private static final UserID USER_ID = UserID.fromInternal("user1@company.com");
    private static final byte[] SCRYPT_PASSWORD = SecUtil.scrypt("temp123".toCharArray(), USER_ID);
    private static final String INVALID_TARGETED_CODE
            = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
    private FullName fullName = new FullName("first", "last");

    @Before
    public void setup()
        throws Exception
    {
        // return stub invitation emails to avoid NPE
        when(factEmail.createUserInvitation(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(new InvitationEmailer());
        when(factEmail.createFolderInvitation(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(new InvitationEmailer());

        serviceReactorCaller.init_();
    }

    @Test(expected = ExNotFound.class)
    public void shouldFailSignUpWhenTargetedCodeDoesNotExist()
            throws Exception
    {
        // The INVALID_TARGETED_CODE has not been added to sp, so this test should fail with
        // ExNotFound.
        _signupHelper.signUp(USER_ID, SCRYPT_PASSWORD, INVALID_TARGETED_CODE, fullName);
    }

    @Test
    public void shouldSuccessfullySignInAfterSignUpNewUserWithValidCode()
            throws Exception
    {
        // Invite a user and get the signup code
        String signUpCode = inviteUserToDefaultOrg(USER_ID);

        // Setting up a new user with valid signUpCode should not throw any exceptions
        _signupHelper.signUp(USER_ID, SCRYPT_PASSWORD, signUpCode, fullName);

        // The user should be able to successfully sign in
        _sp.signIn(USER_ID.toString(), ByteString.copyFrom(SCRYPT_PASSWORD));

        // TODO verify anything else?
    }

    @Test
    public void shouldPermitSignUpOfExistingUserWithNewValidCodeAndSamePassword()
            throws Exception
    {
        // Invite a user twice
        String signUpCode1 = inviteUserToDefaultOrg(USER_ID);
        String signUpCode2 = inviteUserToDefaultOrg(USER_ID);

        // Sign up a new user using both codes
        _signupHelper.signUp(USER_ID, SCRYPT_PASSWORD, signUpCode1, fullName);
        _signupHelper.signUp(USER_ID, SCRYPT_PASSWORD, signUpCode2, fullName);

        _sp.signIn(USER_ID.toString(), ByteString.copyFrom(SCRYPT_PASSWORD));

        // No exceptions should have been thrown
    }

    @Test(expected = ExAlreadyExist.class)
    public void shouldFailSignUpOfExistingUserWithNewValidCodeIncorrectPassword()
            throws Exception
    {
        // Invite a user twice
        String signUpCode1 = inviteUserToDefaultOrg(USER_ID);
        String signUpCode2 = inviteUserToDefaultOrg(USER_ID);

        // Sign up a new user using the first code
        _signupHelper.signUp(USER_ID, SCRYPT_PASSWORD, signUpCode1, fullName);

        // This second sign up uses an incorrect password
        byte [] invalidPassword = "INVALID_PASSWORD".getBytes();
        assertFalse(Arrays.equals(invalidPassword, SCRYPT_PASSWORD));
        _signupHelper.signUp(USER_ID, invalidPassword, signUpCode2, fullName);
    }

    /**
     * Invite the given userId by signing in as an ADMIN
     * (i.e. create a valid invitation code for the userId)
     * @return the signup code generated for userId
     */
    private String inviteUserToDefaultOrg(UserID userId)
            throws Exception
    {
        _sp.signIn(LocalSPServiceReactorCaller.ADMIN_ID.toString(),
                ByteString.copyFrom(LocalSPServiceReactorCaller.ADMIN_CRED));

        List<String> emails = Lists.newArrayList();
        emails.add(userId.toString());
        _sp.inviteUser(emails, true);

        // Verify an invitation email would have been sent: from the ADMIN_ID to userId.
        // Capture the code to return to the caller.
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(factEmail, atLeastOnce()).createUserInvitation(
                eq(LocalSPServiceReactorCaller.ADMIN_ID.toString()), eq(userId.toString()),
                anyString(), anyString(), anyString(), code.capture());

        assertEquals(InvitationCode.getType(code.getValue()), CodeType.TARGETED_SIGNUP);
        return code.getValue();
    }
}
