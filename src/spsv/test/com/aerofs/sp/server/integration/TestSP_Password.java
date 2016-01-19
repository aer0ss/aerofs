/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExCannotResetPassword;
import com.aerofs.base.ex.ExCannotReuseExistingPassword;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.util.Properties;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


public class TestSP_Password extends AbstractTest
{
    @Mock SPDatabase db;
    @Mock User.Factory factUser;
    @Mock PasswordResetEmailer passwordResetEmailer;
    @Mock Authenticator authenticator;
    @InjectMocks PasswordManagement _passwordManagement;

    @Mock User user;

    UserID userId = UserID.fromInternal("test@awesome.com");
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

        _cachedAuth = Identity.AUTHENTICATOR;
    }

    // put back twiddled configuration values
    @After
    public void tearDown()
    {
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
        when(authenticator.isLocallyManaged(Matchers.any(UserID.class))).thenReturn(true);
        _passwordManagement.sendPasswordResetEmail(user);
    }

    @Test
    public void shouldSendEmailToExternallyManagedAccount() throws Exception
    {
        Identity.AUTHENTICATOR = Identity.Authenticator.EXTERNAL_CREDENTIAL;
        when(authenticator.isLocallyManaged(Matchers.any(UserID.class))).thenReturn(false);

            _passwordManagement.sendPasswordResetEmail(user);
            verify(passwordResetEmailer).sendPasswordResetEmailToExternallyManagedAccount(eq(user.id()));
        }

    //  Tests for resetPassword

    @Test(expected=ExNotFound.class)
    public void shouldThrowExNotFoundIfPasswordResetTokenDoesNotExist()
            throws Exception
    {
        mockSPDatabaseGetUserByPasswordResetTokenNoUser();
        mockNonexistingUser();
        _passwordManagement.resetPassword("dummy token", ("dummy new password").getBytes());
    }

    @Test
    public void shouldCallDatabaseUpdateUserCredentials()
        throws Exception
    {
        _passwordManagement.resetPassword("dummy token", "test123".getBytes());
        byte[] scrypted = LocalCredential.deriveKeyForUser(user.id(), "test123".getBytes());
        verify(db).updateUserCredentials(user.id(), SPParam.getShaedSP(scrypted));
    }

    @Test
    public void shouldCallDatabaseInvalidatePasswordResetToken()
        throws Exception
    {
        _passwordManagement.resetPassword("dummy token", "test123".getBytes());
        verify(db).deletePasswordResetToken("dummy token");
    }

    // Tests for changePassword

    @Test(expected = ExCannotResetPassword.class)
    public void shouldNotChangePasswordForExternalCred()
            throws Exception
    {
        when(authenticator.isLocallyManaged(eq(userId))).thenReturn(false);

        _passwordManagement.replacePassword(userId,
                "old password".getBytes(),
                "new password".getBytes());
    }

    @Test
    public void shouldCheckUserExistence()
        throws Exception
    {
        _passwordManagement.replacePassword(user.id(),
                "old password".getBytes(),
                "new password".getBytes());
        verify(user).throwIfNotFound();
    }

    @Test
    public void shouldCallDatabaseTestAndCheckUserCredentials()
        throws Exception
    {
        _passwordManagement.replacePassword(user.id(),
                "old password".getBytes(),
                "new password".getBytes());
        verify(db).checkAndUpdateUserCredentials(user.id(), SPParam.getShaedSP("old password"
                        .getBytes()),
                SPParam.getShaedSP("new password".getBytes()));
    }

    // Tests for revokePassword
    @Test
    public void shouldRevoke() throws Exception
    {
        _passwordManagement.revokePassword(user.id());
        verify(db).updateUserCredentials(user.id(), new byte[0]);
        verify(db).insertPasswordResetToken(eq(user.id()), anyString());
        verify(passwordResetEmailer).sendPasswordRevokeNotification(eq(user.id()), anyString());
    }

    @Test(expected = ExNotFound.class)
    public void shouldFailNonExistentUser() throws Exception
    {
        mockNonexistingUser();
        _passwordManagement.revokePassword(user.id());
    }

    @Test(expected = ExCannotResetPassword.class)
    public void shouldFailToRevokeExternalCred() throws Exception
    {
        when(authenticator.isLocallyManaged(eq(user.id()))).thenReturn(false);
        _passwordManagement.revokePassword(user.id());
    }

    // Test for setPassword
    @Test
    public void shouldSetPassword() throws Exception
    {
        _passwordManagement.setPassword(user.id(), "test".getBytes());
        byte[] scrypted = LocalCredential.deriveKeyForUser(user.id(), "test".getBytes());
        verify(db).updateUserCredentials(user.id(), SPParam.getShaedSP(scrypted));
        verify(passwordResetEmailer).sendPasswordChangeNotification(eq(user.id()));
    }

    @Test(expected = ExNotFound.class)
    public void shouldFailSetForNonExistentUser() throws Exception
    {
        mockNonexistingUser();
        _passwordManagement.setPassword(user.id(), "test".getBytes());
    }

    @Test(expected = ExCannotResetPassword.class)
    public void shouldFailToSetForExternalCred() throws Exception
    {
        when(authenticator.isLocallyManaged(eq(user.id()))).thenReturn(false);
        _passwordManagement.setPassword(user.id(), "test".getBytes());
    }

    @Test(expected = ExCannotReuseExistingPassword.class)
    public void shouldFailToResetPasswordIfNewPasswordIsTheSameAsCurrentOne() throws Exception
    {
        Properties props = new Properties();
        props.put("password.restriction.expiration_period_months", "3");
        ConfigurationProperties.setProperties(props);

        byte[] currentPassword = "test".getBytes();
        byte[] credential = LocalCredential.hashScrypted(
                LocalCredential.deriveKeyForUser(user.id(), currentPassword));

        when(user.getShaedSP(eq(user.id()))).thenReturn(credential);
        _passwordManagement.setPassword(user.id(), currentPassword);

        _passwordManagement.resetPassword(anyString(), currentPassword);
    }

    @Test
    public void shouldSucceedToResetPasswordIfNewPasswordIsDifferentFromCurrentOne() throws Exception
    {
        Properties props = new Properties();
        props.put("password.restriction.expiration_period_months", "3");
        ConfigurationProperties.setProperties(props);

        byte[] currentPassword = "test".getBytes();
        byte[] newPassword = "newtest".getBytes();
        byte[] credential = LocalCredential.hashScrypted(
                LocalCredential.deriveKeyForUser(user.id(), currentPassword));

        when(user.getShaedSP(eq(user.id()))).thenReturn(credential);
        _passwordManagement.setPassword(user.id(), currentPassword);
        _passwordManagement.resetPassword(anyString(), newPassword);
    }

    @Test
    public void shouldSucceedToResetPasswordIfPasswordExpiryNotSet() throws Exception {
        Properties props = new Properties();
        props.put("password.restriction.expiration_period_months", "");
        ConfigurationProperties.setProperties(props);

        byte[] currentPassword = "test".getBytes();
        byte[] credential = LocalCredential.hashScrypted(
                LocalCredential.deriveKeyForUser(user.id(), currentPassword));

        when(user.getShaedSP(eq(user.id()))).thenReturn(credential);
        _passwordManagement.setPassword(user.id(), currentPassword);
        _passwordManagement.resetPassword(anyString(), currentPassword);
    }
}
