/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExPasswordExpired;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Properties;

import static org.mockito.Mockito.doReturn;

/**
 * Important: signIn() and signInUser() might expect scrypted password, or plaintext, depending on
 * the configured Authenticator instance.
 * <p/>
 * This is, to say the least, annoying.
 */
public class TestSP_SignInUser extends AbstractSPTest
{

    Calendar calendar = Calendar.getInstance();
    Timestamp currentTS = new Timestamp(calendar.getTime().getTime());
    long twoMonthsInMillis = 5259492000L;
    long spyPasswordCreatedTSInMillis = currentTS.getTime() - twoMonthsInMillis;


    @Test
    public void testSignInClearText() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void testSignInClearTextBadUserFails() throws Exception
    {
        User user = newUser();

        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void testSignInClearTextBadCredFails() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.credentialSignIn(user.id().getString(), ByteString.copyFrom("oh no".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void testSignInClearTextDoesNotNeedSCrypt() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.credentialSignIn(user.id().getString(),
                ByteString.copyFrom(LocalCredential.deriveKeyForUser(user.id(), CRED)));
    }

    // ---- ---- ---- ----
    // From here down, there are only tests for legacy paths
    // Abandon all hope ye who enter here
    // ---- ---- ---- ----

    @Test
    public void testLegacySignInUserRequiresScrypt() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signInUser(user.id().getString(),
                ByteString.copyFrom(LocalCredential.deriveKeyForUser(user.id(), CRED)));
    }

    @Test(expected = ExBadCredential.class)
    public void testLegacySignInUserFailsNonExistingUser()
            throws Exception
    {
        User user = newUser();

        service.signInUser(user.id().getString(),
                ByteString.copyFrom(LocalCredential.deriveKeyForUser(user.id(), CRED)));
    }

    @Test(expected = ExBadCredential.class)
    public void testLegacySignInUserFailsWithClearText() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signInUser(user.id().getString(), ByteString.copyFrom(CRED));
    }

    //TODO (SN) unit tests for password expiration
    @Test(expected = ExPasswordExpired.class)
    public void shouldFailSignInIfPasswordIsExpired() throws Exception
    {
        //TestSP_ExternalRestrictedSharing.java (use this as a reference for spoofing expiration period value
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        Properties props = new Properties();
        props.put("password.restriction.expiration_period_months", "1");
        ConfigurationProperties.setProperties(props);

        User userSpy = Mockito.spy(user);

        doReturn(userSpy).when(factUser).createFromExternalID(user.id().getString());
        doReturn(new Timestamp(spyPasswordCreatedTSInMillis)).when(userSpy).getPasswordCreatedTS();
        System.out.println("Date print:" + userSpy.getPasswordCreatedTS());
        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test
    public void shouldSucceedSignInIfPasswordIsNotExpired() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        Properties props = new Properties();
        props.put("password.restriction.expiration_period_months", "3");
        ConfigurationProperties.setProperties(props);

        User userSpy = Mockito.spy(user);

        doReturn(userSpy).when(factUser).createFromExternalID(user.id().getString());
        doReturn(new Timestamp(spyPasswordCreatedTSInMillis)).when(userSpy).getPasswordCreatedTS();
        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test
    public void shouldSucceedSignInIfPasswordExpiryIsNotSet() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        Properties props = new Properties();
        props.put("password.restriction.expiration_period_months", "");
        ConfigurationProperties.setProperties(props);

        User userSpy = Mockito.spy(user);

        doReturn(userSpy).when(factUser).createFromExternalID(user.id().getString());
        doReturn(new Timestamp(spyPasswordCreatedTSInMillis)).when(userSpy).getPasswordCreatedTS();
        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }


}
