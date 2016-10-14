/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSP_InviteToOrganization extends AbstractSPTest
{
    static Properties defaultProps;

    @BeforeClass
    public static void setRestrictInvites()
    {
        Properties props = new Properties();
        props.put("signup_restriction", "USER_INVITED");
        defaultProps = props;
    }

    @Before
    public void setup()
    {
        session.setUser(USER_1);
        session.setBasicAuthDate(System.currentTimeMillis());
        // Reset the property so all tests can construct SP with the default properties.
        ConfigurationProperties.setProperties(defaultProps);
    }

    @Test
    public void shouldNotThrowIfInvitingSameUserTwice()
            throws Exception
    {
        String invitee = "cool@dude.com";
        service.inviteToOrganization(invitee);

        service.inviteToOrganization(invitee);
    }

    @Test
    public void shouldThrowIfInviteeIsMember()
            throws Exception
    {
        // move USER_2 to the same org as USER_1
        sqlTrans.begin();
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            service.inviteToOrganization(USER_2.id().getString());
            fail();
        } catch (ExAlreadyExist e) {}
    }

    @Captor ArgumentCaptor<String> signUpCodeCaptor;

    @Test
    public void shouldSendEmailWithSignUpCodeForNewNonAutoProvisionedUser()
            throws Exception
    {
        // By default all the users are non-auto provisioned

        User user = newUser();
        service.inviteToOrganization(user.id().getString());

        verify(factEmailer).createSignUpInvitationEmailer(eq(USER_1), eq(user),
                signUpCodeCaptor.capture());

        assertNotNull(signUpCodeCaptor.getValue());
    }

    @Test
    public void shouldSendEmailWithNoSignUpCodeForNewAutoProvisionedUser()
            throws Exception
    {
        User user = newUser();
        when(authenticator.isLocallyManaged(user.id())).thenReturn(false);
        service.inviteToOrganization(user.id().getString());

        verify(factEmailer).createSignUpInvitationEmailer(eq(USER_1), eq(user), eq((String)null));
    }

    @Test
    public void shouldThrowForNonAdminNewUserInvitationsIfInvitesAreRestricted()
            throws Exception
    {
        // For purpose of this test make USER 2 session user. This is because its not an admin user.
        session.setUser(USER_2);
        session.setBasicAuthDate(System.currentTimeMillis());

        Properties props = new Properties();
        props.put("signup_restriction", "ADMIN_INVITED");
        ConfigurationProperties.setProperties(props);
        rebuildSPService();
        String invitee = "cool@dude.com";
        try {
            service.inviteToOrganization(invitee);
            fail("Expected exception.");
        } catch (ExNoPerm e) {
            // pass
        }
    }
}
