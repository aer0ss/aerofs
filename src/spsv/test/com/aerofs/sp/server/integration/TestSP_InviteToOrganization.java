/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.lib.ex.ExNoStripeCustomerID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

public class TestSP_InviteToOrganization extends AbstractSPTest
{
    @Before
    public void setup()
    {
        sessionUser.set(USER_1);
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
        inviteMaximalFreeUsers();

        try {
            service.inviteToOrganization("paid@invitee.com");
            fail();
        } catch (ExNoStripeCustomerID e) {}
    }

    @Test
    public void shouldNotThrowIfStripeCustomerIDIsPresentWhenExceedingFreePlan()
            throws Exception
    {
        inviteMaximalFreeUsers();

        service.setStripeCustomerID("123");

        service.inviteToOrganization("paid@invitee.com");
    }

    private void inviteMaximalFreeUsers()
            throws Exception
    {
        // Current we allow at most three members for free
        service.inviteToOrganization("free@rider1.com");
        service.inviteToOrganization("free@rider2.com");
    }
}
