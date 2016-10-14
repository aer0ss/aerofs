/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.UserID;
import com.aerofs.sp.server.lib.user.User;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TestSP_RequestToSignUp extends AbstractSPTest
{

    static Properties defaultProps;

    @BeforeClass
    public static void setOpenSignup()
    {
        Properties props = new Properties();
        props.put("signup_restriction", "UNRESTRICTED");
        ConfigurationProperties.setProperties(props);
        defaultProps = props;
    }

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

    @Test
    public void shouldSupportInvitationOnlySignUp() throws Exception
    {
        Properties props = new Properties();
        props.put("signup_restriction", "USER_INVITED");
        ConfigurationProperties.setProperties(props);
        rebuildSPService();

        try {
            // It should fail because AbstractSPTest already created a bunch of users
            service.requestToSignUp("user@gmail.com");
            fail();
        } catch (ExNoPerm e) {}

        // Reset the property so subsequent tests can construct SP with the default properties.
        ConfigurationProperties.setProperties(defaultProps);
    }

    @Test
    public void shouldRejectAutoProsivionedUsers() throws Exception
    {
        when(authenticator.isLocallyManaged(any(UserID.class))).thenReturn(false);

        try {
            // It should fail because AbstractSPTest already created a bunch of users
            service.requestToSignUp("user@gmail.com");
            fail();
        } catch (ExNoPerm e) {}
    }
}
