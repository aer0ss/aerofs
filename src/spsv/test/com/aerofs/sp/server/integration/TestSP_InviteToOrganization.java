/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.lib.ex.ExNoStripeCustomerID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSP_InviteToOrganization extends AbstractSPTest
{
    @Before
    public void setup()
    {
        session.setUser(USER_1);
    }

    @Test
    public void shouldThrowIfInvitingSameUserTwice()
            throws Exception
    {
        String invitee = "cool@dude.com";
        service.inviteToOrganization(invitee);

        try {
            service.inviteToOrganization(invitee);
            fail();
        } catch (ExAlreadyInvited e) {}
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

    @Test
    public void shouldThrowIfNoStripeCustomrIDWhenExceedingFreePlan()
            throws Exception
    {
        inviteMaximumFreeUsers();

        try {
            service.inviteToOrganization("paid@invitee.com");
            fail();
        } catch (ExNoStripeCustomerID e) {}
    }

    @Test
    public void shouldNotThrowIfStripeCustomerIDIsPresentWhenExceedingFreePlan()
            throws Exception
    {
        inviteMaximumFreeUsers();

        service.setStripeCustomerID("123");

        service.inviteToOrganization("paid@invitee.com");
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
                eq((String) null), eq((Permissions) null), eq((String) null), signUpCodeCaptor.capture());

        assertNotNull(signUpCodeCaptor.getValue());
    }

    @Test
    public void shouldSendEmailWithNoSignUpCodeForNewAutoProvisionedUser()
            throws Exception
    {
        User user = newUser();
        when(authenticator.isLocallyManaged(user.id())).thenReturn(false);
        service.inviteToOrganization(user.id().getString());

        verify(factEmailer).createSignUpInvitationEmailer(eq(USER_1), eq(user),
                eq((String) null), eq((Permissions) null), eq((String) null), eq((String) null));
    }

    private void inviteMaximumFreeUsers()
            throws Exception
    {
        service.setMaxFreeMembers(3);
        // Current we allow at most three members for free
        service.inviteToOrganization("free@rider1.com");
        service.inviteToOrganization("free@rider2.com");
    }
}
