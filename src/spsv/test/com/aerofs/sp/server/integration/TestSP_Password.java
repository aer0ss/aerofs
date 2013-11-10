/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExCannotResetPassword;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.SecUtil;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.testlib.AbstractTest;
import com.google.protobuf.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSP_Password extends AbstractTest
{
    @Mock SPDatabase db;
    @Mock User.Factory factUser;
    @Mock PasswordResetEmailer passwordResetEmailer;
    @Mock Authenticator authenticator;
    @InjectMocks PasswordManagement _passwordManagement;

    @Mock User user;

    UserID userId = UserID.fromInternal("test@awesome.com");
    private Boolean _cachedPrivate;
    private Identity.Authenticator _cachedAuth;

    @Before
    public void setup()
        throws Exception
    {
        when(factUser.create(userId)).thenReturn(user);

        when(user.id()).thenReturn(userId);
        when(user.exists()).thenReturn(true);
        when(authenticator.isLocallyManaged(eq(userId))).thenReturn(true);

        mockSPDatabaseGetUserByPasswordResetTokenTestUser();

        _cachedPrivate = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT;
        _cachedAuth = Identity.AUTHENTICATOR;
    }

    // put back twiddled configuration values
    @After
    public void tearDown()
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = _cachedPrivate;
        Identity.AUTHENTICATOR = _cachedAuth;
    }

    private void mockNonexistingUser()
            throws Exception
    {
        when(user.exists()).thenReturn(false);
        doThrow(new ExNotFound()).when(user).throwIfNotFound();
    }

    private void mockSPDatabaseGetUserByPasswordResetTokenNoUser()
        throws Exception
    {
        when(db.resolvePasswordResetToken(anyString())).thenThrow(new ExNotFound());
    }

    private void mockSPDatabaseGetUserByPasswordResetTokenTestUser()
            throws Exception
    {
        when(db.resolvePasswordResetToken(anyString())).thenReturn(userId);
    }

    // Tests for sendPasswordResetEmail

    @Test
    public void shouldDoNothingIfEmailDoesNotExist()
        throws Exception
    {
        mockNonexistingUser();
        _passwordManagement.sendPasswordResetEmail(user);
        verify(user).exists();
    }
    @Test
    public void shouldCallDatabaseToAddPasswordResetToken()
        throws Exception
    {
        _passwordManagement.sendPasswordResetEmail(user);
        verify(db).insertPasswordResetToken(eq(user.id()), anyString());
    }

    @Test
    public void shouldSendPasswordResetEmail()
        throws Exception
    {
        _passwordManagement.sendPasswordResetEmail(user);
        verify(passwordResetEmailer).sendPasswordResetEmail(eq(user.id()),anyString());
    }

    @Test
    public void shouldSucceedResetEmailForInternals() throws Exception
    {
        Identity.AUTHENTICATOR = Identity.Authenticator.EXTERNAL_CREDENTIAL;
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        when(authenticator.isLocallyManaged(Matchers.any(UserID.class))).thenReturn(true);
        _passwordManagement.sendPasswordResetEmail(user);
    }

    @Test(expected=ExCannotResetPassword.class)
    public void shouldThrowExCannotResetPasswordForExternals() throws Exception
    {
        Identity.AUTHENTICATOR = Identity.Authenticator.EXTERNAL_CREDENTIAL;
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        when(authenticator.isLocallyManaged(Matchers.any(UserID.class))).thenReturn(false);
        _passwordManagement.sendPasswordResetEmail(user);
    }

    //  Tests for resetPassword

    @Test(expected=ExNotFound.class)
    public void shouldThrowExNotFoundIfPasswordResetTokenDoesNotExist()
            throws Exception
    {
        mockSPDatabaseGetUserByPasswordResetTokenNoUser();
        mockNonexistingUser();
        _passwordManagement.resetPassword("dummy token", ByteString.copyFrom(("dummy new " +
                "password").getBytes()));
    }

    @Test
    public void shouldCallDatabaseUpdateUserCredentials()
        throws Exception
    {
        _passwordManagement.resetPassword("dummy token", ByteString.copyFrom("test123".getBytes()));
        byte[] scrypted = SecUtil.scrypt("test123".toCharArray(), user.id());
        verify(db).updateUserCredentials(user.id(), SPParam.getShaedSP(scrypted));
    }

    @Test
    public void shouldCallDatabaseInvalidatePasswordResetToken()
        throws Exception
    {
        _passwordManagement.resetPassword("dummy token", ByteString.copyFrom("test123".getBytes()));
        verify(db).deletePasswordResetToken("dummy token");
    }

    // Tests for changePassword

    @Test
    public void shouldCheckUserExistence()
        throws Exception
    {
        _passwordManagement.changePassword(user.id(),
                ByteString.copyFrom("old password".getBytes()),
                ByteString.copyFrom("new password".getBytes())
        );
        verify(user).throwIfNotFound();
    }

    @Test
    public void shouldCallDatabaseTestAndCheckUserCredentials()
        throws Exception
    {
        _passwordManagement.changePassword(user.id(),
                ByteString.copyFrom("old password".getBytes()),
                ByteString.copyFrom("new password".getBytes())
        );
        verify(db).checkAndUpdateUserCredentials(user.id(), SPParam.getShaedSP("old password"
                .getBytes()),
                SPParam.getShaedSP("new password".getBytes()));
    }
}
