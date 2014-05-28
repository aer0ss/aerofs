/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.SPService;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    public void shouldSupportInvitationOnlySignUp() throws Exception
    {
        Properties props = new Properties();
        props.put("open_signup", "false");
        ConfigurationProperties.setProperties(props);

        // reconstruct SP using the new shared folder rules
        service = new SPService(db,
                sqlTrans,
                jedisTrans,
                sessionUser,
                passwordManagement,
                certificateAuthenticator,
                remoteAddress,
                factUser,
                factOrg,
                factOrgInvite,
                factDevice,
                certdb,
                esdb,
                factSharedFolder,
                factEmailer,
                _deviceRegistrationEmailer,
                requestToSignUpEmailer,
                commandQueue,
                analytics,
                identitySessionManager,
                authenticator,
                sharingRules,
                sharedFolderNotificationEmailer,
                asyncEmailSender);
        wireSPService();

        try {
            // It should fail because AbstractSPTest already created a bunch of users
            service.requestToSignUp("user@gmail.com");
            fail();
        } catch (ExNoPerm e) {}

        // Reset the property so subsequent tests can construct SP with the default properties.
        ConfigurationProperties.setProperties(new Properties());
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
