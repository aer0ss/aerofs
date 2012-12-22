/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestSP_OrganizationMovement extends AbstractSPTest
{
    private static final String _organizationName = "Some Awesome Organization";

    @Test
    public void shouldMoveUserToNewOrganizationViaEmail()
            throws Exception
    {
        setSessionUser(USER_1);

        // Create a new organization (name doesn't matter). The session user will now be an admin
        // of this new organization.
        service.addOrganization(_organizationName);
        service.inviteToOrganization(USER_2.toString());

        // Switch to the invited user.
        setSessionUser(USER_2);
        GetOrganizationInvitationsReply pending = service.getOrganizationInvitations().get();

        assertEquals(1, pending.getOrganizationInvitationsList().size());

        // Accept the invite.
        service.acceptOrganizationInvitation(
                pending.getOrganizationInvitationsList().get(0).getOrganizationId());

        pending = service.getOrganizationInvitations().get();

        // Verify user 2 is indeed in the new organization.
        assertEquals(0, pending.getOrganizationInvitationsList().size());
        assertEquals(
                service.getOrgPreferences().get().getOrganizationName(),
                _organizationName);
    }
}
