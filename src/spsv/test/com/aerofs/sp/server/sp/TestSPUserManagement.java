/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.sp.user.AuthorizationLevel;
import com.aerofs.sp.server.sp.user.User;
import com.aerofs.sp.server.sp.user.UserManagement;
import com.aerofs.testlib.AbstractTest;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import javax.annotation.Nullable;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


public class TestSPUserManagement extends AbstractTest
{
    @Mock SPDatabase db;
    @Mock PasswordResetEmailer passwordResetEmailer;
    @InjectMocks UserManagement userManagement;

    User testUser;

    @Before
    public void setup()
        throws Exception
    {
        testUser = new User("test@awesome.com","","","".getBytes(),true,true,"",
                AuthorizationLevel.USER);

        setupMockSPDatabaseGetUserTest();
        setupMockSPDatabaseGetUserByPasswordResetTokenTestUser();
    }

    private void setupMockSPDatabaseGetUser(@Nullable User user)
            throws Exception
    {
        when(db.getUser(any(String.class))).thenReturn(user);
    }
    private void setupMockSPDatabaseGetUserTest()
            throws Exception
    {

        setupMockSPDatabaseGetUser(testUser);
    }

    private void setupMockSPDatabaseGetUserNull()
            throws Exception
    {
         setupMockSPDatabaseGetUser(null);
    }

    private void setupMockSPDatabaseGetUserByPasswordResetToken(@Nullable String userID)
        throws Exception
    {
         when(db.resolvePasswordResetToken(anyString())).thenReturn(userID);
    }

    private void setupMockSPDatabaseGetUserByPasswordResetTokenNull()
        throws Exception
    {
        setupMockSPDatabaseGetUserByPasswordResetToken(null);
    }

    private void setupMockSPDatabaseGetUserByPasswordResetTokenTestUser()
            throws Exception
    {
        setupMockSPDatabaseGetUserByPasswordResetToken(testUser._id);
    }

    // Tests for sendPasswordResetEmail

    @Test
    public void shouldDoNothingIfEmailDoesNotExist()
        throws Exception
    {
        setupMockSPDatabaseGetUserNull();
        userManagement.sendPasswordResetEmail(testUser._id);
        verify(db).getUser(anyString());
        verifyNoMoreInteractions(db);
    }
    @Test
    public void shouldCallDatabaseToAddPasswordResetToken()
        throws Exception
    {
        userManagement.sendPasswordResetEmail(testUser._id);
        verify(db).addPasswordResetToken(eq(testUser._id), anyString());
    }

    @Test
    public void shouldSendPasswordResetEmail()
        throws Exception
    {
        userManagement.sendPasswordResetEmail(testUser._id);
        verify(passwordResetEmailer).sendPasswordResetEmail(eq(testUser._id),anyString());
    }

    //  Tests for resetPassword

    @Test(expected=ExNotFound.class)
    public void shouldThrowExNotFoundIfPasswordResetTokenDoesNotExist()
            throws Exception
    {
        setupMockSPDatabaseGetUserByPasswordResetTokenNull();
        setupMockSPDatabaseGetUserNull();
        userManagement.resetPassword("dummy token", ByteString.copyFrom(("dummy new " +
                "password").getBytes()));
    }

    @Test
    public void shouldCallDatabaseUpdateUserCredentials()
        throws Exception
    {
        userManagement.resetPassword("dummy token", ByteString.copyFrom("test123".getBytes()));
        verify(db).updateUserCredentials(testUser._id, SPParam.getShaedSP("test123".getBytes()));
    }

    @Test
    public void shouldCallDatabaseInvalidatePasswordResetToken()
        throws Exception
    {
        userManagement.resetPassword("dummy token", ByteString.copyFrom("test123".getBytes()));
        verify(db).deletePasswordResetToken("dummy token");
    }

    // Tests for changePassword

    @Test
    public void shouldCallDatabaseGetUserWithSessionUser()
        throws Exception
    {

        userManagement.changePassword(
                testUser._id,
                ByteString.copyFrom("old password".getBytes()),
                ByteString.copyFrom("new password".getBytes())
        );
        verify(db).getUser(testUser._id);
    }

    @Test
    public void shouldCallDatabaseTestAndCheckUserCredentials()
        throws Exception
    {
        userManagement.changePassword(
                testUser._id,
                ByteString.copyFrom("old password".getBytes()),
                ByteString.copyFrom("new password".getBytes())
        );
        verify(db).checkAndUpdateUserCredentials(testUser._id, SPParam.getShaedSP("old password"
                .getBytes()),
                SPParam.getShaedSP("new password".getBytes()));

    }
}
